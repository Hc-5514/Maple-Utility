package com.maple.utility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

import com.maple.utility.dto.response.SchedulerCharacterSummaryResponse;
import com.maple.utility.dto.response.SchedulerSummaryResponse;
import com.maple.utility.dto.response.SyncJobResponse;
import com.maple.utility.dto.request.ManualBossRecordSaveRequest;
import com.maple.utility.entity.BossMaster;
import com.maple.utility.entity.BossPeriodSelection;
import com.maple.utility.entity.BossItemAcquisition;
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
import com.maple.utility.repository.BossMasterRepository;
import com.maple.utility.repository.BossPeriodEntryRepository;
import com.maple.utility.repository.BossPeriodSelectionRepository;
import com.maple.utility.repository.BossItemAcquisitionRepository;
import com.maple.utility.repository.SchedulerBossRecordRepository;
import com.maple.utility.repository.SchedulerDailyRecordRepository;
import com.maple.utility.repository.SchedulerWeeklyRecordRepository;
import com.maple.utility.repository.SyncJobRepository;

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
	private BossMasterRepository bossMasterRepository;
	@Mock
	private BossPeriodSelectionRepository bossPeriodSelectionRepository;
	@Mock
	private BossPeriodEntryRepository bossPeriodEntryRepository;
	@Mock
	private BossItemAcquisitionRepository bossItemAcquisitionRepository;

	@Mock
	private SchedulerSyncService schedulerSyncService;

	@Mock
	private SyncJobRepository syncJobRepository;

	private SchedulerService schedulerService;

	@BeforeEach
	void setUp() {
		schedulerService = new SchedulerService(
				characterRepository,
				dailyRecordRepository,
			weeklyRecordRepository,
			bossRecordRepository,
			bossMasterRepository,
			bossPeriodSelectionRepository,
			bossPeriodEntryRepository,
			bossItemAcquisitionRepository,
				schedulerSyncService,
				syncJobRepository,
				CLOCK
		);
	}

	@Test
	void getSummaryUsesCompletedJobTimeWhenNoRecordsExist() {
		LocalDateTime completedAt = LocalDateTime.of(2026, 7, 14, 21, 0);
		when(characterRepository.findByUserIdAndFavoriteTrueOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of());
		when(syncJobRepository.latestCompleted(1L, "SCHEDULER")).thenReturn(Optional.of(
				new SyncJobResponse(1L, "SCHEDULER", "COMPLETED", 0, 0, 0, null,
						completedAt.minusSeconds(1), completedAt)));

		SchedulerSummaryResponse response = schedulerService.getSummary(1L, LocalDate.of(2026, 7, 14));

		assertThat(response.syncedAt()).isEqualTo(completedAt);
	}

	@Test
	void getSummaryReturnsFavoriteCharacterRecords() {
		User user = user();
		MapleCharacter character = character(user);
		BossMaster weeklyBoss = boss(20L, ResetPeriod.WEEKLY);
		BossMaster monthlyBoss = boss(21L, ResetPeriod.MONTHLY);
		SchedulerDailyRecord daily = SchedulerDailyRecord.create(character, LocalDate.parse("2026-07-14"), "일일 퀘스트", 1, 3, null);
		SchedulerWeeklyRecord weekly = SchedulerWeeklyRecord.create(character, LocalDate.parse("2026-07-09"), "길드 주간 미션", true, 1000, null);
		SchedulerBossRecord weeklyBossRecord = SchedulerBossRecord.create(character, weeklyBoss, LocalDate.parse("2026-07-14"), ResetPeriod.WEEKLY, true, null);
		SchedulerBossRecord monthlyBossRecord = SchedulerBossRecord.create(character, monthlyBoss, LocalDate.parse("2026-07-14"), ResetPeriod.MONTHLY, false, null);

		when(characterRepository.findByUserIdAndFavoriteTrueOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(character));
		when(dailyRecordRepository.findByCharacterIdInAndRecordDateOrderByCharacterIdAscIdAsc(List.of(10L), LocalDate.parse("2026-07-14")))
				.thenReturn(List.of(daily));
		when(weeklyRecordRepository.findByCharacterIdInAndWeekStartDateOrderByCharacterIdAscIdAsc(List.of(10L), LocalDate.parse("2026-07-09")))
				.thenReturn(List.of(weekly));
		when(bossRecordRepository.findByCharacterIdInAndRecordDateAndResetPeriodOrderByCharacterIdAscBoss_SortOrderAscIdAsc(List.of(10L), LocalDate.parse("2026-07-14"), ResetPeriod.WEEKLY))
				.thenReturn(List.of(weeklyBossRecord));
		when(bossRecordRepository.findByCharacterIdInAndRecordDateAndResetPeriodOrderByCharacterIdAscBoss_SortOrderAscIdAsc(List.of(10L), LocalDate.parse("2026-07-14"), ResetPeriod.MONTHLY))
				.thenReturn(List.of(monthlyBossRecord));
		when(bossRecordRepository.findLatestByCharacterIdAndRecordDateBetweenAndResetPeriod(10L, LocalDate.parse("2026-07-09"), LocalDate.parse("2026-07-15"), ResetPeriod.WEEKLY))
				.thenReturn(List.of(weeklyBossRecord));
		when(bossRecordRepository.findLatestByCharacterIdAndRecordDateBetweenAndResetPeriod(10L, LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-31"), ResetPeriod.MONTHLY))
				.thenReturn(List.of(monthlyBossRecord));
		when(bossPeriodSelectionRepository.findByCharacter_IdAndPeriodStartAndResetPeriod(10L, LocalDate.parse("2026-07-09"), ResetPeriod.WEEKLY))
				.thenReturn(List.of());
		when(bossPeriodSelectionRepository.findByCharacter_IdAndPeriodStartAndResetPeriod(10L, LocalDate.parse("2026-07-01"), ResetPeriod.MONTHLY))
				.thenReturn(List.of());

		SchedulerSummaryResponse response = schedulerService.getSummary(1L, LocalDate.parse("2026-07-14"));

		assertThat(response.characters()).hasSize(1);
		SchedulerCharacterSummaryResponse charSummary = response.characters().get(0);
		assertThat(charSummary.daily().total()).isEqualTo(1);
		assertThat(charSummary.weekly().total()).isEqualTo(1);
		assertThat(charSummary.weeklyBoss().total()).isEqualTo(1);
		assertThat(charSummary.monthlyBoss().total()).isEqualTo(1);
	}

	@Test
	void getGuildReturnsWeeklyGuildContents() {
		User user = user();
		MapleCharacter character = character(user);
		SchedulerWeeklyRecord guild = SchedulerWeeklyRecord.create(character, LocalDate.parse("2026-07-09"), "길드 주간 미션", true, 1000, null);
		SchedulerWeeklyRecord other = SchedulerWeeklyRecord.create(character, LocalDate.parse("2026-07-09"), "무릉도장", false, null, null);

		when(characterRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(character));
		when(weeklyRecordRepository.findByCharacterIdAndWeekStartDateOrderByIdAsc(10L, LocalDate.parse("2026-07-09")))
				.thenReturn(List.of(guild, other));

		assertThat(schedulerService.getGuild(1L, 10L, LocalDate.parse("2026-07-14")))
				.extracting(response -> response.contentName())
				.containsExactly("길드 주간 미션");
	}

	@Test
	void getBossSeparatesPeriodsAndReturnsLatestSnapshotPerBoss() {
		User user = user();
		MapleCharacter character = character(user);
		SchedulerBossRecord weeklyBossRecord = SchedulerBossRecord.create(character, boss(20L, ResetPeriod.WEEKLY), LocalDate.parse("2026-07-14"), ResetPeriod.WEEKLY, true, null);
		SchedulerBossRecord monthlyBossRecord = SchedulerBossRecord.create(character, boss(21L, ResetPeriod.MONTHLY), LocalDate.parse("2026-07-14"), ResetPeriod.MONTHLY, false, null);

		when(characterRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(character));
		when(bossRecordRepository.findLatestByCharacterIdAndRecordDateBetweenAndResetPeriod(10L, LocalDate.parse("2026-07-09"), LocalDate.parse("2026-07-15"), ResetPeriod.WEEKLY))
				.thenReturn(List.of(weeklyBossRecord));
		when(bossRecordRepository.findLatestByCharacterIdAndRecordDateBetweenAndResetPeriod(10L, LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-30"), ResetPeriod.MONTHLY))
				.thenReturn(List.of(monthlyBossRecord));
		when(bossPeriodSelectionRepository.findByCharacter_IdAndPeriodStartAndResetPeriod(10L, LocalDate.parse("2026-07-09"), ResetPeriod.WEEKLY))
				.thenReturn(List.of());
		when(bossPeriodSelectionRepository.findByCharacter_IdAndPeriodStartAndResetPeriod(10L, LocalDate.parse("2026-06-01"), ResetPeriod.MONTHLY))
				.thenReturn(List.of());

		var response = schedulerService.getBoss(1L, 10L,
				LocalDate.parse("2026-07-14"), LocalDate.parse("2026-06-15"), null);

		assertThat(response.weeklyBosses()).hasSize(1);
		assertThat(response.monthlyBosses()).hasSize(1);
		verify(bossRecordRepository).findLatestByCharacterIdAndRecordDateBetweenAndResetPeriod(
				10L, LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-30"), ResetPeriod.MONTHLY);
	}

	@Test
	void addBossesToExistingWeekReturnsManualCompletion() {
		MapleCharacter character = character(user());
		BossMaster boss = boss(20L, ResetPeriod.WEEKLY);
		LocalDate start = LocalDate.parse("2026-07-09");
		BossPeriodSelection added = BossPeriodSelection.create(character, boss, start, true);
		when(characterRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(character));
		when(bossMasterRepository.findByResetPeriodAndActiveTrueOrderBySortOrderAsc(ResetPeriod.WEEKLY)).thenReturn(List.of(boss));
		when(bossRecordRepository.findLatestByCharacterIdAndRecordDateBetweenAndResetPeriod(10L, start, start.plusDays(6), ResetPeriod.WEEKLY))
				.thenReturn(List.of());
		when(bossPeriodSelectionRepository.findByCharacter_IdAndPeriodStartAndResetPeriod(10L, start, ResetPeriod.WEEKLY))
				.thenReturn(List.of(), List.of(), List.of(added));
		when(bossPeriodSelectionRepository.save(any())).thenReturn(added);

		var records = schedulerService.addBossesToPeriod(1L, 10L,
				new ManualBossRecordSaveRequest(start, ResetPeriod.WEEKLY, List.of(20L)));

		assertThat(records).singleElement().satisfies(record -> {
			assertThat(record.bossId()).isEqualTo(20L);
			assertThat(record.isCompleted()).isTrue();
		});
	}

	@Test
	void hideBossDeletesOnlySelectedPeriodAcquisitions() {
		MapleCharacter character = character(user());
		BossMaster boss = boss(20L, ResetPeriod.WEEKLY);
		LocalDate start = LocalDate.parse("2026-07-09");
		BossPeriodSelection selection = BossPeriodSelection.create(character, boss, start, true);
		BossItemAcquisition acquisition = mock(BossItemAcquisition.class);
		when(characterRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(character));
		when(bossMasterRepository.findById(20L)).thenReturn(Optional.of(boss));
		when(bossPeriodSelectionRepository.findByCharacter_IdAndBoss_IdAndPeriodStart(10L, 20L, start))
				.thenReturn(Optional.of(selection));
		when(bossItemAcquisitionRepository.findByCharacter_IdAndBossDropItem_Boss_IdAndAcquiredDateBetween(
				10L, 20L, start, start.plusDays(6))).thenReturn(List.of(acquisition));

		schedulerService.hideBossFromPeriod(1L, 10L, 20L, LocalDate.parse("2026-07-14"), ResetPeriod.WEEKLY);

		assertThat(selection.isVisible()).isFalse();
		verify(bossItemAcquisitionRepository).deleteAll(List.of(acquisition));
		verify(bossPeriodEntryRepository).deleteByCharacter_IdAndBoss_IdAndPeriodStart(10L, 20L, start);
	}

	@Test
	void hiddenBossRemainsAbsentWhenSyncedSnapshotExists() {
		MapleCharacter character = character(user());
		BossMaster boss = boss(20L, ResetPeriod.WEEKLY);
		LocalDate start = LocalDate.parse("2026-07-09");
		BossPeriodSelection hidden = BossPeriodSelection.create(character, boss, start, false);
		SchedulerBossRecord snapshot = SchedulerBossRecord.create(character, boss, start.plusDays(5), ResetPeriod.WEEKLY, true, null);
		when(characterRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(character));
		when(bossRecordRepository.findLatestByCharacterIdAndRecordDateBetweenAndResetPeriod(10L, start, start.plusDays(6), ResetPeriod.WEEKLY))
				.thenReturn(List.of(snapshot));
		when(bossRecordRepository.findLatestByCharacterIdAndRecordDateBetweenAndResetPeriod(10L, LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-31"), ResetPeriod.MONTHLY))
				.thenReturn(List.of());
		when(bossPeriodSelectionRepository.findByCharacter_IdAndPeriodStartAndResetPeriod(10L, start, ResetPeriod.WEEKLY))
				.thenReturn(List.of(hidden));
		when(bossPeriodSelectionRepository.findByCharacter_IdAndPeriodStartAndResetPeriod(10L, LocalDate.parse("2026-07-01"), ResetPeriod.MONTHLY))
				.thenReturn(List.of());

		assertThat(schedulerService.getBoss(1L, 10L, start, start, null).weeklyBosses()).isEmpty();
	}

	@Test
	void saveManualWeeklyBossesStoresSelectedActiveCandidatesWithinLimit() {
		User user = user();
		MapleCharacter character = character(user);
		BossMaster boss = boss(20L, ResetPeriod.WEEKLY);
		when(characterRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(character));
		when(bossRecordRepository.findLatestByCharacterIdAndRecordDateBetweenAndResetPeriod(
				10L, LocalDate.parse("2026-07-09"), LocalDate.parse("2026-07-15"), ResetPeriod.WEEKLY)).thenReturn(List.of());
		when(bossMasterRepository.findByResetPeriodAndActiveTrueOrderBySortOrderAsc(ResetPeriod.WEEKLY)).thenReturn(List.of(boss));
		when(bossRecordRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

		var response = schedulerService.saveManualBossRecords(1L, 10L,
				new ManualBossRecordSaveRequest(LocalDate.parse("2026-07-14"), ResetPeriod.WEEKLY, List.of(20L)));

		assertThat(response).singleElement().satisfies(record -> {
			assertThat(record.bossId()).isEqualTo(20L);
			assertThat(record.recordDate()).isEqualTo(LocalDate.parse("2026-07-09"));
			assertThat(record.isCompleted()).isTrue();
		});
	}

	@Test
	void saveManualWeeklyBossesRejectsMoreThanTwelveSelections() {
		User user = user();
		MapleCharacter character = character(user);
		when(characterRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(character));

		assertThatThrownBy(() -> schedulerService.saveManualBossRecords(1L, 10L,
				new ManualBossRecordSaveRequest(LocalDate.parse("2026-07-14"), ResetPeriod.WEEKLY,
						List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L))))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("선택 한도");
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
		character.toggleFavorite();
		return character;
	}

	private BossMaster boss(Long id, ResetPeriod resetPeriod) {
		BossMaster boss = newBossMaster();
		ReflectionTestUtils.setField(boss, "id", id);
		ReflectionTestUtils.setField(boss, "bossName", "스우");
		ReflectionTestUtils.setField(boss, "difficulty", Difficulty.HARD);
		ReflectionTestUtils.setField(boss, "resetPeriod", resetPeriod);
		ReflectionTestUtils.setField(boss, "crystalPrice", 25000000L);
		ReflectionTestUtils.setField(boss, "bossImage", "/assets/boss/suu.png");
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
