package com.maple.utility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.maple.utility.dto.response.SchedulerSummaryResponse;
import com.maple.utility.entity.BossMaster;
import com.maple.utility.entity.Difficulty;
import com.maple.utility.entity.MapleCharacter;
import com.maple.utility.entity.OAuthProvider;
import com.maple.utility.entity.ResetPeriod;
import com.maple.utility.entity.SchedulerBossRecord;
import com.maple.utility.entity.SchedulerDailyRecord;
import com.maple.utility.entity.SchedulerWeeklyRecord;
import com.maple.utility.entity.User;
import com.maple.utility.exception.ApiException;
import com.maple.utility.repository.CharacterRepository;
import com.maple.utility.repository.SchedulerBossRecordRepository;
import com.maple.utility.repository.SchedulerDailyRecordRepository;
import com.maple.utility.repository.SchedulerWeeklyRecordRepository;

@ExtendWith(MockitoExtension.class)
class SchedulerServiceTest {

	private static final Clock CLOCK = Clock.fixed(
			Instant.parse("2026-07-14T12:00:00Z"),
			ZoneId.of("Asia/Seoul")
	);

	@Mock
	private CharacterRepository characterRepository;

	@Mock
	private SchedulerDailyRecordRepository dailyRecordRepository;

	@Mock
	private SchedulerWeeklyRecordRepository weeklyRecordRepository;

	@Mock
	private SchedulerBossRecordRepository bossRecordRepository;

	@Mock
	private SchedulerSyncService schedulerSyncService;

	private SchedulerService schedulerService;

	@BeforeEach
	void setUp() {
		schedulerService = new SchedulerService(
				characterRepository,
				dailyRecordRepository,
				weeklyRecordRepository,
				bossRecordRepository,
				schedulerSyncService,
				CLOCK
		);
	}

	@Test
	void getSummaryReturnsFavoriteCharacterRecords() {
		User user = user();
		MapleCharacter character = character(user);
		BossMaster weeklyBoss = boss(20L, ResetPeriod.WEEKLY);
		BossMaster monthlyBoss = boss(21L, ResetPeriod.MONTHLY);
		SchedulerDailyRecord daily = SchedulerDailyRecord.create(character, LocalDate.parse("2026-07-14"), "일일 퀘스트", 1, 3, null);
		SchedulerWeeklyRecord weekly = SchedulerWeeklyRecord.create(character, LocalDate.parse("2026-07-13"), "길드 주간 미션", true, 1000, null);
		SchedulerBossRecord weeklyBossRecord = SchedulerBossRecord.create(character, weeklyBoss, LocalDate.parse("2026-07-14"), ResetPeriod.WEEKLY, true, null);
		SchedulerBossRecord monthlyBossRecord = SchedulerBossRecord.create(character, monthlyBoss, LocalDate.parse("2026-07-14"), ResetPeriod.MONTHLY, false, null);

		when(characterRepository.findByUserIdAndFavoriteTrueOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(character));
		when(dailyRecordRepository.findByCharacterIdInAndRecordDateOrderByCharacterIdAscIdAsc(List.of(10L), LocalDate.parse("2026-07-14")))
				.thenReturn(List.of(daily));
		when(weeklyRecordRepository.findByCharacterIdInAndWeekStartDateOrderByCharacterIdAscIdAsc(List.of(10L), LocalDate.parse("2026-07-13")))
				.thenReturn(List.of(weekly));
		when(bossRecordRepository.findByCharacterIdInAndRecordDateAndResetPeriodOrderByCharacterIdAscBoss_SortOrderAscIdAsc(List.of(10L), LocalDate.parse("2026-07-14"), ResetPeriod.WEEKLY))
				.thenReturn(List.of(weeklyBossRecord));
		when(bossRecordRepository.findByCharacterIdInAndRecordDateAndResetPeriodOrderByCharacterIdAscBoss_SortOrderAscIdAsc(List.of(10L), LocalDate.parse("2026-07-14"), ResetPeriod.MONTHLY))
				.thenReturn(List.of(monthlyBossRecord));

		SchedulerSummaryResponse response = schedulerService.getSummary(1L, LocalDate.parse("2026-07-14"));

		assertThat(response.daily()).hasSize(1);
		assertThat(response.weekly()).hasSize(1);
		assertThat(response.weeklyBoss()).hasSize(1);
		assertThat(response.monthlyBoss()).hasSize(1);
	}

