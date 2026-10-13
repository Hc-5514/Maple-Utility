package com.maple.utility.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.maple.utility.config.RedisCacheNames;
import com.maple.utility.exception.ApiException;
import com.maple.utility.repository.CharacterRepository;
import com.maple.utility.repository.SchedulerWeekFinalizationRepository;
import com.maple.utility.repository.UserRepository;
import com.maple.utility.security.NexonRequestMode;

@Service
public class SchedulerWeekFinalizationService {
	private static final Logger log = LoggerFactory.getLogger(SchedulerWeekFinalizationService.class);
	private final SchedulerWeekFinalizationRepository records;
	private final UserRepository users;
	private final CharacterRepository characters;
	private final SchedulerSyncService sync;
	private final SchedulerSyncLock syncLock;
	private final CacheManager caches;
	private final TransactionTemplate transaction;
	private final Clock clock;

	public SchedulerWeekFinalizationService(SchedulerWeekFinalizationRepository records, UserRepository users,
			CharacterRepository characters, SchedulerSyncService sync, SchedulerSyncLock syncLock,
			CacheManager caches, PlatformTransactionManager manager, Clock clock) {
		this.records = records;
		this.users = users;
		this.characters = characters;
		this.sync = sync;
		this.syncLock = syncLock;
		this.caches = caches;
		this.transaction = new TransactionTemplate(manager);
		this.clock = clock;
	}

	public void finalizeWeeks() {
		run(true);
	}

	/** Prioritize durable retries before the daily batch, without seeding a new Wednesday before 02:00. */
	public void retryPendingWeeks() {
		run(false);
	}

	/** Seeds the two completed Wednesdays for a live sync and returns how many still need a snapshot. */
	public int prepareRecentWeeks(Long userId) {
		LocalDate today = LocalDate.now(clock);
		records.expire(today, LocalDateTime.now(clock));
		seedRecentWeeks(userId, today);
		return records.due(userId, today).size();
	}

	/** Fills unfinalized recent weeks during a live sync; finalized weeks are skipped by the durable status. */
	public boolean finalizeRecentWeeks(Long userId, Runnable onTarget) {
		return finalizeUser(userId, LocalDate.now(clock), NexonRequestMode.REALTIME, onTarget);
	}

	private void run(boolean seedNewWeeks) {
		LocalDate today = LocalDate.now(clock);
		records.expire(today, LocalDateTime.now(clock));
		boolean changed = false;
		for (var user : users.findDataOwnersWithActiveKey()) {
			try {
				if (seedNewWeeks) {
					seedRecentWeeks(user.getId(), today);
				}
				changed |= finalizeUser(user.getId(), today, NexonRequestMode.BATCH, () -> { });
			} catch (RuntimeException exception) {
				log.error("Week finalization failed for user. userId={}", user.getId(), exception);
			}
		}
		if (changed) {
			var cache = caches.getCache(RedisCacheNames.SCHEDULER);
			if (cache != null) { cache.clear(); }
		}
	}

	// Seed the two completed Wednesdays still covered by Nexon's inclusive 14-day window.
	private void seedRecentWeeks(Long userId, LocalDate today) {
		LocalDate wednesday = today.with(TemporalAdjusters.previous(DayOfWeek.WEDNESDAY));
		while (!wednesday.isBefore(today.minusDays(14))) {
			records.seed(userId, wednesday.minusDays(6));
			wednesday = wednesday.minusWeeks(1);
		}
	}

	private boolean finalizeUser(Long userId, LocalDate today, NexonRequestMode mode, Runnable onTarget) {
		boolean changed = false;
		for (var target : records.due(userId, today)) {
			try {
				Boolean saved = transaction.execute(status -> {
					if (!syncLock.tryAcquire(target.userId())) {
						records.retry(target.id(), "SCHEDULER_SYNC_IN_PROGRESS", false, LocalDateTime.now(clock));
						return false;
					}
					var current = records.lockDue(target.id(), today);
					if (current.isEmpty()) { return false; }
					var character = characters.findByIdAndUserId(target.characterId(), target.userId()).orElse(null);
					if (character == null) { return false; }
					boolean hasData = sync.syncHistoricalCharacter(target.userId(), character, target.recordDate(), mode);
					records.finish(target.id(), hasData, LocalDateTime.now(clock));
					return hasData;
				});
				changed |= Boolean.TRUE.equals(saved);
			} catch (RuntimeException exception) {
				String error = exception instanceof ApiException api ? api.getCode() : "FINALIZATION_FAILED";
				boolean attempted = !error.endsWith("LIMIT_REACHED") && !error.endsWith("LIMIT_EXCEEDED")
						&& !"SCHEDULER_SYNC_IN_PROGRESS".equals(error) && !"API_KEY_NOT_REGISTERED".equals(error);
				// Separate transaction after rollback: a failed snapshot must leave a durable retry entry.
				records.retry(target.id(), error, attempted, LocalDateTime.now(clock));
				log.warn("Week finalization deferred. userId={}, characterId={}, date={}, code={}",
						target.userId(), target.characterId(), target.recordDate(), error);
			} finally {
				onTarget.run();
			}
		}
		return changed;
	}

}
