package com.maple.utility.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "FLYWAY_TEST_URL", matches = ".+")
class CatalogReconcileMigrationTest {
	@Test
	void freshDatabaseHasPricesAndNoUnusedFragments() throws Exception {
		try (var db = new SyncJobRepositoryTest.Database(null)) {
			assertThat(db.jdbc.queryForList("SELECT crystal_price FROM boss_master WHERE boss_name IN ('자쿰','매그너스','반반','피에르','블러디 퀸','벨룸','파풀라투스') ORDER BY boss_name", Long.class))
					.containsExactly(4280000L, 4070000L, 4640000L, 4070000L, 4040000L, 6550000L, 4080000L);
			assertThat(db.jdbc.queryForObject("SELECT count(*) FROM boss_master WHERE crystal_price=0 AND boss_name='시즌 보스 메이린'", Integer.class)).isEqualTo(2);
			assertThat(db.jdbc.queryForObject("SELECT count(*) FROM boss_drop_items WHERE item_name='솔 에르다 조각'", Integer.class)).isZero();
		}
	}

	@Test
	void upgradePreservesActiveDropsAndReferencedLegacyAcquisitions() throws Exception {
		try (var db = new SyncJobRepositoryTest.Database("26")) {
			Long user = db.createUser();
			Long character = db.jdbc.queryForObject("INSERT INTO characters(user_id,ocid,character_name) VALUES (?,'legacy-test','검증') RETURNING id", Long.class, user);
			db.jdbc.update("INSERT INTO boss_item_acquisitions(character_id,boss_drop_item_id,acquired_date,period_start,period_end) SELECT ?,min(id),'2026-10-29','2026-10-29','2026-11-04' FROM boss_drop_items WHERE item_name='솔 에르다 조각'", character);
			var records = db.jdbc.queryForList("SELECT * FROM boss_item_acquisitions");
			var active = db.jdbc.queryForList("SELECT * FROM boss_drop_items WHERE is_active ORDER BY id");
			db.migrate(null);
			assertThat(db.jdbc.queryForList("SELECT * FROM boss_item_acquisitions")).isEqualTo(records);
			assertThat(db.jdbc.queryForList("SELECT * FROM boss_drop_items WHERE is_active ORDER BY id")).isEqualTo(active);
			assertThat(db.jdbc.queryForObject("SELECT count(*) FROM boss_drop_items WHERE item_name='솔 에르다 조각'", Integer.class)).isEqualTo(1);
		}
	}

	@Test
	void alreadyCorrectedPricesAreUnchanged() throws Exception {
		try (var db = new SyncJobRepositoryTest.Database("27")) {
			var before = db.jdbc.queryForList("SELECT * FROM boss_master ORDER BY id");
			db.jdbc.execute("DELETE FROM flyway_schema_history WHERE version='27'");
			db.migrate(null);
			assertThat(db.jdbc.queryForList("SELECT * FROM boss_master ORDER BY id")).isEqualTo(before);
		}
	}

	@Test
	void unexpectedPriceRollsBackAllPrices() throws Exception {
		try (var db = new SyncJobRepositoryTest.Database("26")) {
			db.jdbc.update("UPDATE boss_master SET crystal_price=123 WHERE boss_name='자쿰'");
			var before = db.jdbc.queryForList("SELECT * FROM boss_master ORDER BY id");
			assertThatThrownBy(() -> db.migrate(null)).isInstanceOf(RuntimeException.class);
			assertThat(db.jdbc.queryForList("SELECT * FROM boss_master ORDER BY id")).isEqualTo(before);
		}
	}

	@Test
	void missingBossFailsWithoutPartialUpdate() throws Exception {
		try (var db = new SyncJobRepositoryTest.Database("26")) {
			db.jdbc.update("UPDATE boss_master SET boss_name='검증용 이름' WHERE boss_name='자쿰'");
			assertThatThrownBy(() -> db.migrate(null)).isInstanceOf(RuntimeException.class);
			assertThat(db.jdbc.queryForObject("SELECT crystal_price FROM boss_master WHERE boss_name='매그너스'", Long.class)).isZero();
		}
	}
}
