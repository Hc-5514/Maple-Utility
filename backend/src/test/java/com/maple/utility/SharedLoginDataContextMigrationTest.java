package com.maple.utility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "FLYWAY_TEST_URL", matches = ".+")
class SharedLoginDataContextMigrationTest {

	private final String schema = "task258_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

	@Test
	void upgradePreservesLegacyKeysAndDataOwnersWithoutInferringAccounts() throws Exception {
		flyway("15").migrate();
		try (Connection connection = connection(); Statement statement = connection.createStatement()) {
			statement.execute("""
					INSERT INTO users (id, oauth_provider, oauth_id, created_at, updated_at) VALUES
					(101, 'KAKAO', 'social-101', '2026-01-01 09:00:00', '2026-02-01 10:00:00'),
					(102, 'NEXON_APIKEY', 'direct-102', '2026-01-02 09:00:00', '2026-02-02 10:00:00'),
					(103, 'KAKAO', 'no-key-103', '2026-01-03 09:00:00', '2026-02-03 10:00:00')
					""");
			statement.execute("""
					INSERT INTO user_api_keys (id, user_id, encrypted_key, key_status) VALUES
					(201, 101, 'legacy-ciphertext-social', 'ACTIVE'),
					(202, 102, 'legacy-ciphertext-direct', 'INVALID')
					""");
			statement.execute("""
					INSERT INTO characters (id, user_id, ocid, character_name) VALUES
					(301, 101, 'same-ocid', '사회 계정'), (302, 102, 'same-ocid', '직접 계정')
					""");
		}

		flyway(null).migrate();
		flyway(null).validate();
		assertThat(flyway(null).migrate().migrationsExecuted).isZero();
		try (Connection connection = connection(); Statement statement = connection.createStatement()) {
			assertThat(number(statement, "SELECT count(*) FROM data_contexts")).isEqualTo(3);
			assertThat(number(statement, "SELECT count(*) FROM user_data_contexts")).isEqualTo(3);
			assertThat(number(statement, "SELECT count(*) FROM data_contexts WHERE kakao_user_id = owner_user_id")).isEqualTo(2);
			assertThat(number(statement, "SELECT count(*) FROM data_contexts WHERE owner_user_id = 102 AND kakao_user_id IS NULL")).isEqualTo(1);
			assertThat(number(statement, "SELECT count(*) FROM user_api_keys k JOIN data_contexts c ON c.id = k.context_id WHERE k.user_id = c.owner_user_id AND k.key_fingerprint IS NULL")).isEqualTo(2);
			assertThat(number(statement, "SELECT count(*) FROM user_api_keys WHERE id = 201 AND encrypted_key = 'legacy-ciphertext-social' AND key_status = 'ACTIVE'")).isEqualTo(1);
			assertThat(number(statement, "SELECT count(*) FROM user_api_keys WHERE id = 202 AND encrypted_key = 'legacy-ciphertext-direct' AND key_status = 'INVALID'")).isEqualTo(1);
			assertThat(number(statement, "SELECT count(*) FROM characters WHERE (id = 301 AND user_id = 101) OR (id = 302 AND user_id = 102)")).isEqualTo(2);
			assertThat(number(statement, "SELECT count(*) FROM data_context_accounts")).isZero();
			assertThat(number(statement, "SELECT count(*) FROM data_merge_provenance")).isZero();
			assertThat(number(statement, "SELECT count(*) FROM data_contexts WHERE owner_user_id = 101 AND created_at = TIMESTAMPTZ '2026-01-01 09:00:00+09' AND updated_at = TIMESTAMPTZ '2026-02-01 10:00:00+09'")).isEqualTo(1);
		}
	}

