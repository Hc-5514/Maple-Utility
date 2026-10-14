package com.maple.utility.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import com.maple.utility.repository.SchedulerMonthlyFinalizationRepository.BossSnapshot;

@EnabledIfEnvironmentVariable(named="FLYWAY_TEST_URL",matches=".+")
class SchedulerMonthlyFinalizationRepositoryPostgresTest {
	Connection connection; JdbcTemplate jdbc; SchedulerMonthlyFinalizationRepository repository;
	static final LocalDate TODAY=LocalDate.of(2026,10,1);
	static final LocalDateTime NOW=TODAY.atTime(2,0);
	@BeforeEach void setup() throws Exception {
		connection=DriverManager.getConnection(System.getenv("FLYWAY_TEST_URL"),System.getenv("FLYWAY_TEST_USER"),System.getenv("FLYWAY_TEST_PASSWORD"));
		connection.setAutoCommit(false); jdbc=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
		String schema="month_test_"+UUID.randomUUID().toString().replace("-","");
		jdbc.execute("CREATE SCHEMA "+schema); jdbc.execute("SET LOCAL search_path TO "+schema);
		jdbc.execute("CREATE TABLE users(id bigint PRIMARY KEY); CREATE TABLE characters(id bigint PRIMARY KEY,user_id bigint,is_favorite boolean); CREATE TABLE boss_master(id bigint PRIMARY KEY)");
		jdbc.execute("CREATE FUNCTION update_updated_at() RETURNS TRIGGER AS $$ BEGIN NEW.updated_at=CURRENT_TIMESTAMP; RETURN NEW; END; $$ LANGUAGE plpgsql");
		jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/V18__scheduler_monthly_finalizations.sql")));
		jdbc.update("INSERT INTO users VALUES(10),(20); INSERT INTO characters VALUES(1,10,true),(2,10,true),(3,20,true),(4,10,false); INSERT INTO boss_master VALUES(100)");
		repository=new SchedulerMonthlyFinalizationRepository(jdbc);
	}
	@AfterEach void cleanup() throws Exception { if(connection!=null) { connection.rollback(); connection.close(); } }
	@Test void seedAndReadPreserveFalseAndEmptyFinalizedSnapshot() {
		assertThat(repository.hasDueOrUnseeded(10L,TODAY.minusMonths(1),TODAY)).isTrue();
		repository.seed(10L,TODAY.minusMonths(1)); repository.seed(10L,TODAY.minusMonths(1));
		var targets=repository.due(10L,TODAY); assertThat(targets).hasSize(2);
		repository.finish(targets.getFirst(),List.of(new BossSnapshot(100,false)),NOW);
		repository.finish(targets.getLast(),List.of(),NOW);
		assertThat(repository.findFinalized(1L,TODAY.minusMonths(1)).orElseThrow().bosses()).containsExactly(new BossSnapshot(100,false));
		assertThat(repository.findFinalized(2L,TODAY.minusMonths(1)).orElseThrow().bosses()).isEmpty();
		assertThat(repository.hasDueOrUnseeded(10L,TODAY.minusMonths(1),TODAY)).isFalse();
		assertThat(repository.lockDue(targets.getFirst().id(),TODAY)).isEmpty();
		assertThat(repository.due(20L,TODAY)).isEmpty();
	}
	@Test void noDataRetryAndExpirationRespectInclusiveThirteenDayBoundary() {
		repository.seed(10L,TODAY.minusMonths(1)); var targets=repository.due(10L,TODAY);
		repository.noData(targets.getFirst().id(),NOW); repository.retry(targets.getLast().id(),"BUDGET",false,NOW);
		assertThat(repository.due(10L,TODAY)).isEmpty();
		assertThat(repository.due(10L,TODAY.plusDays(12))).hasSize(2);
		assertThat(repository.expire(TODAY.plusDays(12),NOW.plusDays(12))).isZero();
		assertThat(repository.expire(TODAY.plusDays(13),NOW.plusDays(13))).isEqualTo(2);
		assertThat(repository.due(10L,TODAY.plusDays(13))).isEmpty();
		assertThat(jdbc.queryForObject("SELECT SUM(attempts) FROM scheduler_monthly_finalizations",Integer.class)).isEqualTo(1);
	}
	@Test void snapshotInsertionAndStateCanBeRolledBackTogether() throws Exception {
		repository.seed(10L,TODAY.minusMonths(1)); var target=repository.due(10L,TODAY).getFirst();
		var savepoint=connection.setSavepoint();
		assertThatThrownBy(() -> repository.finish(target,List.of(new BossSnapshot(100,true),new BossSnapshot(999,true)),NOW)).isInstanceOf(RuntimeException.class);
		connection.rollback(savepoint);
		assertThat(repository.findFinalized(target.characterId(),target.monthStartDate())).isEmpty();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM scheduler_monthly_boss_snapshots",Integer.class)).isZero();
		assertThat(repository.lockDue(target.id(),TODAY)).isPresent();
	}
	@Test void expirationCannotRemoveFinalizedSnapshots() {
		repository.seed(10L,TODAY.minusMonths(1)); var target=repository.due(10L,TODAY).getFirst();
		repository.finish(target,List.of(new BossSnapshot(100,true)),NOW);
		repository.expire(TODAY.plusMonths(2),NOW.plusMonths(2));
		assertThat(repository.findFinalized(target.characterId(),target.monthStartDate()).orElseThrow().bosses()).containsExactly(new BossSnapshot(100,true));
	}
}
