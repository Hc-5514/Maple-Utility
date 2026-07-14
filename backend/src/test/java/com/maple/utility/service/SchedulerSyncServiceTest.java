package com.maple.utility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.maple.utility.entity.BossMaster;
import com.maple.utility.entity.Difficulty;
import com.maple.utility.entity.MapleCharacter;
import com.maple.utility.entity.OAuthProvider;
import com.maple.utility.entity.ResetPeriod;
import com.maple.utility.entity.SchedulerBossRecord;
import com.maple.utility.entity.SchedulerDailyRecord;
import com.maple.utility.entity.SchedulerWeeklyRecord;
import com.maple.utility.entity.User;
import com.maple.utility.repository.BossMasterRepository;
import com.maple.utility.repository.SchedulerBossRecordRepository;
import com.maple.utility.repository.SchedulerDailyRecordRepository;
import com.maple.utility.repository.SchedulerWeeklyRecordRepository;
import com.maple.utility.security.NexonOpenApiClient;
import com.maple.utility.security.NexonSchedulerResponse;

@ExtendWith(MockitoExtension.class)
class SchedulerSyncServiceTest {

	private static final Clock CLOCK = Clock.fixed(
			Instant.parse("2026-07-14T12:00:00Z"),
			ZoneId.of("Asia/Seoul")
	);

	@Mock
	private NexonOpenApiClient nexonOpenApiClient;

	@Mock
	private BossMasterRepository bossMasterRepository;

	@Mock
	private SchedulerDailyRecordRepository dailyRecordRepository;

	@Mock
	private SchedulerWeeklyRecordRepository weeklyRecordRepository;

	@Mock
	private SchedulerBossRecordRepository bossRecordRepository;

	private SchedulerSyncService schedulerSyncService;

	@BeforeEach
	void setUp() {
		schedulerSyncService = new SchedulerSyncService(
				nexonOpenApiClient,
				bossMasterRepository,
				dailyRecordRepository,
				weeklyRecordRepository,
				bossRecordRepository,
				CLOCK
		);
	}

	@Test
	void syncCharactersCreatesSchedulerRecords() {
		User user = user();
		MapleCharacter character = character(user);
		BossMaster boss = boss();
		NexonSchedulerResponse response = new NexonSchedulerResponse(
				List.of(new NexonSchedulerResponse.Daily(LocalDate.parse("2026-07-14"), "일일 퀘스트", 1, 3)),
				List.of(new NexonSchedulerResponse.Weekly(LocalDate.parse("2026-07-13"), "길드 주간 미션", true, 1000)),
				List.of(new NexonSchedulerResponse.Boss(LocalDate.parse("2026-07-14"), "스우", Difficulty.HARD, ResetPeriod.WEEKLY, true))
		);

		when(nexonOpenApiClient.getCharacterScheduler(1L, "ocid")).thenReturn(response);
		when(dailyRecordRepository.findByCharacterIdAndRecordDateAndContentName(10L, LocalDate.parse("2026-07-14"), "일일 퀘스트"))
				.thenReturn(Optional.empty());
		when(weeklyRecordRepository.findByCharacterIdAndWeekStartDateAndContentName(10L, LocalDate.parse("2026-07-13"), "길드 주간 미션"))
				.thenReturn(Optional.empty());
		when(bossMasterRepository.findByBossNameAndDifficulty("스우", Difficulty.HARD)).thenReturn(Optional.of(boss));
		when(bossRecordRepository.findByCharacterIdAndBossIdAndRecordDate(10L, 20L, LocalDate.parse("2026-07-14")))
				.thenReturn(Optional.empty());
		when(dailyRecordRepository.save(any(SchedulerDailyRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(weeklyRecordRepository.save(any(SchedulerWeeklyRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(bossRecordRepository.save(any(SchedulerBossRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

		schedulerSyncService.syncCharacters(1L, List.of(character));

		ArgumentCaptor<SchedulerDailyRecord> dailyCaptor = ArgumentCaptor.forClass(SchedulerDailyRecord.class);
		ArgumentCaptor<SchedulerWeeklyRecord> weeklyCaptor = ArgumentCaptor.forClass(SchedulerWeeklyRecord.class);
		ArgumentCaptor<SchedulerBossRecord> bossCaptor = ArgumentCaptor.forClass(SchedulerBossRecord.class);
		verify(dailyRecordRepository).save(dailyCaptor.capture());
		verify(weeklyRecordRepository).save(weeklyCaptor.capture());
		verify(bossRecordRepository).save(bossCaptor.capture());
		assertThat(dailyCaptor.getValue().getCompletedCount()).isEqualTo(1);
		assertThat(weeklyCaptor.getValue().isCompleted()).isTrue();
		assertThat(bossCaptor.getValue().getResetPeriod()).isEqualTo(ResetPeriod.WEEKLY);
	}

	@Test
	void syncCharactersForBatchUsesBatchSchedulerApi() {
		User user = user();
		MapleCharacter character = character(user);
		NexonSchedulerResponse response = new NexonSchedulerResponse(List.of(), List.of(), List.of());
		when(nexonOpenApiClient.getCharacterSchedulerForBatch(1L, "ocid")).thenReturn(response);

		schedulerSyncService.syncCharactersForBatch(1L, List.of(character));

		verify(nexonOpenApiClient).getCharacterSchedulerForBatch(1L, "ocid");
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

	private BossMaster boss() {
		BossMaster boss = newBossMaster();
		ReflectionTestUtils.setField(boss, "id", 20L);
		ReflectionTestUtils.setField(boss, "bossName", "스우");
		ReflectionTestUtils.setField(boss, "difficulty", Difficulty.HARD);
		ReflectionTestUtils.setField(boss, "resetPeriod", ResetPeriod.WEEKLY);
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
