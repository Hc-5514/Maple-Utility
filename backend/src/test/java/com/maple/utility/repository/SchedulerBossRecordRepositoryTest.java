package com.maple.utility.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.util.ReflectionTestUtils;

import com.maple.utility.entity.BossMaster;
import com.maple.utility.entity.Difficulty;
import com.maple.utility.entity.MapleCharacter;
import com.maple.utility.entity.OAuthProvider;
import com.maple.utility.entity.ResetPeriod;
import com.maple.utility.entity.SchedulerBossRecord;
import com.maple.utility.entity.User;

import jakarta.persistence.EntityManager;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
class SchedulerBossRecordRepositoryTest {

	@Autowired
	private SchedulerBossRecordRepository repository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void returnsLatestSnapshotOncePerBossWithinSelectedPeriod() {
		for (String table : List.of("users", "characters", "boss_master", "scheduler_boss_records")) {
			for (String column : List.of("created_at", "updated_at")) {
				entityManager.createNativeQuery("ALTER TABLE " + table + " ALTER COLUMN " + column
						+ " SET DEFAULT CURRENT_TIMESTAMP").executeUpdate();
			}
		}

		User user = User.create(OAuthProvider.KAKAO, "scheduler-user", null, "스케줄러 사용자");
		entityManager.persist(user);
		MapleCharacter character = MapleCharacter.create(user, "ocid", "꼬농", "엘리시움", "마법사", 290, 0);
		entityManager.persist(character);

		BossMaster weeklyBoss = boss("스우", ResetPeriod.WEEKLY, 1);
		BossMaster secondWeeklyBoss = boss("데미안", ResetPeriod.WEEKLY, 2);
		BossMaster monthlyBoss = boss("검은 마법사", ResetPeriod.MONTHLY, 3);
		entityManager.persist(weeklyBoss);
		entityManager.persist(secondWeeklyBoss);
		entityManager.persist(monthlyBoss);
		entityManager.flush();

		entityManager.persist(SchedulerBossRecord.create(character, weeklyBoss,
				LocalDate.of(2026, 7, 10), ResetPeriod.WEEKLY, false, null));
		entityManager.persist(SchedulerBossRecord.create(character, weeklyBoss,
				LocalDate.of(2026, 7, 14), ResetPeriod.WEEKLY, true, null));
		entityManager.persist(SchedulerBossRecord.create(character, secondWeeklyBoss,
				LocalDate.of(2026, 7, 12), ResetPeriod.WEEKLY, true, null));
		entityManager.persist(SchedulerBossRecord.create(character, monthlyBoss,
				LocalDate.of(2026, 7, 2), ResetPeriod.MONTHLY, false, null));
		entityManager.persist(SchedulerBossRecord.create(character, monthlyBoss,
				LocalDate.of(2026, 7, 20), ResetPeriod.MONTHLY, true, null));
		entityManager.flush();
		entityManager.clear();

		List<SchedulerBossRecord> weekly = repository.findLatestByCharacterIdAndRecordDateBetweenAndResetPeriod(
				character.getId(), LocalDate.of(2026, 7, 9), LocalDate.of(2026, 7, 15), ResetPeriod.WEEKLY);
		List<SchedulerBossRecord> monthly = repository.findLatestByCharacterIdAndRecordDateBetweenAndResetPeriod(
				character.getId(), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), ResetPeriod.MONTHLY);

		assertThat(weekly).hasSize(2)
				.extracting(SchedulerBossRecord::getRecordDate)
				.containsExactly(LocalDate.of(2026, 7, 14), LocalDate.of(2026, 7, 12));
		assertThat(weekly.getFirst().isCompleted()).isTrue();
		assertThat(monthly).hasSize(1)
				.singleElement()
				.satisfies(record -> {
					assertThat(record.getRecordDate()).isEqualTo(LocalDate.of(2026, 7, 20));
					assertThat(record.isCompleted()).isTrue();
				});
	}

	private BossMaster boss(String name, ResetPeriod resetPeriod, int sortOrder) {
		BossMaster boss = BeanUtils.instantiateClass(BossMaster.class);
		ReflectionTestUtils.setField(boss, "bossName", name);
		ReflectionTestUtils.setField(boss, "difficulty", Difficulty.HARD);
		ReflectionTestUtils.setField(boss, "resetPeriod", resetPeriod);
		ReflectionTestUtils.setField(boss, "crystalPrice", 10_000_000L);
		ReflectionTestUtils.setField(boss, "sortOrder", sortOrder);
		ReflectionTestUtils.setField(boss, "active", true);
		return boss;
	}
}
