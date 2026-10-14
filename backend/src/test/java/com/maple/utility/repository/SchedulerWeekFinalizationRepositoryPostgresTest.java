package com.maple.utility.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

@EnabledIfEnvironmentVariable(named = "FLYWAY_TEST_URL", matches = ".+")
class SchedulerWeekFinalizationRepositoryPostgresTest {
	private static final LocalDate TODAY = LocalDate.of(2026, 10, 15);
	private static final LocalDateTime NOW = TODAY.atTime(2, 0);
	private Connection connection;
	private JdbcTemplate jdbc;
	private SchedulerWeekFinalizationRepository repository;

	@BeforeEach
	void setUp() throws Exception {
		connection = DriverManager.getConnection(System.getenv("FLYWAY_TEST_URL"), System.getenv("FLYWAY_TEST_USER"), System.getenv("FLYWAY_TEST_PASSWORD"));
		connection.setAutoCommit(false);
		jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
		String schema = "recent_week_" + UUID.randomUUID().toString().replace("-", "");
		jdbc.execute("CREATE SCHEMA " + schema);
		jdbc.execute("SET LOCAL search_path TO " + schema);
		jdbc.execute("CREATE TABLE characters (id bigint PRIMARY KEY, user_id bigint, is_favorite boolean)");
		jdbc.execute("""
				CREATE TABLE scheduler_week_finalizations (
				 id bigserial PRIMARY KEY, user_id bigint, character_id bigint, week_start_date date,
				 status text DEFAULT 'PENDING', attempts int DEFAULT 0, last_error text,
				 last_attempt_at timestamp, finalized_at timestamp, updated_at timestamp,
				 UNIQUE(character_id,week_start_date))
				""");
		jdbc.update("INSERT INTO characters VALUES (1,10,true),(2,10,true),(3,20,true),(4,10,false)");
		repository = new SchedulerWeekFinalizationRepository(jdbc);
	}

	@AfterEach
	void tearDown() throws Exception {
		if (connection != null) { connection.rollback(); connection.close(); }
	}

	@Test
	void expirationPreservesFinalizedSnapshotsAndOnlyCurrentScopeCanBeLocked() {
		for (String status : new String[] { "PENDING", "RETRY", "NO_DATA", "FINALIZED" }) {
			jdbc.update("INSERT INTO scheduler_week_finalizations (user_id,character_id,week_start_date,status) VALUES (10,?, '2026-10-01',?)",
					status.equals("PENDING") ? 1 : status.equals("RETRY") ? 2 : status.equals("NO_DATA") ? 3 : 4, status);
		}
		repository.seed(10L, LocalDate.of(2026, 10, 8));
		repository.expire(TODAY, NOW);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM scheduler_week_finalizations WHERE status='EXPIRED' AND last_error='RECENT_WEEK_SCOPE_EXPIRED'", Integer.class)).isEqualTo(3);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM scheduler_week_finalizations WHERE status='FINALIZED'", Integer.class)).isEqualTo(1);
		assertThat(repository.due(10L, TODAY)).hasSize(2).allMatch(target -> target.weekStartDate().equals(LocalDate.of(2026, 10, 8)));
		assertThat(repository.lockDue(1L, TODAY)).isEmpty();
		assertThat(repository.due(20L, TODAY)).isEmpty();
	}

	@Test
	void missingAndDeferredTargetsAreDetectedWithoutConfusingFinalizedWork() {
		assertThat(repository.hasDueOrUnseeded(10L, TODAY)).isTrue();
		repository.seed(10L, LocalDate.of(2026, 10, 8));
		repository.seed(10L, LocalDate.of(2026, 10, 8));
		assertThat(repository.due(10L, TODAY)).hasSize(2);
		var targets = repository.due(10L, TODAY);
		repository.finish(targets.getFirst().id(), true, NOW);
		repository.finish(targets.getLast().id(), false, NOW);
		assertThat(repository.hasDueOrUnseeded(10L, TODAY)).isFalse();
		assertThat(repository.due(10L, TODAY)).isEmpty();
		assertThat(repository.hasDueOrUnseeded(10L, TODAY.plusDays(1))).isTrue();
		assertThat(repository.due(10L, TODAY.plusDays(1))).hasSize(1);
		assertThat(repository.hasDueOrUnseeded(999L, TODAY)).isFalse();
	}
}