	@Test
	void freshSchemaSupportsMultipleKeysAndLinksWhileProtectingUniqueIdentity() throws Exception {
		flyway(null).migrate();
		try (Connection connection = connection(); Statement statement = connection.createStatement()) {
			statement.execute("INSERT INTO users (id, oauth_provider, oauth_id) VALUES (101, 'KAKAO', 'social'), (102, 'NEXON_APIKEY', 'direct')");
			long firstContext = number(statement, "SELECT id FROM data_contexts WHERE owner_user_id = 101");
			long secondContext = number(statement, "SELECT id FROM data_contexts WHERE owner_user_id = 102");
			statement.execute("UPDATE data_contexts SET kakao_user_id = 101 WHERE id = " + secondContext);
			statement.execute("INSERT INTO user_data_contexts (user_id, context_id) VALUES (101, " + secondContext + "), (102, " + firstContext + ")");
			statement.execute("INSERT INTO user_api_keys (user_id, context_id, encrypted_key, key_fingerprint) VALUES (101, " + firstContext + ", 'encrypted-a', repeat('a',64)), (101, " + firstContext + ", 'encrypted-b', repeat('b',64)), (101, " + secondContext + ", 'legacy-a', NULL), (101, " + secondContext + ", 'legacy-b', NULL)");
			assertThat(number(statement, "SELECT count(*) FROM user_api_keys WHERE user_id = 101")).isEqualTo(4);
			assertThatThrownBy(() -> statement.execute("INSERT INTO user_api_keys (user_id, context_id, encrypted_key, key_fingerprint) VALUES (102, " + secondContext + ", 'duplicate', repeat('a',64))")).isInstanceOf(SQLException.class).hasMessageContaining("uk_user_api_keys_key_fingerprint");
			statement.execute("INSERT INTO data_context_accounts (account_id, context_id) VALUES ('verified-account', " + firstContext + ")");
			assertThatThrownBy(() -> statement.execute("INSERT INTO data_context_accounts (account_id, context_id) VALUES ('verified-account', " + secondContext + ")")).isInstanceOf(SQLException.class);
			assertThatThrownBy(() -> statement.execute("INSERT INTO user_data_contexts (user_id, context_id) VALUES (101, " + firstContext + ")")).isInstanceOf(SQLException.class);
			assertThatThrownBy(() -> statement.execute("UPDATE data_contexts SET canonical_context_id = id WHERE id = " + firstContext)).isInstanceOf(SQLException.class);
			statement.execute("UPDATE data_contexts SET canonical_context_id = " + firstContext + " WHERE id = " + secondContext);
			assertThat(number(statement, "SELECT owner_user_id FROM data_contexts WHERE id = (SELECT canonical_context_id FROM data_contexts WHERE id = " + secondContext + ")")).isEqualTo(101);
			statement.execute("INSERT INTO data_merge_provenance (table_name, record_id, source_user_id) VALUES ('characters', 301, 102), ('hunting_records', 301, 102)");
			assertThat(number(statement, "SELECT count(*) FROM data_merge_provenance WHERE record_id = 301")).isEqualTo(2);
			assertThatThrownBy(() -> statement.execute("DELETE FROM data_contexts WHERE id = " + firstContext)).isInstanceOf(SQLException.class);
			assertThatThrownBy(() -> statement.execute("DELETE FROM users WHERE id = 102")).isInstanceOf(SQLException.class);
		}
	}

	@Test
	void newTablesHaveTimestampDefaultsAndUpdateTriggers() throws Exception {
		flyway(null).migrate();
		try (Connection connection = connection(); Statement statement = connection.createStatement()) {
			statement.execute("INSERT INTO users (id, oauth_provider, oauth_id) VALUES (101, 'KAKAO', 'social')");
			statement.execute("INSERT INTO data_context_accounts (account_id, context_id) SELECT 'account', id FROM data_contexts WHERE owner_user_id = 101");
			statement.execute("INSERT INTO data_merge_provenance (table_name, record_id, source_user_id) VALUES ('characters', 301, 101)");
			for (String table : new String[] {"data_contexts", "data_context_accounts", "user_data_contexts", "data_merge_provenance"}) {
				assertThat(number(statement, "SELECT count(*) FROM " + table + " WHERE created_at IS NOT NULL AND updated_at IS NOT NULL")).isEqualTo(1);
				statement.execute("UPDATE " + table + " SET updated_at = TIMESTAMPTZ '2000-01-01 00:00:00+09'");
				assertThat(number(statement, "SELECT count(*) FROM " + table + " WHERE updated_at > TIMESTAMPTZ '2000-01-02 00:00:00+09'")).isEqualTo(1);
			}
		}
	}

