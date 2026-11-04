package com.maple.utility.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import com.maple.utility.dto.response.SyncJobResponse;

@EnabledIfEnvironmentVariable(named = "FLYWAY_TEST_URL", matches = ".+")
class SyncJobRepositoryTest {

	private static final LocalDateTime FINISHED_AT = LocalDateTime.of(2026, 11, 5, 12, 30);
	private static Database database;
	private SyncJobRepository jobs;
	private Long userId;

	@BeforeAll
	static void migrateFreshSchema() throws SQLException {
		database = new Database(null);
	}

	@AfterAll
	static void dropSchema() throws SQLException {
		if (database != null) database.close();
	}

	@BeforeEach
	void createUserAndRepository() {
		jobs = new SyncJobRepository(database.jdbc);
		userId = database.createUser();
	}

	@Test
	void mixedResultsPersistAsCompletedWithDistinctFailureCountInResponse() throws Exception {
		Long id = jobs.start(userId, "SCHEDULER").job().id();
		jobs.setTotal(id, 3);
		jobs.advance(id, false);
		jobs.advanceFailed(id);
		jobs.advance(id, true);

		jobs.complete(id, FINISHED_AT);

		SyncJobResponse result = jobs.get(userId, id).orElseThrow();
		assertThat(result.status()).isEqualTo("COMPLETED");
		assertThat(result.totalCount()).isEqualTo(3);
		assertThat(result.completedCount()).isEqualTo(3);
		assertThat(result.skippedCount()).isEqualTo(1);
		assertThat(result.failedCount()).isEqualTo(1);
		assertThat(result.errorMessage()).isEqualTo("SCHEDULER_PARTIAL_FAILURE");
		assertThat(result.completedAt()).isEqualTo(FINISHED_AT);
		var json = new ObjectMapper().registerModule(new JavaTimeModule()).valueToTree(result);
		assertThat(json.path("failedCount").asInt()).isEqualTo(1);
		assertThat(json.path("completedCount").asInt()).isEqualTo(3);
	}

	@Test
	void onlyNetworkFailuresFinishAsFailedWithoutLeavingAnActiveJob() {
		Long id = jobs.start(userId, "SCHEDULER").job().id();
		jobs.setTotal(id, 2);
		jobs.advanceFailed(id);
		jobs.advanceFailed(id);

		jobs.complete(id, FINISHED_AT);

		SyncJobResponse result = jobs.get(userId, id).orElseThrow();
		assertThat(result.status()).isEqualTo("FAILED");
		assertThat(result.completedCount()).isEqualTo(2);
		assertThat(result.failedCount()).isEqualTo(2);
		assertThat(result.skippedCount()).isZero();
		assertThat(result.errorMessage()).isEqualTo("NEXON_NETWORK_ERROR");
		assertThat(result.completedAt()).isEqualTo(FINISHED_AT);
		assertThat(jobs.active(userId, "SCHEDULER")).isEmpty();
		assertThat(jobs.start(userId, "SCHEDULER").created()).isTrue();
	}

	@Test
	void skippedCharactersDoNotTurnOtherwiseUnsuccessfulJobIntoPartialSuccess() {
		Long id = jobs.start(userId, "SCHEDULER").job().id();
		jobs.setTotal(id, 2);
		jobs.advance(id, true);
		jobs.advanceFailed(id);

		jobs.complete(id, FINISHED_AT);

		SyncJobResponse result = jobs.get(userId, id).orElseThrow();
		assertThat(result.status()).isEqualTo("FAILED");
		assertThat(result.skippedCount()).isEqualTo(1);
		assertThat(result.failedCount()).isEqualTo(1);
		assertThat(result.errorMessage()).isEqualTo("NEXON_NETWORK_ERROR");
	}

