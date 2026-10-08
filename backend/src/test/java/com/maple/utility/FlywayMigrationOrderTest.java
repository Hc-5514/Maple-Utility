package com.maple.utility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import java.util.stream.Stream;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "FLYWAY_TEST_URL", matches = ".+")
class FlywayMigrationOrderTest {

	private static final Path MIGRATION_DIR = Path.of("src/main/resources/db/migration").toAbsolutePath();

	@Test
	void laterV9RequiresOutOfOrderOnDatabaseAlreadyAtV12() throws Exception {
		String schema = schemaName();
		Path initialMigrations = Files.createTempDirectory("flyway-without-v9-");
		try {
			try (Stream<Path> files = Files.list(MIGRATION_DIR)) {
				for (Path file : files.filter(path -> !path.getFileName().toString().startsWith("V9__")).toList()) {
					Files.copy(file, initialMigrations.resolve(file.getFileName()));
				}
			}

			flyway(schema, initialMigrations, false).migrate();
			assertThat(latestVersion(schema)).isEqualTo("12");
			assertThatThrownBy(() -> flyway(schema, MIGRATION_DIR, false).migrate())
					.hasMessageContaining("migration not applied to database: 9");

			flyway(schema, MIGRATION_DIR, true).migrate();
			assertThat(migrationSuccess(schema, "9")).isTrue();
			assertThat(bossSortOrder(schema, "스우", "HARD")).isEqualTo(170);
			flyway(schema, MIGRATION_DIR, false).validate();
		} finally {
			try (Stream<Path> files = Files.list(initialMigrations)) {
				for (Path file : files.toList()) {
					Files.delete(file);
				}
			}
			Files.delete(initialMigrations);
		}
	}

	@Test
	void allMigrationsApplyToFreshDatabaseWithoutOutOfOrder() throws Exception {
		String schema = schemaName();
		flyway(schema, MIGRATION_DIR, false).migrate();

		assertThat(migrationSuccess(schema, "9")).isTrue();
		assertThat(latestVersion(schema)).isEqualTo("12");
		assertThat(bossSortOrder(schema, "스우", "HARD")).isEqualTo(170);
	}

	private Flyway flyway(String schema, Path location, boolean outOfOrder) {
		return Flyway.configure()
				.dataSource(System.getenv("FLYWAY_TEST_URL"), System.getenv("FLYWAY_TEST_USER"), System.getenv("FLYWAY_TEST_PASSWORD"))
				.defaultSchema(schema)
				.schemas(schema)
				.locations("filesystem:" + location)
				.outOfOrder(outOfOrder)
				.load();
	}

	private String schemaName() {
		return "flyway_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
	}

	private String latestVersion(String schema) throws Exception {
		try (Connection connection = connection();
				Statement statement = connection.createStatement();
				ResultSet result = statement.executeQuery(
						"SELECT version FROM " + schema + ".flyway_schema_history ORDER BY installed_rank DESC LIMIT 1")) {
			assertThat(result.next()).isTrue();
			return result.getString(1);
		}
	}

	private boolean migrationSuccess(String schema, String version) throws Exception {
		try (Connection connection = connection();
				Statement statement = connection.createStatement();
				ResultSet result = statement.executeQuery(
						"SELECT success FROM " + schema + ".flyway_schema_history WHERE version = '" + version + "'")) {
			assertThat(result.next()).isTrue();
			return result.getBoolean(1);
		}
	}

	private int bossSortOrder(String schema, String bossName, String difficulty) throws Exception {
		try (Connection connection = connection();
				Statement statement = connection.createStatement();
				ResultSet result = statement.executeQuery("SELECT sort_order FROM " + schema
						+ ".boss_master WHERE boss_name = '" + bossName + "' AND difficulty = '" + difficulty + "'")) {
			assertThat(result.next()).isTrue();
			return result.getInt(1);
		}
	}

	private Connection connection() throws Exception {
		return DriverManager.getConnection(System.getenv("FLYWAY_TEST_URL"), System.getenv("FLYWAY_TEST_USER"),
				System.getenv("FLYWAY_TEST_PASSWORD"));
	}
}
