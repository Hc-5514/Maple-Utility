package com.maple.utility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import com.maple.utility.exception.ApiException;
import com.maple.utility.service.DataContextMergeService;
import com.maple.utility.service.DataContextService;
import com.maple.utility.service.SchedulerSyncLock;

/** Executes production merge SQL against migrated PostgreSQL, including real foreign keys and rollback. */
@EnabledIfEnvironmentVariable(named = "FLYWAY_TEST_URL", matches = ".+")
class SharedLoginMergePostgresTest {

	private final String schema = "task259_merge_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
	private JdbcTemplate jdbc;
	private TransactionTemplate transactions;
	private DataContextMergeService merger;
	private String sessionTimeZone = "UTC";

	@BeforeEach
	void migrateIsolatedSchema() {
		Flyway.configure().dataSource(System.getenv("FLYWAY_TEST_URL"), System.getenv("FLYWAY_TEST_USER"),
				System.getenv("FLYWAY_TEST_PASSWORD")).defaultSchema(schema).schemas(schema)
				.locations("filesystem:" + Path.of("src/main/resources/db/migration").toAbsolutePath())
				.load().migrate();
		DriverManagerDataSource dataSource = new DriverManagerDataSource(System.getenv("FLYWAY_TEST_URL"),
				System.getenv("FLYWAY_TEST_USER"), System.getenv("FLYWAY_TEST_PASSWORD")) {
			@Override
			public Connection getConnection() throws SQLException {
				Connection connection = super.getConnection();
				try (var statement = connection.createStatement()) {
					// PgJDBC sends the JVM timezone at connection startup, overriding server options.
					statement.execute("SET TIME ZONE '" + sessionTimeZone + "'");
				} catch (SQLException exception) {
					connection.close();
					throw exception;
				}
				return connection;
			}
		};
		Properties properties = new Properties();
		properties.setProperty("currentSchema", schema);
		dataSource.setConnectionProperties(properties);
		jdbc = new JdbcTemplate(dataSource);
		transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
		merger = new DataContextMergeService(jdbc, new SchedulerSyncLock(jdbc));
	}