	@Test
	void legacyApplicationCanInsertUsersAndKeysWithoutContextFieldsAfterMigration() throws Exception {
		flyway(null).migrate();
		try (Connection connection = connection(); Statement statement = connection.createStatement()) {
			statement.execute("""
					INSERT INTO users (id, oauth_provider, oauth_id, created_at, updated_at) VALUES
					(101, 'KAKAO', 'new-social', '2026-01-01 09:00:00', '2026-02-01 10:00:00'),
					(102, 'NEXON_APIKEY', 'new-direct', '2026-01-02 09:00:00', '2026-02-02 10:00:00')
					""");
			statement.execute("INSERT INTO user_api_keys (user_id, encrypted_key) VALUES (101, 'new-social-ciphertext'), (102, 'new-direct-ciphertext')");
			assertThat(number(statement, "SELECT count(*) FROM data_contexts")).isEqualTo(2);
			assertThat(number(statement, "SELECT count(*) FROM user_data_contexts l JOIN data_contexts c ON c.id = l.context_id WHERE l.user_id = c.owner_user_id")).isEqualTo(2);
			assertThat(number(statement, "SELECT count(*) FROM user_api_keys k JOIN data_contexts c ON c.id = k.context_id WHERE k.user_id = c.owner_user_id AND k.key_fingerprint IS NULL")).isEqualTo(2);
			assertThat(number(statement, "SELECT count(*) FROM user_api_keys WHERE encrypted_key IN ('new-social-ciphertext', 'new-direct-ciphertext')")).isEqualTo(2);
			assertThat(number(statement, "SELECT count(*) FROM data_contexts WHERE owner_user_id = 101 AND kakao_user_id = 101 AND created_at = TIMESTAMPTZ '2026-01-01 09:00:00+09' AND updated_at = TIMESTAMPTZ '2026-02-01 10:00:00+09'")).isEqualTo(1);
			assertThat(number(statement, "SELECT count(*) FROM data_contexts WHERE owner_user_id = 102 AND kakao_user_id IS NULL")).isEqualTo(1);
			statement.execute("SELECT ensure_user_data_context(101)");
			statement.execute("SELECT ensure_user_data_context(101)");
			assertThat(number(statement, "SELECT count(*) FROM data_contexts")).isEqualTo(2);
			assertThat(number(statement, "SELECT count(*) FROM user_data_contexts")).isEqualTo(2);
			long directContext = number(statement, "SELECT id FROM data_contexts WHERE owner_user_id = 102");
			statement.execute("INSERT INTO user_api_keys (user_id, context_id, encrypted_key) VALUES (101, " + directContext + ", 'explicit-context-ciphertext')");
			assertThat(number(statement, "SELECT context_id FROM user_api_keys WHERE encrypted_key = 'explicit-context-ciphertext'")).isEqualTo(directContext);
			assertThat(number(statement, "SELECT count(*) FROM data_context_accounts")).isZero();
		}
	}

	@Test
	void automaticContextBootstrapRollsBackWithUserInsertion() throws Exception {
		flyway(null).migrate();
		try (Connection connection = connection(); Statement statement = connection.createStatement()) {
			connection.setAutoCommit(false);
			statement.execute("INSERT INTO users (id, oauth_provider, oauth_id) VALUES (101, 'KAKAO', 'rolled-back-user')");
			statement.execute("INSERT INTO user_api_keys (user_id, encrypted_key) VALUES (101, 'rolled-back-ciphertext')");
			assertThat(number(statement, "SELECT count(*) FROM data_contexts")).isEqualTo(1);
			connection.rollback();
			assertThat(number(statement, "SELECT count(*) FROM users")).isZero();
			assertThat(number(statement, "SELECT count(*) FROM data_contexts")).isZero();
			assertThat(number(statement, "SELECT count(*) FROM user_data_contexts")).isZero();
			assertThat(number(statement, "SELECT count(*) FROM user_api_keys")).isZero();
		}
	}

	@AfterEach
	void dropTestSchema() throws Exception {
		try (Connection connection = DriverManager.getConnection(System.getenv("FLYWAY_TEST_URL"),
				System.getenv("FLYWAY_TEST_USER"), System.getenv("FLYWAY_TEST_PASSWORD"));
				Statement statement = connection.createStatement()) {
			statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
		}
	}

	private Flyway flyway(String target) {
		var configuration = Flyway.configure()
				.dataSource(System.getenv("FLYWAY_TEST_URL"), System.getenv("FLYWAY_TEST_USER"), System.getenv("FLYWAY_TEST_PASSWORD"))
				.defaultSchema(schema).schemas(schema)
				.locations("filesystem:" + Path.of("src/main/resources/db/migration").toAbsolutePath());
		if (target != null) {
			configuration.target(target);
		}
		return configuration.load();
	}

	private Connection connection() throws Exception {
		Connection connection = DriverManager.getConnection(System.getenv("FLYWAY_TEST_URL"),
				System.getenv("FLYWAY_TEST_USER"), System.getenv("FLYWAY_TEST_PASSWORD"));
		connection.setSchema(schema);
		return connection;
	}

	private long number(Statement statement, String sql) throws Exception {
		try (ResultSet result = statement.executeQuery(sql)) {
			assertThat(result.next()).isTrue();
			return result.getLong(1);
		}
	}
}
