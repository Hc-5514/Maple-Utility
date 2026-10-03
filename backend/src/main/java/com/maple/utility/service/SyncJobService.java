package com.maple.utility.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
	private final NexonOpenApiClient nexon;
	private final CacheManager cacheManager;
	private final JdbcTemplate jdbcTemplate;
	private final Executor executor;
	private final Clock clock;

	public SyncJobService(SyncJobRepository jobs, UserRepository users, CharacterRepository characters,
			CharacterSyncService characterSync, SchedulerSyncService schedulerSync, NexonOpenApiClient nexon,
			CacheManager cacheManager, JdbcTemplate jdbcTemplate, @Qualifier("syncExecutor") Executor executor, Clock clock) {
		this.jobs = jobs;
		this.users = users;
		this.characters = characters;
		this.characterSync = characterSync;
		this.schedulerSync = schedulerSync;
		this.nexon = nexon;
		this.cacheManager = cacheManager;
		this.jdbcTemplate = jdbcTemplate;
		this.executor = executor;
		this.clock = clock;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void failInterruptedJobs() {
		jobs.failInterrupted(LocalDateTime.now(clock));
	}

	public SyncJobResponse startCharacters(Long userId) {
		var result = jobs.start(userId, "CHARACTER");
		if (result.created()) {
			submit(result.job().id(), () -> runCharacters(userId, result.job().id()));
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
		var result = jobs.start(userId, "SCHEDULER");
		if (result.created()) {
			submit(result.job().id(), () -> runScheduler(userId, result.job().id(), batch, force));
		}
		return result.job();
	}

	public SyncJobResponse get(Long userId, Long jobId) {
		return jobs.get(userId, jobId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SYNC_JOB_NOT_FOUND", "동기화 작업 없음"));
	}

	private void submit(Long id, Runnable task) {
		try {
			executor.execute(task);
		} catch (RejectedExecutionException exception) {
			jobs.fail(id, "동기화 작업 대기열 포화", LocalDateTime.now(clock));
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
				boolean synced = characterSync.syncCharacterBasic(userId, summaries.get(i), i + 1);
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
			jobs.setTotal(id, favorites.size());
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
			evictSchedulerCaches(userId, favorites);
			jobs.complete(id, LocalDateTime.now(clock));
		} catch (Exception exception) {
			fail(id, exception);
		}
	}

	private void evictSchedulerCaches(Long userId, List<MapleCharacter> favorites) {
		var cache = cacheManager.getCache(RedisCacheNames.SCHEDULER);
		if (cache == null) {
			return;
		}
		LocalDate today = LocalDate.now(clock);
		Set<String> dates = Stream.of("null", today.toString(),
				today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()).collect(Collectors.toSet());
		for (String date : dates) {
			cache.evict("summary:" + userId + ":" + date);
			for (MapleCharacter character : favorites) {
				for (String view : List.of("daily", "weekly", "boss", "guild")) {
					cache.evict(view + ":" + userId + ":" + character.getId() + ":" + date);
				}
			}
		}
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
