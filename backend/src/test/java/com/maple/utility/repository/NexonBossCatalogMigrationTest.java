package com.maple.utility.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.maple.utility.service.BossNames;

/** 넥슨 스케줄러 응답의 주간·월간 보스가 마이그레이션 적용 후 카탈로그와 모두 매칭되는지 확인. */
@EnabledIfEnvironmentVariable(named = "FLYWAY_TEST_URL", matches = ".+")
class NexonBossCatalogMigrationTest {

	// 2026-11-05 넥슨 scheduler/character-state의 boss_contents 중 bossWeekly·bossMonthly (이름 난이도 주기)
	private static final String NEXON_BOSSES = """
			자쿰 chaos bossWeekly
			매그너스 hard bossWeekly
			파풀라투스 chaos bossWeekly
			피에르 chaos bossWeekly
			반반 chaos bossWeekly
			블러디퀸 chaos bossWeekly
			벨룸 chaos bossWeekly
			스우 normal bossWeekly
			스우 hard bossWeekly
			스우 extreme bossWeekly
			데미안 normal bossWeekly
			데미안 hard bossWeekly
			가디언 엔젤 슬라임 normal bossWeekly
			가디언 엔젤 슬라임 chaos bossWeekly
			루시드 easy bossWeekly
			루시드 normal bossWeekly
			루시드 hard bossWeekly
			윌 easy bossWeekly
			윌 normal bossWeekly
			윌 hard bossWeekly
			더스크 normal bossWeekly
			더스크 chaos bossWeekly
			진 힐라 normal bossWeekly
			진 힐라 hard bossWeekly
			듄켈 normal bossWeekly
			듄켈 hard bossWeekly
			검은 마법사 hard bossMonthly
			검은 마법사 extreme bossMonthly
			선택받은 세렌 normal bossWeekly
			선택받은 세렌 hard bossWeekly
			선택받은 세렌 extreme bossWeekly
			감시자 칼로스 easy bossWeekly
			감시자 칼로스 normal bossWeekly
			감시자 칼로스 chaos bossWeekly
			감시자 칼로스 extreme bossWeekly
			카링 easy bossWeekly
			카링 normal bossWeekly
			카링 hard bossWeekly
			카링 extreme bossWeekly
			림보 normal bossWeekly
			림보 hard bossWeekly
			림보 extreme bossWeekly
			발드릭스 normal bossWeekly
			발드릭스 hard bossWeekly
			발드릭스 extreme bossWeekly
			최초의 대적자 easy bossWeekly
			최초의 대적자 normal bossWeekly
			최초의 대적자 hard bossWeekly
			최초의 대적자 extreme bossWeekly
			찬란한 흉성 normal bossWeekly
			찬란한 흉성 hard bossWeekly
			유피테르 normal bossWeekly
			유피테르 hard bossWeekly
			시즌 보스 메이린 normal bossWeekly
			시즌 보스 메이린 hard bossWeekly
			벨로나 easy bossWeekly
			벨로나 normal bossWeekly
			벨로나 hard bossWeekly
			""";

