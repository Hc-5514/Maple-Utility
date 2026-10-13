package com.maple.utility.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.maple.utility.config.RedisCacheNames;
import com.maple.utility.dto.response.SyncJobResponse;
import com.maple.utility.entity.MapleCharacter;
import com.maple.utility.entity.User;
import com.maple.utility.exception.ApiException;
import com.maple.utility.exception.NexonApiException;
import com.maple.utility.repository.CharacterRepository;
import com.maple.utility.repository.SyncJobRepository;
import com.maple.utility.repository.UserRepository;
import com.maple.utility.security.NexonCharacterSummary;
import com.maple.utility.security.NexonOpenApiClient;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SyncJobService {
	private final SyncJobRepository jobs;
	private final UserRepository users;
	private final CharacterRepository characters;
	private final CharacterSyncService characterSync;
	private final SchedulerSyncService schedulerSync;
	private final SchedulerWeekFinalizationService weekFinalization;
	private final NexonOpenApiClient nexon;
	private final CacheManager cacheManager;
	private final JdbcTemplate jdbcTemplate;
	private final Executor executor;
	private final Clock clock;
    private final com.maple.utility.repository.UserApiKeyRepository keys;

	public SyncJobService(SyncJobRepository jobs, UserRepository users, CharacterRepository characters,
			CharacterSyncService characterSync, SchedulerSyncService schedulerSync,
			SchedulerWeekFinalizationService weekFinalization, NexonOpenApiClient nexon,
			CacheManager cacheManager, JdbcTemplate jdbcTemplate, @Qualifier("syncExecutor") Executor executor, Clock clock, com.maple.utility.repository.UserApiKeyRepository keys) {
		this.jobs = jobs;
		this.users = users;
		this.characters = characters;
		this.characterSync = characterSync;
		this.schedulerSync = schedulerSync;
		this.weekFinalization = weekFinalization;
		this.nexon = nexon;
		this.cacheManager = cacheManager;
		this.jdbcTemplate = jdbcTemplate;
		this.executor = executor;
		this.clock = clock;
        this.keys = keys;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void failInterruptedJobs() {
		jobs.failInterrupted(LocalDateTime.now(clock));
        jdbcTemplate.update("UPDATE data_sync_logs SET status='FAILED', error_message='서버 재시작으로 동기화 중단', completed_at=? WHERE status='STARTED'", LocalDateTime.now(clock));
	}

	public SyncJobResponse startCharacters(Long userId) {
		Long credentialId = selectedCredential(userId);
        var result = jobs.start(userId, "CHARACTER");
		if (result.created()) {
			submitPinned(result.job().id(), credentialId, () -> runCharacters(userId, result.job().id()));
		}
		return result.job();
	}

	public SyncJobResponse startScheduler(Long userId, boolean force, boolean batch) {
		var active = jobs.active(userId, "SCHEDULER");
		if (active.isPresent()) {
			return active.get();
		}
		if (!force && !batch) {
			var recent = jobs.latestCompleted(userId, "SCHEDULER");
			if (recent.isPresent() && recent.get().completedAt() != null
					&& Duration.between(recent.get().completedAt(), LocalDateTime.now(clock)).compareTo(Duration.ofMinutes(5)) < 0
					&& !favoritesChanged(userId, recent.get())) {
				return recent.get();
			}
		}
		Long credentialId = selectedCredential(userId);
        var result = jobs.start(userId, "SCHEDULER");
		if (result.created()) {
			submitPinned(result.job().id(), credentialId, () -> runScheduler(userId, result.job().id(), batch, force));
		}
		return result.job();
	}

	public SyncJobResponse get(Long userId, Long jobId) {
		return jobs.get(userId, jobId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SYNC_JOB_NOT_FOUND", "동기화 작업 없음"));
	}

    private Long selectedCredential(Long ownerId) {
        Long selected = com.maple.utility.security.CredentialScope.current();
        if (selected != null) return selected;
        return keys.findActiveByDataOwnerId(ownerId).map(com.maple.utility.entity.UserApiKey::getId).orElse(-1L);
    }

    private void submitPinned(Long id, Long credentialId, Runnable task) {
        Runnable pinned = () -> {
            try (var scope = com.maple.utility.security.CredentialScope.use(credentialId)) { task.run(); }
        };
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCommit() {
                        try { submit(id, pinned); }
                        catch (ApiException rejected) { log.warn("Committed sync job rejected: jobId={}", id); }
                    }
                });
        } else submit(id, pinned);
    }

    private void submit(Long id, Runnable task) {
		try {
			executor.execute(task);
		} catch (RejectedExecutionException exception) {
			jobs.failIsolated(id, "동기화 작업 대기열 포화", LocalDateTime.now(clock));
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SYNC_QUEUE_FULL", "동기화 요청 과다");
		}
	}

	private void runCharacters(Long userId, Long id) {
		try {
			User user = users.findById(userId).orElseThrow();
			List<NexonCharacterSummary> summaries = nexon.getCharacters(userId);
			characterSync.syncSummaries(user, summaries);
			jobs.setTotal(id, summaries.size());
			for (int i = 0; i < summaries.size(); i++) {
				boolean synced = characterSync.syncCharacterBasic(userId, summaries.get(i));
				jobs.advance(id, !synced);
			}
			jobs.complete(id, LocalDateTime.now(clock));
		} catch (Exception exception) {
			fail(id, exception);
		}
	}

	private void runScheduler(Long userId, Long id, boolean batch, boolean force) {
		try {
			List<MapleCharacter> favorites = characters.findByUserIdAndFavoriteTrueOrderBySortOrderAscIdAsc(userId);
			int historyTargets = batch ? 0 : prepareRecentWeeks(userId);
			jobs.setTotal(id, favorites.size() + historyTargets);
			for (MapleCharacter character : favorites) {
				try {
					schedulerSync.syncOneCharacter(userId, character, batch, force);
					jobs.advance(id, false);
				} catch (NexonApiException exception) {
					if (!"OPENAPI00003".equals(exception.getNexonErrorName())) {
						throw exception;
					}
					log.warn("Skipping Nexon scheduler sync. userId={}, ocid={}, nexonErrorName={}",
							userId, character.getOcid(), exception.getNexonErrorName());
					jobs.advance(id, true);
				}
			}
			if (historyTargets > 0) {
				// Live syncs fill the two finalizable past weeks now; the 02:00 batch only retries what remains.
				weekFinalization.finalizeRecentWeeks(userId, () -> jobs.advance(id, false));
			}
			jobs.complete(id, LocalDateTime.now(clock));
			evictSchedulerCaches();
		} catch (Exception exception) {
			fail(id, exception);
		}
	}

	private int prepareRecentWeeks(Long userId) {
		try {
			return weekFinalization.prepareRecentWeeks(userId);
		} catch (RuntimeException exception) {
			log.warn("Skipping recent week backfill. userId={}", userId, exception);
			return 0;
		}
	}

	private void evictSchedulerCaches() {
		var cache = cacheManager.getCache(RedisCacheNames.SCHEDULER);
		if (cache == null) {
			return;
		}
		// Includes historical summaries and independent weekly/monthly detail cache keys.
		cache.clear();
	}

	private boolean favoritesChanged(Long userId, SyncJobResponse job) {
		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM characters WHERE user_id = ? AND is_favorite = true", Integer.class, userId);
		if (count == null || count != job.totalCount()) {
			return true;
		}
		Boolean updated = jdbcTemplate.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM characters WHERE user_id = ? AND is_favorite = true AND updated_at > ?)",
				Boolean.class, userId, job.completedAt());
		return Boolean.TRUE.equals(updated);
	}

	private void fail(Long id, Exception exception) {
		log.error("Sync job failed. jobId={}", id, exception);
		String message = exception instanceof ApiException api ? api.getCode() : "SYNC_FAILED";
		if (exception instanceof NexonApiException nexon && nexon.getNexonErrorName() != null) {
			message += " (" + nexon.getNexonErrorName() + ")";
		}
		jobs.fail(id, message, LocalDateTime.now(clock));
	}
}
