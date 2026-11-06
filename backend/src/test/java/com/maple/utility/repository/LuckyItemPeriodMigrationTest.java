package com.maple.utility.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Date;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataIntegrityViolationException;

@EnabledIfEnvironmentVariable(named = "FLYWAY_TEST_URL", matches = ".+")
class LuckyItemPeriodMigrationTest {

	@Test
	void classifiesOnlyTheApprovedCatalogAndPreservesFixedAndCrystalItems() throws Exception {
		try (var db = new SyncJobRepositoryTest.Database("25")) {
			var original = db.jdbc.queryForList("SELECT id, item_kind FROM boss_drop_items WHERE item_kind <> 'RANDOM'");
			db.migrate(null);

			assertThat(db.jdbc.queryForObject("SELECT count(DISTINCT item_name) FROM boss_drop_items WHERE item_kind = 'LUCKY'", Integer.class))
					.isEqualTo(44);
			assertThat(db.jdbc.queryForList("SELECT DISTINCT item_name FROM boss_drop_items WHERE item_kind = 'LUCKY'", String.class))
					.contains("1단계 소울 에테르", "2단계 소울 에테르", "3단계 소울 에테르", "4단계 소울 에테르",
							"녹옥의 보스 반지 상자", "생명의 보스 반지 상자", "혼돈의 칠흑 장신구 상자",
							"익셉셔널 해머(펜던트)", "익셉셔널 해머(포켓)")
					.doesNotContain("파풀라투스 마크", "가디언 엔젤 링", "루인 포스실드", "저주받은 카이세리움",
							"데이브레이크 펜던트", "아케인셰이드 무기 상자", "스우로이드", "솔 에르다 조각");
			assertThat(db.jdbc.queryForList("SELECT id, item_kind FROM boss_drop_items WHERE item_kind NOT IN ('RANDOM', 'LUCKY')"))
					.containsExactlyInAnyOrderElementsOf(original);
		}
	}

	@Test
	void backfillsLinkedAndLegacyPeriodsWithoutChangingAcquisitionData() throws Exception {
		try (var db = new SyncJobRepositoryTest.Database("25")) {
			Long user = db.createUser();
			Long character = db.jdbc.queryForObject("INSERT INTO characters (user_id, ocid, character_name) VALUES (?, 'period-test', '기간검증') RETURNING id", Long.class, user);
			Long weeklyBoss = boss(db, "스우", "HARD");
			Long monthlyBoss = boss(db, "검은 마법사", "HARD");
			Long entry = db.jdbc.queryForObject("INSERT INTO boss_period_entries (character_id, boss_id, reset_period, period_start) VALUES (?, ?, 'WEEKLY', '2026-11-05') RETURNING id", Long.class, character, weeklyBoss);
			Long linked = acquisition(db, character, weeklyBoss, "2026-11-19", entry);
			Long weekly = acquisition(db, character, weeklyBoss, "2026-11-04", null);
			Long monthly = acquisition(db, character, monthlyBoss, "2028-02-29", null);
			var before = db.jdbc.queryForList("SELECT id, character_id, boss_drop_item_id, acquired_date, boss_period_entry_id, memo, quantity, meso_amount, detail_item FROM boss_item_acquisitions ORDER BY id");

			db.migrate(null);

			assertThat(db.jdbc.queryForList("SELECT id, character_id, boss_drop_item_id, acquired_date, boss_period_entry_id, memo, quantity, meso_amount, detail_item FROM boss_item_acquisitions ORDER BY id"))
					.containsExactlyElementsOf(before);
			assertPeriod(db, linked, "2026-11-05", "2026-11-11");
			assertPeriod(db, weekly, "2026-10-29", "2026-11-04");
			assertPeriod(db, monthly, "2028-02-01", "2028-02-29");
			assertThatThrownBy(() -> db.jdbc.update("UPDATE boss_item_acquisitions SET period_end = period_start - 1 WHERE id = ?", linked))
					.isInstanceOf(DataIntegrityViolationException.class);
			assertThatThrownBy(() -> db.jdbc.update("UPDATE boss_item_acquisitions SET period_start = NULL WHERE id = ?", linked))
					.isInstanceOf(DataIntegrityViolationException.class);
		}
	}

	@Test
	void doesNotPromoteUnknownRandomItems() throws Exception {
		try (var db = new SyncJobRepositoryTest.Database("25")) {
			db.jdbc.update("INSERT INTO boss_drop_items (boss_id, item_name, item_kind) VALUES (?, '미확인 확률 보상', 'RANDOM')", boss(db, "스우", "HARD"));
			db.migrate(null);
			assertThat(db.jdbc.queryForObject("SELECT item_kind FROM boss_drop_items WHERE item_name = '미확인 확률 보상'", String.class)).isEqualTo("RANDOM");
		}
	}

	private Long boss(SyncJobRepositoryTest.Database db, String name, String difficulty) {
		return db.jdbc.queryForObject("SELECT id FROM boss_master WHERE boss_name = ? AND difficulty = ?", Long.class, name, difficulty);
	}

	private Long acquisition(SyncJobRepositoryTest.Database db, Long character, Long boss, String date, Long entry) {
		return db.jdbc.queryForObject("""
				INSERT INTO boss_item_acquisitions (character_id, boss_drop_item_id, acquired_date, boss_period_entry_id, memo, quantity, meso_amount, detail_item)
				SELECT ?, id, ?::date, ?, '원본 메모', 2, 12345, '원본 상세'
				FROM boss_drop_items WHERE boss_id = ? AND item_name = '강렬한 힘의 결정' RETURNING id
				""", Long.class, character, date, entry, boss);
	}

	private void assertPeriod(SyncJobRepositoryTest.Database db, Long id, String start, String end) {
		assertThat(db.jdbc.queryForMap("SELECT period_start, period_end FROM boss_item_acquisitions WHERE id = ?", id))
				.containsEntry("period_start", Date.valueOf(start)).containsEntry("period_end", Date.valueOf(end));
	}
}
