package com.maple.utility.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.util.ReflectionTestUtils;

import com.maple.utility.entity.BossDropItem;
import com.maple.utility.entity.BossItemAcquisition;
import com.maple.utility.entity.BossItemKind;
import com.maple.utility.entity.BossMaster;
import com.maple.utility.entity.BossPeriodEntry;
import com.maple.utility.entity.Difficulty;
import com.maple.utility.entity.MapleCharacter;
import com.maple.utility.entity.OAuthProvider;
import com.maple.utility.entity.ResetPeriod;
import com.maple.utility.entity.User;

import jakarta.persistence.EntityManager;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
class BossCrystalEntryQueryTest {

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private BossPeriodEntryRepository periodEntryRepository;

	@Autowired
	private BossItemAcquisitionRepository acquisitionRepository;

	@Test
	void findsPeriodEntriesAndCrystalEntryIdsInBulk() {
		// Production Flyway owns timestamp defaults; Hibernate's test schema needs the same defaults.
		for (String table : List.of("users", "characters", "boss_master", "boss_drop_items", "boss_period_entries",
				"boss_item_acquisitions")) {
			for (String column : List.of("created_at", "updated_at")) {
				entityManager.createNativeQuery("ALTER TABLE " + table + " ALTER COLUMN " + column
						+ " SET DEFAULT CURRENT_TIMESTAMP").executeUpdate();
			}
		}
		User user = User.create(OAuthProvider.KAKAO, "crystal-user", null, "결정");
		entityManager.persist(user);
		MapleCharacter character = MapleCharacter.create(user, "ocid-a", "꼬농", "엘리시움", "마법사", 294, 0);
		entityManager.persist(character);
		BossMaster withCrystal = boss("스우");
		BossMaster withoutCrystal = boss("윌");
		BossMaster otherPeriod = boss("루시드");
		LocalDate period = LocalDate.parse("2026-10-29");

		BossPeriodEntry checked = BossPeriodEntry.create(character, withCrystal, period, 2);
		BossPeriodEntry unchecked = BossPeriodEntry.create(character, withoutCrystal, period, 1);
		BossPeriodEntry previousWeek = BossPeriodEntry.create(character, otherPeriod, LocalDate.parse("2026-10-22"), 1);
		entityManager.persist(checked);
		entityManager.persist(unchecked);
		entityManager.persist(previousWeek);
		entityManager.persist(BossItemAcquisition.createForPeriod(
				checked, dropItem(withCrystal, "강렬한 힘의 결정", BossItemKind.CRYSTAL), 1, 100L, null));
		// 결정이 아닌 획득만 있는 기록은 결정 획득으로 보지 않음
		entityManager.persist(BossItemAcquisition.createForPeriod(
				unchecked, dropItem(withoutCrystal, "홍옥의 보스 반지 상자", BossItemKind.RANDOM), 1, null, null));
		entityManager.flush();
		entityManager.clear();

		List<BossPeriodEntry> entries = periodEntryRepository.findByCharacter_IdAndPeriodStart(character.getId(), period);

		assertThat(entries).extracting(entry -> entry.getBoss().getBossName()).containsExactlyInAnyOrder("스우", "윌");
		assertThat(acquisitionRepository.findCrystalEntryIds(entries.stream().map(BossPeriodEntry::getId).toList()))
				.containsExactly(checked.getId());
	}

	private BossMaster boss(String bossName) {
		BossMaster boss = BeanUtils.instantiateClass(BossMaster.class);
		ReflectionTestUtils.setField(boss, "bossName", bossName);
		ReflectionTestUtils.setField(boss, "difficulty", Difficulty.HARD);
		ReflectionTestUtils.setField(boss, "resetPeriod", ResetPeriod.WEEKLY);
		entityManager.persist(boss);
		return boss;
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