	@Test
	void directLoginRecordsWinOverOlderKakaoRecordsAndNonduplicateRecordsSurvive() {
		user(101, "KAKAO", "2025-01-01");
		user(102, "NEXON_APIKEY", "2026-01-01");
		character(301, 101, "same", "카카오 캐릭터", false);
		character(302, 102, "same", "직접 캐릭터", true);
		character(303, 101, "social-only", "비중복 캐릭터", true);
		hunting(401, 301, "2026-10-12", 100);
		hunting(402, 302, "2026-10-12", 200);
		hunting(403, 301, "2026-10-13", 300);
		hunting(404, 303, "2026-10-11", 400);
		jdbc.update("INSERT INTO user_selected_worlds(user_id, world_name) VALUES (101,'루나'),(102,'스카니아')");

		merge(102, 101);
		merge(102, 101);

		assertThat(number("SELECT count(*) FROM characters WHERE user_id=101")).isZero();
		assertThat(number("SELECT count(*) FROM characters WHERE user_id=102")).isEqualTo(2);
		assertThat(jdbc.queryForObject("SELECT character_name FROM characters WHERE id=302", String.class))
				.isEqualTo("직접 캐릭터");
		assertThat(jdbc.queryForObject("SELECT is_favorite FROM characters WHERE id=302", Boolean.class)).isTrue();
		assertThat(number("SELECT count(*) FROM hunting_records")).isEqualTo(3);
		assertThat(number("SELECT meso_earned FROM hunting_records WHERE character_id=302 AND record_date='2026-10-12'"))
				.isEqualTo(200);
		assertThat(number("SELECT character_id FROM hunting_records WHERE id=403")).isEqualTo(302);
		assertThat(number("SELECT character_id FROM hunting_records WHERE id=404")).isEqualTo(303);
		assertThat(jdbc.queryForList("SELECT world_name FROM user_selected_worlds WHERE user_id=102", String.class))
				.containsExactly("스카니아");
		assertThat(number("SELECT source_user_id FROM data_merge_provenance WHERE table_name='hunting_records' AND record_id=403"))
				.isEqualTo(101);
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void directLoginConflictUsesOldestIdentityThenSmallestUserId(boolean equalCreationTime) {
		user(101, "NEXON_APIKEY", "2025-01-01");
		user(102, "NEXON_APIKEY", equalCreationTime ? "2025-01-01" : "2026-01-01");
		character(301, 101, "same", "우선 캐릭터", true);
		character(302, 102, "same", "후순위 캐릭터", false);
		hunting(401, 301, "2026-10-12", 100);
		hunting(402, 302, "2026-10-12", 200);

		// Exercise preferred-source replacement rather than assuming the caller has picked the winner.
		merge(102, 101);

		assertThat(jdbc.queryForObject("SELECT character_name FROM characters WHERE id=302", String.class))
				.isEqualTo("우선 캐릭터");
		assertThat(number("SELECT meso_earned FROM hunting_records WHERE character_id=302")).isEqualTo(100);
		assertThat(number("SELECT source_user_id FROM data_merge_provenance WHERE table_name='characters' AND record_id=302"))
				.isEqualTo(101);
	}

	@Test
	void laterDirectLoginReplacesSocialOriginRecordDespiteOlderCanonicalDirectOwner() {
		user(101, "KAKAO", "2024-01-01");
		user(102, "NEXON_APIKEY", "2025-01-01");
		user(103, "NEXON_APIKEY", "2026-01-01");
		character(301, 101, "same", "사회", false);
		character(302, 102, "same", "최초 직접", true);
		character(303, 103, "same", "추가 직접", false);
		hunting(401, 301, "2026-10-12", 100);
		hunting(403, 303, "2026-10-12", 300);

		merge(102, 101);
		assertThat(number("SELECT source_user_id FROM data_merge_provenance WHERE table_name='hunting_records' AND record_id=401"))
				.isEqualTo(101);
		merge(102, 103);

		assertThat(number("SELECT count(*) FROM hunting_records")).isEqualTo(1);
		assertThat(number("SELECT meso_earned FROM hunting_records WHERE character_id=302")).isEqualTo(300);
		assertThat(number("SELECT source_user_id FROM data_merge_provenance WHERE table_name='hunting_records' AND record_id=403"))
				.isEqualTo(103);
		assertThat(jdbc.queryForObject("SELECT character_name FROM characters WHERE id=302", String.class))
				.isEqualTo("최초 직접");
	}

	@ParameterizedTest
	@CsvSource({"false,UTC", "true,UTC", "false,Asia/Seoul", "true,Asia/Seoul"})
	void acquisitionsFollowSurvivingPeriodForeignKeyAndKeepDistinctEvents(boolean sourcePreferred, String databaseTimeZone) {
		sessionTimeZone = databaseTimeZone;
		assertThat(jdbc.queryForObject("SHOW TIME ZONE", String.class)).isEqualTo(databaseTimeZone);
		user(101, sourcePreferred ? "NEXON_APIKEY" : "KAKAO", "2025-01-01");
		user(102, sourcePreferred ? "KAKAO" : "NEXON_APIKEY", "2026-01-01");
		character(301, 101, "same", "원본", false);
		character(302, 102, "same", "대상", true);
		long bossId = number("SELECT min(id) FROM boss_master WHERE reset_period='WEEKLY'");
		jdbc.update("INSERT INTO boss_drop_items(id,boss_id,item_name) VALUES (9001,?,'검증 아이템')", bossId);
		jdbc.update("INSERT INTO boss_period_entries(id,character_id,boss_id,reset_period,period_start,party_size) VALUES (501,301,?,'WEEKLY','2026-10-08',2),(502,302,?,'WEEKLY','2026-10-08',1)", bossId, bossId);
		acquisition(601, 301, 501, "복사 이벤트", 1, "2026-10-12 23:59:59+09");
		acquisition(602, 302, 502, "복사 이벤트", 1, "2026-10-12 23:59:59+09");
		acquisition(603, 301, 501, "별도 이벤트", 2, "2026-10-13 00:00:00+09");
		acquisition(604, 302, 502, "별도 이벤트", 2, "2026-10-13 00:00:01+09");
		// Legacy columns represent Seoul wall time, independent of the database/JVM timezone.
		assertThat(number("SELECT count(*) FROM boss_item_acquisitions WHERE acquired_date='2026-10-13'"))
				.isEqualTo(2);
		assertThat(jdbc.queryForObject("SELECT created_at FROM boss_item_acquisitions WHERE id=603", java.time.LocalDateTime.class))
				.isEqualTo(java.time.LocalDateTime.of(2026, 10, 13, 0, 0));

		merge(102, 101);

		long survivor = sourcePreferred ? 501 : 502;
		assertThat(number("SELECT count(*) FROM boss_period_entries")).isEqualTo(1);
		assertThat(number("SELECT character_id FROM boss_period_entries WHERE id=" + survivor)).isEqualTo(302);
		assertThat(number("SELECT count(*) FROM boss_item_acquisitions")).isEqualTo(3);
		assertThat(number("SELECT count(*) FROM boss_item_acquisitions WHERE character_id=302 AND boss_period_entry_id=" + survivor))
				.isEqualTo(3);
		assertThat(number("SELECT count(*) FROM boss_item_acquisitions a LEFT JOIN boss_period_entries p ON p.id=a.boss_period_entry_id WHERE p.id IS NULL OR a.character_id<>p.character_id"))
				.isZero();
		assertThat(number("SELECT count(*) FROM boss_item_acquisitions WHERE acquired_date='2026-10-13'"))
				.isEqualTo(2);
	}

	@Test
	void mergeMovesNonduplicateSchedulerRecordsFinalizationsAndFavoriteUnion() {
		user(101, "KAKAO", "2025-01-01");
		user(102, "NEXON_APIKEY", "2026-01-01");
		character(301, 101, "same", "원본", false);
		character(302, 102, "same", "대상", true);
		long bossId = number("SELECT min(id) FROM boss_master WHERE reset_period='WEEKLY'");
		jdbc.update("INSERT INTO scheduler_daily_records(character_id,record_date,content_name) VALUES (301,'2026-10-13','일일')");
		jdbc.update("INSERT INTO scheduler_weekly_records(character_id,week_start_date,content_name) VALUES (301,'2026-10-08','주간')");
		jdbc.update("INSERT INTO scheduler_boss_records(character_id,boss_id,record_date,reset_period) VALUES (301,?,'2026-10-13','WEEKLY')", bossId);
		jdbc.update("INSERT INTO boss_period_selections(character_id,boss_id,reset_period,period_start,is_visible) VALUES (301,?,'WEEKLY','2026-10-08',true)", bossId);
		jdbc.update("INSERT INTO scheduler_week_finalizations(user_id,character_id,week_start_date) VALUES (101,301,'2026-10-08')");
		var grounds = jdbc.queryForList("SELECT id FROM hunting_grounds ORDER BY id LIMIT 2", Long.class);
		assertThat(grounds).hasSize(2);
		jdbc.update("INSERT INTO user_hunting_ground_favorites(user_id,hunting_ground_id) VALUES (101,?),(101,?),(102,?)", grounds.get(0), grounds.get(1), grounds.get(0));

		merge(102, 101);

		for (String table : new String[] {"scheduler_daily_records", "scheduler_weekly_records", "scheduler_boss_records", "boss_period_selections", "scheduler_week_finalizations"}) {
			assertThat(number("SELECT count(*) FROM " + table + " WHERE character_id=302")).as(table).isEqualTo(1);
		}
		assertThat(number("SELECT user_id FROM scheduler_week_finalizations")).isEqualTo(102);
		assertThat(number("SELECT count(*) FROM user_hunting_ground_favorites WHERE user_id=102")).isEqualTo(2);
		assertThat(number("SELECT count(*) FROM user_hunting_ground_favorites WHERE user_id=101")).isZero();
	}

	@Test
	void activeSynchronizationRejectsMergeWithoutChangingRecordsOrProvenance() {
		user(101, "KAKAO", "2025-01-01");
		user(102, "NEXON_APIKEY", "2026-01-01");
		character(301, 101, "same", "원본", false);
		character(302, 102, "same", "대상", true);
		jdbc.update("INSERT INTO sync_jobs(user_id,job_type,status) VALUES (101,'CHARACTER','STARTED')");

		assertThatThrownBy(() -> merge(102, 101)).isInstanceOf(ApiException.class)
				.extracting("code").isEqualTo("DATA_CONTEXT_SYNC_IN_PROGRESS");

		assertThat(number("SELECT count(*) FROM characters")).isEqualTo(2);
		assertThat(number("SELECT user_id FROM characters WHERE id=301")).isEqualTo(101);
		assertThat(number("SELECT count(*) FROM data_merge_provenance")).isZero();
	}

	@Test
	void databaseFailureAfterMovingChildRecordRollsBackAllPriorMergeWrites() {
		user(101, "KAKAO", "2025-01-01");
		user(102, "NEXON_APIKEY", "2026-01-01");
		character(301, 101, "same", "원본", false);
		character(302, 102, "same", "대상", true);
		hunting(401, 301, "2026-10-13", 100);
		jdbc.update("INSERT INTO scheduler_daily_records(character_id,record_date,content_name) VALUES (301,'2026-10-13','일일')");
		jdbc.update("INSERT INTO user_selected_worlds(user_id,world_name) VALUES (101,'루나'),(102,'스카니아')");
		jdbc.execute("CREATE FUNCTION reject_hunting_move() RETURNS trigger AS $$ BEGIN RAISE EXCEPTION 'test merge failure'; END; $$ LANGUAGE plpgsql");
		jdbc.execute("CREATE TRIGGER reject_hunting_move BEFORE UPDATE ON hunting_records FOR EACH ROW EXECUTE FUNCTION reject_hunting_move()");

		assertThatThrownBy(() -> merge(102, 101)).isInstanceOf(DataAccessException.class)
				.hasMessageContaining("test merge failure");

		assertThat(number("SELECT count(*) FROM characters")).isEqualTo(2);
		assertThat(number("SELECT character_id FROM scheduler_daily_records")).isEqualTo(301);
		assertThat(number("SELECT character_id FROM hunting_records")).isEqualTo(301);
		assertThat(number("SELECT count(*) FROM user_selected_worlds")).isEqualTo(2);
		assertThat(number("SELECT count(*) FROM data_merge_provenance")).isZero();
	}

	@AfterEach
	void dropIsolatedSchema() {
		if (jdbc != null) jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
	}

	private void merge(long target, long source) {
		transactions.executeWithoutResult(status -> {
			jdbc.execute("SELECT pg_advisory_xact_lock(" + DataContextService.CONNECTION_LOCK + ")");
			merger.merge(target, source);
		});
	}

	private void user(long id, String provider, String createdAt) {
		jdbc.update("INSERT INTO users(id,oauth_provider,oauth_id,created_at) VALUES (?,?,?,?::timestamptz AT TIME ZONE 'Asia/Seoul')",
				id, provider, "merge-" + id, createdAt + " 00:00:00+09");
	}

	private void character(long id, long owner, String ocid, String name, boolean favorite) {
		jdbc.update("INSERT INTO characters(id,user_id,ocid,character_name,is_favorite) VALUES (?,?,?,?,?)",
				id, owner, ocid, name, favorite);
	}

	private void hunting(long id, long character, String date, long meso) {
		jdbc.update("INSERT INTO hunting_records(id,character_id,record_date,meso_earned) VALUES (?,?,?::date,?)",
				id, character, date, meso);
	}

	private void acquisition(long id, long character, long period, String memo, int quantity, String createdAt) {
		jdbc.update("INSERT INTO boss_item_acquisitions(id,character_id,boss_drop_item_id,boss_period_entry_id,acquired_date,memo,quantity,created_at) VALUES (?,?,9001,?,(?::timestamptz AT TIME ZONE 'Asia/Seoul')::date,?,?,?::timestamptz AT TIME ZONE 'Asia/Seoul')",
				id, character, period, createdAt, memo, quantity, createdAt);
	}

	private long number(String sql) {
		return jdbc.queryForObject(sql, Long.class);
	}
}