	@Test
	void emptyJobStillCompletesWithoutFailure() {
		Long id = jobs.start(userId, "SCHEDULER").job().id();

		jobs.complete(id, FINISHED_AT);

		SyncJobResponse result = jobs.get(userId, id).orElseThrow();
		assertThat(result.status()).isEqualTo("COMPLETED");
		assertThat(result.completedCount()).isZero();
		assertThat(result.failedCount()).isZero();
		assertThat(result.errorMessage()).isNull();
	}

	@Test
	void onlySkippedCharactersStillCompleteWithoutFailure() {
		Long id = jobs.start(userId, "SCHEDULER").job().id();
		jobs.setTotal(id, 1);
		jobs.advance(id, true);

		jobs.complete(id, FINISHED_AT);

		SyncJobResponse result = jobs.get(userId, id).orElseThrow();
		assertThat(result.status()).isEqualTo("COMPLETED");
		assertThat(result.completedCount()).isEqualTo(1);
		assertThat(result.skippedCount()).isEqualTo(1);
		assertThat(result.failedCount()).isZero();
		assertThat(result.errorMessage()).isNull();
	}

	@Test
	void terminalJobRejectsLateFailureProgress() {
		Long id = jobs.start(userId, "SCHEDULER").job().id();
		jobs.setTotal(id, 1);
		jobs.advance(id, false);
		jobs.complete(id, FINISHED_AT);
		SyncJobResponse beforeLateProgress = jobs.get(userId, id).orElseThrow();

		jobs.advanceFailed(id);

		assertThat(jobs.get(userId, id)).contains(beforeLateProgress);
	}

	@ParameterizedTest
	@CsvSource({"-1, 0, 0", "1, 0, 0", "1, 1, 1"})
	void databaseRejectsFailureCountsOutsideProcessedProgress(int failed, int skipped, int completed) {
		Long id = jobs.start(userId, "SCHEDULER").job().id();

		assertThatThrownBy(() -> database.jdbc.update(
				"UPDATE sync_jobs SET failed_count = ?, skipped_count = ?, completed_count = ? WHERE id = ?",
				failed, skipped, completed, id)).isInstanceOf(DataIntegrityViolationException.class);

		assertThat(jobs.get(userId, id).orElseThrow().failedCount()).isZero();
	}

	@Test
	void databaseRejectsNullFailureCount() {
		Long id = jobs.start(userId, "SCHEDULER").job().id();

		assertThatThrownBy(() -> database.jdbc.update("UPDATE sync_jobs SET failed_count = NULL WHERE id = ?", id))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	static final class Database implements AutoCloseable {
		private static final String URL = System.getenv("FLYWAY_TEST_URL");
		private static final String USER = System.getenv("FLYWAY_TEST_USER");
		private static final String PASSWORD = System.getenv("FLYWAY_TEST_PASSWORD");
		private final String schema = "sync_jobs_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		private final SingleConnectionDataSource dataSource;
		final JdbcTemplate jdbc;

		Database(String target) throws SQLException {
			migrate(target);
			Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
			connection.setSchema(schema);
			dataSource = new SingleConnectionDataSource(connection, true);
			jdbc = new JdbcTemplate(dataSource);
		}

		Long createUser() {
			return jdbc.queryForObject("INSERT INTO users (oauth_provider, oauth_id) VALUES ('KAKAO', ?) RETURNING id",
					Long.class, UUID.randomUUID().toString());
		}

		void migrate(String target) {
			var configuration = Flyway.configure()
					.dataSource(URL, USER, PASSWORD)
					.defaultSchema(schema)
					.schemas(schema)
					.locations("filesystem:" + Path.of("src/main/resources/db/migration").toAbsolutePath());
			if (target != null) configuration.target(MigrationVersion.fromVersion(target));
			configuration.load().migrate();
		}

		@Override
		public void close() throws SQLException {
			dataSource.destroy();
			try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
					var statement = connection.createStatement()) {
				statement.execute("DROP SCHEMA " + schema + " CASCADE");
			}
		}
	}
}
