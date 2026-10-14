package com.maple.utility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import com.maple.utility.entity.*;
import com.maple.utility.exception.ApiException;
import com.maple.utility.repository.*;
import com.maple.utility.repository.SchedulerMonthlyFinalizationRepository.*;
import com.maple.utility.security.*;
import com.maple.utility.util.SchedulerPeriodPolicy;

@ExtendWith(MockitoExtension.class)
class SchedulerMonthlyFinalizationServiceTest {
	static final LocalDate END = LocalDate.of(2026,9,30);
	static final LocalDateTime NOW = LocalDateTime.of(2026,10,1,2,0);
	static final Clock CLOCK = Clock.fixed(NOW.atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));
	static final Target TARGET = new Target(1,10,100,END.withDayOfMonth(1));
	@Mock SchedulerMonthlyFinalizationRepository records;
	@Mock CharacterRepository characters;
	@Mock BossMasterRepository bosses;
	@Mock SchedulerSyncLock lock;
	@Mock PlatformTransactionManager manager;
	@Mock TransactionStatus status;
	@Mock NexonOpenApiClient nexon;
	SchedulerMonthlyFinalizationService service;
	MapleCharacter character;
	@BeforeEach void setup() {
		service = new SchedulerMonthlyFinalizationService(records,characters,bosses,lock,manager,CLOCK);
		character = MapleCharacter.create(User.create(OAuthProvider.KAKAO,"oauth",null,"name"),"ocid","name","world","job",280,0);
		character.toggleFavorite();
		lenient().when(manager.getTransaction(any())).thenReturn(status);
		lenient().when(lock.tryAcquire(10L)).thenReturn(true);
		lenient().when(records.lockDue(1, NOW.toLocalDate())).thenReturn(Optional.of(TARGET));
		lenient().when(characters.findByIdAndUserId(100L,10L)).thenReturn(Optional.of(character));
	}
	List<Boolean> run() {
		var results = new ArrayList<Boolean>();
		service.finalizePrepared(10L,List.of(TARGET),NexonRequestMode.REALTIME,results::add,new SchedulerHistoryFetch(nexon));
		return results;
	}
	@Test void boundariesCoverMonthLengthsAndYearEnd() {
		for (String date : List.of("2024-02-29","2026-02-28","2026-04-30","2026-12-31")) {
			LocalDate end=LocalDate.parse(date);
			assertThat(SchedulerPeriodPolicy.canFinalizeMonth(end,end.plusDays(1).atTime(1,59))).isFalse();
			assertThat(SchedulerPeriodPolicy.canFinalizeMonth(end,end.plusDays(1).atTime(2,0))).isTrue();
			assertThat(SchedulerPeriodPolicy.canFinalizeMonth(end,end.plusDays(2).atTime(1,0))).isTrue();
			assertThat(SchedulerPeriodPolicy.canFinalizeMonth(end,end.plusDays(13).atTime(2,0))).isTrue();
			assertThat(SchedulerPeriodPolicy.canFinalizeMonth(end,end.plusDays(14).atTime(2,0))).isFalse();
		}
		assertThat(SchedulerPeriodPolicy.canFinalizeMonth(END.minusDays(1),NOW)).isFalse();
	}
	@Test void firstDayBeforeTwoDoesNotSeedOrConsumeAttempt() {
		Clock early=Clock.fixed(NOW.minusMinutes(1).atZone(CLOCK.getZone()).toInstant(),CLOCK.getZone());
		var earlyService=new SchedulerMonthlyFinalizationService(records,characters,bosses,lock,manager,early);
		assertThat(earlyService.prepare(10L,true)).isEmpty();
		verify(records,never()).seed(any(),any()); verify(records,never()).due(any(),any());
	}
	@Test void nullIsRetryableButEmptySnapshotIsFinalized() {
		when(nexon.getCharacterSchedulerForDateStrict(10L,"ocid",END,NexonRequestMode.REALTIME)).thenReturn(null);
		assertThat(run()).containsExactly(true); verify(records).noData(1,NOW);
		when(nexon.getCharacterSchedulerForDateStrict(10L,"ocid",END,NexonRequestMode.REALTIME))
				.thenReturn(new NexonSchedulerResponse(END,List.of(),List.of(),List.of()));
		assertThat(run()).containsExactly(false); verify(records).finish(TARGET,List.of(),NOW);
	}
	@Test void falseFlagIsPreservedAndOnlyMonthlyRowsAreSaved() {
		BossMaster boss=mock(BossMaster.class); when(boss.getId()).thenReturn(20L); when(boss.getResetPeriod()).thenReturn(ResetPeriod.MONTHLY);
		when(bosses.findByBossNameAndDifficulty("검은 마법사",Difficulty.HARD)).thenReturn(Optional.of(boss));
		when(nexon.getCharacterSchedulerForDateStrict(10L,"ocid",END,NexonRequestMode.REALTIME)).thenReturn(new NexonSchedulerResponse(END,List.of(),List.of(),List.of(
				new NexonSchedulerResponse.Boss(END,"검은 마법사",Difficulty.HARD,ResetPeriod.MONTHLY,false),
				new NexonSchedulerResponse.Boss(END,"스우",Difficulty.HARD,ResetPeriod.WEEKLY,true))));
		assertThat(run()).containsExactly(false); verify(records).finish(TARGET,List.of(new BossSnapshot(20,false)),NOW); verify(manager).commit(status);
	}
	@Test void unknownBossRollsBackBeforeDurableRetry() {
		when(nexon.getCharacterSchedulerForDateStrict(10L,"ocid",END,NexonRequestMode.REALTIME)).thenReturn(new NexonSchedulerResponse(END,List.of(),List.of(),List.of(
				new NexonSchedulerResponse.Boss(END,"unknown",Difficulty.HARD,ResetPeriod.MONTHLY,true))));
		assertThat(run()).containsExactly(true);
		var order=inOrder(manager,records); order.verify(manager).rollback(status); order.verify(records).retry(1,"NEXON_MONTH_BOSS_INVALID",true,NOW);
		verify(records,never()).finish(any(),any(),any());
	}
	@Test void budgetFailureIsSharedWithoutRepeatedNetworkCalls() {
		var fetch=new SchedulerHistoryFetch(nexon);
		when(nexon.getCharacterSchedulerForDateStrict(10L,"ocid",END,NexonRequestMode.REALTIME))
				.thenThrow(new ApiException(HttpStatus.TOO_MANY_REQUESTS,"NEXON_REALTIME_LIMIT_REACHED","budget"));
		var skipped=new ArrayList<Boolean>();
		service.finalizePrepared(10L,List.of(TARGET),NexonRequestMode.REALTIME,skipped::add,fetch);
		try { fetch.get(10L,"ocid",END,NexonRequestMode.REALTIME); } catch(ApiException ignored) { }
		verify(nexon,times(1)).getCharacterSchedulerForDateStrict(10L,"ocid",END,NexonRequestMode.REALTIME);
		verify(records).retry(1,"NEXON_REALTIME_LIMIT_REACHED",false,NOW); assertThat(skipped).containsExactly(true);
	}
	@Test void finalizedByOtherWorkerAndUnfavoritedCharacterNeverFetch() {
		when(records.lockDue(1,NOW.toLocalDate())).thenReturn(Optional.empty()); assertThat(run()).containsExactly(true);
		when(records.lockDue(1,NOW.toLocalDate())).thenReturn(Optional.of(TARGET)); character.toggleFavorite(); assertThat(run()).containsExactly(true);
		verifyNoInteractions(nexon);
	}
	@Test void userLockConflictIsSkippedAndDoesNotConsumeAttempt() {
		when(lock.tryAcquire(10L)).thenReturn(false); assertThat(run()).containsExactly(true);
		verify(records).retry(1,"SCHEDULER_SYNC_IN_PROGRESS",false,NOW); verifyNoInteractions(nexon);
	}
	@Test void mismatchedDateCannotSaveSnapshot() {
		when(nexon.getCharacterSchedulerForDateStrict(10L,"ocid",END,NexonRequestMode.REALTIME))
				.thenReturn(new NexonSchedulerResponse(END.minusDays(1),List.of(),List.of(),List.of()));
		assertThat(run()).containsExactly(true); verify(records).retry(1,"NEXON_HISTORY_DATE_MISMATCH",true,NOW);
	}
	@Test void rolloverDuringRequestExpiresInsteadOfSaving() {
		var current=new java.util.concurrent.atomic.AtomicReference<>(LocalDateTime.of(2026,10,13,23,59).atZone(CLOCK.getZone()).toInstant());
		Clock moving=new Clock() {
			public ZoneId getZone() { return CLOCK.getZone(); }
			public Clock withZone(ZoneId zone) { return this; }
			public Instant instant() { return current.get(); }
		};
		var movingService=new SchedulerMonthlyFinalizationService(records,characters,bosses,lock,manager,moving);
		when(records.lockDue(1,LocalDate.of(2026,10,13))).thenReturn(Optional.of(TARGET));
		when(nexon.getCharacterSchedulerForDateStrict(10L,"ocid",END,NexonRequestMode.REALTIME)).thenAnswer(call -> {
			current.set(LocalDateTime.of(2026,10,14,0,0).atZone(CLOCK.getZone()).toInstant());
			return new NexonSchedulerResponse(END,List.of(),List.of(),List.of());
		});
		var progress=new ArrayList<Boolean>();
		movingService.finalizePrepared(10L,List.of(TARGET),NexonRequestMode.REALTIME,progress::add,new SchedulerHistoryFetch(nexon));
		assertThat(progress).containsExactly(true); verify(records,never()).finish(any(),any(),any());
		verify(records).expire(LocalDate.of(2026,10,14),LocalDateTime.of(2026,10,14,0,0));
	}
	@Test void duplicateMonthlyBossIsNotFinalized() {
		BossMaster boss=mock(BossMaster.class); when(boss.getId()).thenReturn(20L); when(boss.getResetPeriod()).thenReturn(ResetPeriod.MONTHLY);
		when(bosses.findByBossNameAndDifficulty("검은 마법사",Difficulty.HARD)).thenReturn(Optional.of(boss));
		var row=new NexonSchedulerResponse.Boss(END,"검은 마법사",Difficulty.HARD,ResetPeriod.MONTHLY,true);
		when(nexon.getCharacterSchedulerForDateStrict(10L,"ocid",END,NexonRequestMode.REALTIME)).thenReturn(new NexonSchedulerResponse(END,List.of(),List.of(),List.of(row,row)));
		assertThat(run()).containsExactly(true); verify(records).retry(1,"NEXON_MONTH_BOSS_INVALID",true,NOW); verify(records,never()).finish(any(),any(),any());
	}
	@Test void failedSnapshotRollsBackAndRetriesWithoutLosingProgress() {
		when(nexon.getCharacterSchedulerForDateStrict(10L,"ocid",END,NexonRequestMode.REALTIME)).thenReturn(new NexonSchedulerResponse(END,List.of(),List.of(),List.of()));
		doThrow(new IllegalStateException("storage")).when(records).finish(TARGET,List.of(),NOW);
		assertThat(run()).containsExactly(true);
		var order=inOrder(manager,records); order.verify(manager).rollback(status); order.verify(records).retry(1,"FINALIZATION_FAILED",true,NOW);
	}
	@Test void retryStorageFailureStillAdvancesEveryFrozenTarget() {
		when(nexon.getCharacterSchedulerForDateStrict(10L,"ocid",END,NexonRequestMode.REALTIME)).thenThrow(new IllegalStateException("network"));
		doThrow(new IllegalStateException("storage")).when(records).retry(1,"FINALIZATION_FAILED",true,NOW);
		var progress=new ArrayList<Boolean>();
		service.finalizePrepared(10L,List.of(TARGET,TARGET),NexonRequestMode.REALTIME,progress::add,new SchedulerHistoryFetch(nexon));
		assertThat(progress).containsExactly(true,true);
	}

}