	@Test
	void getGuildReturnsWeeklyGuildContents() {
		User user = user();
		MapleCharacter character = character(user);
		SchedulerWeeklyRecord guild = SchedulerWeeklyRecord.create(character, LocalDate.parse("2026-07-13"), "길드 주간 미션", true, 1000, null);
		SchedulerWeeklyRecord other = SchedulerWeeklyRecord.create(character, LocalDate.parse("2026-07-13"), "무릉도장", false, null, null);

		when(characterRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(character));
		when(weeklyRecordRepository.findByCharacterIdAndWeekStartDateOrderByIdAsc(10L, LocalDate.parse("2026-07-13")))
				.thenReturn(List.of(guild, other));

		assertThat(schedulerService.getGuild(1L, 10L, LocalDate.parse("2026-07-14")))
				.extracting(response -> response.contentName())
				.containsExactly("길드 주간 미션");
	}

	@Test
	void getBossSeparatesWeeklyAndMonthlyBoss() {
		User user = user();
		MapleCharacter character = character(user);
		SchedulerBossRecord weeklyBossRecord = SchedulerBossRecord.create(character, boss(20L, ResetPeriod.WEEKLY), LocalDate.parse("2026-07-14"), ResetPeriod.WEEKLY, true, null);
		SchedulerBossRecord monthlyBossRecord = SchedulerBossRecord.create(character, boss(21L, ResetPeriod.MONTHLY), LocalDate.parse("2026-07-14"), ResetPeriod.MONTHLY, false, null);

		when(characterRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(character));
		when(bossRecordRepository.findByCharacterIdAndRecordDateAndResetPeriodOrderByBoss_SortOrderAscIdAsc(10L, LocalDate.parse("2026-07-14"), ResetPeriod.WEEKLY))
				.thenReturn(List.of(weeklyBossRecord));
		when(bossRecordRepository.findByCharacterIdAndRecordDateAndResetPeriodOrderByBoss_SortOrderAscIdAsc(10L, LocalDate.parse("2026-07-14"), ResetPeriod.MONTHLY))
				.thenReturn(List.of(monthlyBossRecord));

		assertThat(schedulerService.getBoss(1L, 10L, LocalDate.parse("2026-07-14")).weeklyBoss()).hasSize(1);
		assertThat(schedulerService.getBoss(1L, 10L, LocalDate.parse("2026-07-14")).monthlyBoss()).hasSize(1);
	}

	@Test
	void getDailyRejectsOtherUserCharacter() {
		when(characterRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> schedulerService.getDaily(1L, 10L, LocalDate.parse("2026-07-14")))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("캐릭터 없음");
	}

	@Test
	void syncEvictsAndRunsSchedulerSync() {
		User user = user();
		MapleCharacter character = character(user);
		when(characterRepository.findByUserIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(character));
		when(characterRepository.findByUserIdAndFavoriteTrueOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of());

		schedulerService.sync(1L);

		verify(schedulerSyncService).syncCharacters(1L, List.of(character));
	}

	private User user() {
		User user = User.create(OAuthProvider.KAKAO, "oauth-id", "user@example.com", "nickname");
		ReflectionTestUtils.setField(user, "id", 1L);
		return user;
	}

	private MapleCharacter character(User user) {
		MapleCharacter character = MapleCharacter.create(user, "ocid", "캐릭터", "스카니아", "히어로", 280, 1);
		ReflectionTestUtils.setField(character, "id", 10L);
		return character;
	}

	private BossMaster boss(Long id, ResetPeriod resetPeriod) {
		BossMaster boss = newBossMaster();
		ReflectionTestUtils.setField(boss, "id", id);
		ReflectionTestUtils.setField(boss, "bossName", "스우");
		ReflectionTestUtils.setField(boss, "difficulty", Difficulty.HARD);
		ReflectionTestUtils.setField(boss, "resetPeriod", resetPeriod);
		ReflectionTestUtils.setField(boss, "sortOrder", 1);
		ReflectionTestUtils.setField(boss, "active", true);
		return boss;
	}

	private BossMaster newBossMaster() {
		try {
			Constructor<BossMaster> constructor = BossMaster.class.getDeclaredConstructor();
			constructor.setAccessible(true);
			return constructor.newInstance();
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException(exception);
		}
	}
}
