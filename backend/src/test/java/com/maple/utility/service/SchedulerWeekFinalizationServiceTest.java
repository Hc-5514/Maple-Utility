package com.maple.utility.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import com.maple.utility.config.RedisCacheNames;
import com.maple.utility.entity.MapleCharacter;
import com.maple.utility.entity.OAuthProvider;
import com.maple.utility.entity.User;
import com.maple.utility.exception.ApiException;
import com.maple.utility.repository.CharacterRepository;
import com.maple.utility.repository.SchedulerWeekFinalizationRepository;
import com.maple.utility.repository.SchedulerWeekFinalizationRepository.Target;
import com.maple.utility.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class SchedulerWeekFinalizationServiceTest {
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-15T17:00:00Z"), ZoneId.of("Asia/Seoul"));
	private static final LocalDate TODAY = LocalDate.parse("2026-07-16");
	private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
	private static final Target TARGET = new Target(100L, 1L, 10L, LocalDate.parse("2026-07-09"));
	@Mock SchedulerWeekFinalizationRepository records;
	@Mock UserRepository users;
	@Mock CharacterRepository characters;
	@Mock SchedulerSyncService sync;
	@Mock SchedulerSyncLock lock;
	@Mock CacheManager caches;
	@Mock Cache cache;
	@Mock PlatformTransactionManager manager;
	@Mock TransactionStatus status;
	private SchedulerWeekFinalizationService service;
	private MapleCharacter character;

	@BeforeEach
	void setUp() {
		User user = User.create(OAuthProvider.KAKAO, "oauth", null, "nickname");
		ReflectionTestUtils.setField(user, "id", 1L);
		character = MapleCharacter.create(user, "ocid", "캐릭터", "스카니아", "히어로", 280, 1);
		ReflectionTestUtils.setField(character, "id", 10L);
		when(users.findAll()).thenReturn(List.of(user));
		lenient().when(manager.getTransaction(any())).thenReturn(status);
		lenient().when(lock.tryAcquire(1L)).thenReturn(true);
		lenient().when(characters.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(character));
		lenient().when(records.lockDue(100L, TODAY)).thenReturn(Optional.of(TARGET));
		service = new SchedulerWeekFinalizationService(records, users, characters, sync, lock, caches, manager, CLOCK);
	}

	@Test
	void successfulWednesdaySnapshotCommitsFinalStateAndEvictsCaches() {
		when(records.due(1L, TODAY)).thenReturn(List.of(TARGET));
		when(sync.syncHistoricalCharacter(1L, character, LocalDate.parse("2026-07-15"))).thenReturn(true);
		when(caches.getCache(RedisCacheNames.SCHEDULER)).thenReturn(cache);
		service.finalizeWeeks();
		verify(records).finish(100L, true, NOW);
		verify(manager).commit(status);
		verify(cache).clear();
		verify(records).seed(1L, LocalDate.parse("2026-07-09"));
		verify(records).seed(1L, LocalDate.parse("2026-07-02"));
		verify(records).expire(TODAY, NOW);
	}

	@Test
	void alreadyFinalizedTargetIsNotFetchedAgain() {
		when(records.due(1L, TODAY)).thenReturn(List.of(TARGET));
		when(records.lockDue(100L, TODAY)).thenReturn(Optional.empty());
		service.finalizeWeeks();
		verifyNoInteractions(sync, caches);
		verify(records, never()).finish(anyLong(), anyBoolean(), any());
	}

	@Test
	void failedSnapshotRollsBackBeforeDurableRetryState() {
		when(records.due(1L, TODAY)).thenReturn(List.of(TARGET));
		when(sync.syncHistoricalCharacter(1L, character, TARGET.recordDate()))
				.thenThrow(new ApiException(HttpStatus.BAD_GATEWAY, "NEXON_SERVER_ERROR", "서버 오류"));
		service.finalizeWeeks();
		var order = inOrder(manager, records);
		order.verify(manager).rollback(status);
		order.verify(records).retry(100L, "NEXON_SERVER_ERROR", true, NOW);
		verify(records, never()).finish(anyLong(), anyBoolean(), any());
		verifyNoInteractions(caches);
	}

	@Test
	void exhaustedBudgetLeavesEveryRemainingCharacterDurableAndUnattempted() {
		Target second = new Target(101L, 1L, 10L, LocalDate.parse("2026-07-02"));
		when(records.due(1L, TODAY)).thenReturn(List.of(TARGET, second));
		when(records.lockDue(101L, TODAY)).thenReturn(Optional.of(second));
		when(sync.syncHistoricalCharacter(anyLong(), any(), any()))
				.thenThrow(new ApiException(HttpStatus.TOO_MANY_REQUESTS, "NEXON_BATCH_LIMIT_REACHED", "예산 소진"));
		service.finalizeWeeks();
		verify(records).retry(100L, "NEXON_BATCH_LIMIT_REACHED", false, NOW);
		verify(records).retry(101L, "NEXON_BATCH_LIMIT_REACHED", false, NOW);
		verify(manager, times(2)).rollback(status);
	}

	@Test
	void noDataRemainsDistinctAndRetryableWithoutCacheEviction() {
		when(records.due(1L, TODAY)).thenReturn(List.of(TARGET));
		service.finalizeWeeks();
		verify(records).finish(100L, false, NOW);
		verify(manager).commit(status);
		verifyNoInteractions(caches);
	}

	@Test
	void activeLiveSyncDefersWithoutExternalQuery() {
		when(records.due(1L, TODAY)).thenReturn(List.of(TARGET));
		when(lock.tryAcquire(1L)).thenReturn(false);
		service.finalizeWeeks();
		verify(records).retry(100L, "SCHEDULER_SYNC_IN_PROGRESS", false, NOW);
		verifyNoInteractions(sync, characters);
	}
}
