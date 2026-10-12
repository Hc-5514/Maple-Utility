package com.maple.utility.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

import com.maple.utility.config.QuerydslConfig;
import com.maple.utility.dto.response.PageResponse;
import com.maple.utility.dto.response.StatsBossItemResponse;
import com.maple.utility.entity.BossDropItem;
import com.maple.utility.entity.BossItemAcquisition;
import com.maple.utility.entity.BossItemKind;
import com.maple.utility.entity.BossMaster;
import com.maple.utility.entity.Difficulty;
import com.maple.utility.entity.MapleCharacter;
import com.maple.utility.entity.OAuthProvider;
import com.maple.utility.entity.ResetPeriod;
import com.maple.utility.entity.User;

import jakarta.persistence.EntityManager;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
@Import({QuerydslConfig.class, StatsQueryRepository.class})
class StatsQueryRepositoryTest {

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private StatsQueryRepository repository;

	@Test
	void itemPagesExcludeCrystalsButCrystalIncomeKeepsThem() {
		// Production Flyway owns timestamp defaults; Hibernate's test schema needs the same defaults.
		for (String table : List.of("users", "characters", "boss_master", "boss_drop_items", "boss_item_acquisitions")) {
			for (String column : List.of("created_at", "updated_at")) {
				entityManager.createNativeQuery("ALTER TABLE " + table + " ALTER COLUMN " + column
						+ " SET DEFAULT CURRENT_TIMESTAMP").executeUpdate();
			}
		}
		User user = User.create(OAuthProvider.KAKAO, "preview-user", null, "미리보기");
		entityManager.persist(user);
		MapleCharacter character = MapleCharacter.create(user, "ocid-a", "꼬농", "엘리시움", "마법사", 294, 0);
		entityManager.persist(character);
		MapleCharacter other = MapleCharacter.create(user, "ocid-b", "말랑꼬농", "엘리시움", "궁수", 285, 1);
		entityManager.persist(other);
		BossMaster boss = BeanUtils.instantiateClass(BossMaster.class);
		ReflectionTestUtils.setField(boss, "bossName", "스우");
		ReflectionTestUtils.setField(boss, "difficulty", Difficulty.HARD);
		ReflectionTestUtils.setField(boss, "resetPeriod", ResetPeriod.WEEKLY);
		entityManager.persist(boss);

		BossDropItem drop = dropItem(boss, "일반 전리품", BossItemKind.RANDOM);
		BossDropItem crystal = dropItem(boss, "강렬한 힘의 결정", BossItemKind.CRYSTAL);
		for (int index = 0; index < 11; index++) {
			entityManager.persist(BossItemAcquisition.create(character, drop, LocalDate.parse("2026-10-07"), null));
		}
		entityManager.persist(BossItemAcquisition.create(other, drop, LocalDate.parse("2026-09-30"), null));
		BossItemAcquisition crystalAcquisition = BossItemAcquisition.create(
				character, crystal, LocalDate.parse("2026-10-07"), null);
		ReflectionTestUtils.setField(crystalAcquisition, "mesoAmount", 12_000L);
		entityManager.persist(crystalAcquisition);
		entityManager.flush();
		entityManager.clear();

		PageResponse<StatsBossItemResponse> first = repository.findBossItemStats(
				List.of(character.getId()), null, null, null, 0, 10);
		PageResponse<StatsBossItemResponse> second = repository.findBossItemStats(
				List.of(character.getId()), null, null, null, 1, 10);

		assertThat(first.totalElements()).isEqualTo(11);
		assertThat(first.totalPages()).isEqualTo(2);
		assertThat(first.content()).hasSize(10).allSatisfy(item -> assertThat(item.itemName()).isEqualTo("일반 전리품"));
		assertThat(second.content()).hasSize(1);
		assertThat(repository.findBossItemStats(List.of(character.getId(), other.getId()), null,
				LocalDate.parse("2026-10-01"), null, 0, 20).totalElements()).isEqualTo(11);
		assertThat(repository.findBossItemStats(List.of(other.getId()), null, null, null, 0, 10).totalElements())
				.isEqualTo(1);
		assertThat(repository.findBossItemStats(List.of(), null, null, null, 0, 10).totalElements())
				.isZero();
		assertThat(repository.findBossItemStats(List.of(character.getId(), other.getId()), "스우", null, null, 0, 10)
				.totalElements()).isEqualTo(12);
		assertThat(repository.findBossItemStats(List.of(character.getId(), other.getId()), "자쿰", null, null, 0, 10)
				.totalElements()).isZero();
		assertThat(repository.findCompletedBossCrystalRows(List.of(character.getId()), null, null))
				.extracting(StatsQueryRepository.CrystalIncomeRow::income)
				.containsExactly(12_000L);
	}

	@Test
	void activeBossNamesFollowCatalogSortOrderOnce() {
		entityManager.createNativeQuery("ALTER TABLE boss_master ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP")
				.executeUpdate();
		entityManager.createNativeQuery("ALTER TABLE boss_master ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP")
				.executeUpdate();
		boss("검은 마법사", Difficulty.HARD, 330, true);
		boss("자쿰", Difficulty.CHAOS, 100, true);
		boss("스우", Difficulty.NORMAL, 170, true);
		boss("스우", Difficulty.HARD, 171, true);
		boss("매그너스", Difficulty.HARD, 110, true);
		boss("비활성 보스", Difficulty.EASY, 1, false);
		entityManager.flush();

		assertThat(repository.findActiveBossNames()).containsExactly("자쿰", "매그너스", "스우", "검은 마법사");
	}

	private void boss(String bossName, Difficulty difficulty, int sortOrder, boolean active) {
		BossMaster boss = BeanUtils.instantiateClass(BossMaster.class);
		ReflectionTestUtils.setField(boss, "bossName", bossName);
		ReflectionTestUtils.setField(boss, "difficulty", difficulty);
		ReflectionTestUtils.setField(boss, "resetPeriod", ResetPeriod.WEEKLY);
		ReflectionTestUtils.setField(boss, "sortOrder", sortOrder);
		ReflectionTestUtils.setField(boss, "active", active);
		entityManager.persist(boss);
	}

	private BossDropItem dropItem(BossMaster boss, String itemName, BossItemKind itemKind) {
		BossDropItem item = BeanUtils.instantiateClass(BossDropItem.class);
		ReflectionTestUtils.setField(item, "boss", boss);
		ReflectionTestUtils.setField(item, "itemName", itemName);
		ReflectionTestUtils.setField(item, "itemKind", itemKind);
		ReflectionTestUtils.setField(item, "defaultQuantity", 1);
		entityManager.persist(item);
		return item;
	}
}