	@Test
	void newCatalogEntriesAreAddedWithPartyLimitsAndDropItems() throws Exception {
		try (var database = new SyncJobRepositoryTest.Database("24")) {
			assertThat(count(database, "림보", "EXTREME")).isZero();

			database.migrate(null);

			var rows = database.jdbc.queryForList("""
					SELECT boss_name, difficulty, reset_period, crystal_price, max_party_size, is_active
					FROM boss_master
					WHERE (boss_name, difficulty) IN (('림보', 'EXTREME'), ('발드릭스', 'EXTREME'),
					      ('시즌 보스 메이린', 'NORMAL'), ('시즌 보스 메이린', 'HARD'))
					ORDER BY boss_name, difficulty
					""");
			assertThat(rows).extracting(row -> row.get("boss_name"), row -> row.get("difficulty"),
					row -> row.get("reset_period"), row -> row.get("crystal_price"),
					row -> row.get("max_party_size"), row -> row.get("is_active"))
					.containsExactly(
							tuple("림보", "EXTREME", "WEEKLY", 7_500_000_000L, 3, true),
							tuple("발드릭스", "EXTREME", "WEEKLY", 9_600_000_000L, 3, true),
							tuple("시즌 보스 메이린", "HARD", "WEEKLY", 0L, 1, true),
							tuple("시즌 보스 메이린", "NORMAL", "WEEKLY", 0L, 1, true));

			// 시즌 보스 메이린은 드랍 아이템이 없음
			assertThat(dropItems(database, "시즌 보스 메이린", "NORMAL")).isEmpty();
			assertThat(dropItems(database, "시즌 보스 메이린", "HARD")).isEmpty();

			// 림보·발드릭스 익스트림 = 하드 활성 아이템 − 고정 아이템 + 익셉셔널 해머 + 영롱한 달빛 포션
			for (var spec : List.of(
					tuple("림보", "왜곡된 욕망의 결정", "익셉셔널 해머(포켓)"),
					tuple("발드릭스", "영원한 충성의 흔적", "익셉셔널 해머(펜던트)"))) {
				String boss = (String) spec.toList().get(0);
				String excluded = (String) spec.toList().get(1);
				String hammer = (String) spec.toList().get(2);
				var hard = dropItems(database, boss, "HARD");
				var extreme = dropItems(database, boss, "EXTREME");
				assertThat(hard).extracting(item -> item.get("item_name")).contains(excluded);
				// 익스트림에서는 하드의 고정 아이템이 드랍되지 않음
				assertThat(extreme).extracting(item -> item.get("item_name")).doesNotContain(excluded);
				assertThat(extreme).filteredOn(item -> item.get("item_kind").equals("FIXED")).isEmpty();
				var expected = new ArrayList<>(hard.stream().filter(item -> !item.get("item_name").equals(excluded)).toList());
				assertThat(extreme).containsAll(expected);
				assertThat(extreme).extracting(item -> item.get("item_name"))
						.contains(hammer, "영롱한 달빛 포션", "강렬한 힘의 결정")
						.hasSize(expected.size() + 2);
			}
		}
	}

	@Test
	void everyNexonWeeklyAndMonthlyBossMatchesTheCatalogWithTheSamePeriod() throws Exception {
		try (var database = new SyncJobRepositoryTest.Database(null)) {
			List<String> unmatched = new ArrayList<>();
			int checked = 0;
			for (String line : NEXON_BOSSES.strip().split("\n")) {
				String[] part = line.strip().split(" ");
				String cycle = part[part.length - 1];
				String difficulty = part[part.length - 2].toUpperCase();
				String name = String.join(" ", java.util.Arrays.copyOfRange(part, 0, part.length - 2));
				String period = cycle.equals("bossMonthly") ? "MONTHLY" : "WEEKLY";
				Integer matches = database.jdbc.queryForObject("""
						SELECT count(*) FROM boss_master
						WHERE replace(boss_name, ' ', '') = ? AND difficulty = ? AND reset_period = ? AND is_active
						""", Integer.class, BossNames.lookupKey(name), difficulty, period);
				checked++;
				if (matches == null || matches != 1) {
					unmatched.add(name + " " + difficulty + " " + period);
				}
			}
			assertThat(checked).isEqualTo(58);
			assertThat(unmatched).isEmpty();
		}
	}

	private List<java.util.Map<String, Object>> dropItems(SyncJobRepositoryTest.Database database, String name, String difficulty) {
		return database.jdbc.queryForList("""
				SELECT d.item_name, d.item_kind, d.default_quantity
				FROM boss_drop_items d JOIN boss_master m ON m.id = d.boss_id
				WHERE m.boss_name = ? AND m.difficulty = ? AND d.is_active
				ORDER BY d.item_name
				""", name, difficulty);
	}

	private int count(SyncJobRepositoryTest.Database database, String name, String difficulty) {
		return database.jdbc.queryForObject(
				"SELECT count(*) FROM boss_master WHERE boss_name = ? AND difficulty = ?", Integer.class, name, difficulty);
	}
}
