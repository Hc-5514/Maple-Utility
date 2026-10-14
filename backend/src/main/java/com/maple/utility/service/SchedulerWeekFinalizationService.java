package com.maple.utility.service;

import com.maple.utility.util.SchedulerPeriodPolicy;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

import com.maple.utility.repository.SchedulerWeekFinalizationRepository.Target;

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

	/** 조회·진행률에 사용할 지난주 대상 목록 고정. */
	public List<Target> prepareRecentWeeks(Long userId) {
		LocalDateTime now = LocalDateTime.now(clock);
		records.expire(now.toLocalDate(), now);
		if (!SchedulerPeriodPolicy.historyReady(now)) { return List.of(); }
		seedRecentWeeks(userId, now.toLocalDate());
		return List.copyOf(records.due(userId, now.toLocalDate()));
	}

	public boolean hasPendingRecentWeeks(Long userId) {
		LocalDateTime now = LocalDateTime.now(clock);
		return SchedulerPeriodPolicy.historyReady(now) && records.hasDueOrUnseeded(userId, now.toLocalDate());
	}

	public boolean finalizeRecentWeeks(Long userId, List<Target> targets, Consumer<Boolean> onTarget) {
		return finalizeTargets(userId, List.copyOf(targets), NexonRequestMode.REALTIME, onTarget);
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
				if (SchedulerPeriodPolicy.historyReady(LocalDateTime.now(clock))) {
					changed |= finalizeTargets(user.getId(), records.due(user.getId(), today), NexonRequestMode.BATCH, skipped -> { });
				}
			} catch (RuntimeException exception) {
				log.error("Week finalization failed for user. userId={}", user.getId(), exception);
			}
		}
		if (changed) {
			var cache = caches.getCache(RedisCacheNames.SCHEDULER);
			if (cache != null) { cache.clear(); }
		}
	}

	private void seedRecentWeeks(Long userId, LocalDate today) {
		if (SchedulerPeriodPolicy.historyReady(LocalDateTime.now(clock))) {
			records.seed(userId, SchedulerPeriodPolicy.previousWeekStart(today));
		}
	}

	private boolean finalizeTargets(Long userId, List<Target> targets, NexonRequestMode mode, Consumer<Boolean> onTarget) {
		boolean changed = false;
		for (var target : targets) {
			boolean skipped = true;
			try {
				Boolean saved = transaction.execute(status -> {
					LocalDateTime now = LocalDateTime.now(clock);
					if (target.userId() != userId || !SchedulerPeriodPolicy.canFinalize(target.recordDate(), now)) {
						records.expire(now.toLocalDate(), now);
						return false;
					}
					if (!syncLock.tryAcquire(target.userId())) {
						records.retry(target.id(), "SCHEDULER_SYNC_IN_PROGRESS", false, LocalDateTime.now(clock));
						return false;
					}
					var current = records.lockDue(target.id(), now.toLocalDate());
					if (current.isEmpty()) { return false; }
					var character = characters.findByIdAndUserId(target.characterId(), target.userId()).orElse(null);
					if (character == null || !character.isFavorite()) { return false; }
					boolean hasData = sync.syncHistoricalCharacter(target.userId(), character, target.recordDate(), mode);
					records.finish(target.id(), hasData, LocalDateTime.now(clock));
					return hasData;
				});
				skipped = !Boolean.TRUE.equals(saved);
				changed |= !skipped;
			} catch (RuntimeException exception) {
				String error = exception instanceof ApiException api ? api.getCode() : "FINALIZATION_FAILED";
				boolean attempted = !error.endsWith("LIMIT_REACHED") && !error.endsWith("LIMIT_EXCEEDED")
						&& !"SCHEDULER_SYNC_IN_PROGRESS".equals(error) && !"API_KEY_NOT_REGISTERED".equals(error);
				// Separate transaction after rollback: a failed snapshot must leave a durable retry entry.
				LocalDateTime now = LocalDateTime.now(clock);
				if (!target.weekStartDate().equals(SchedulerPeriodPolicy.previousWeekStart(now.toLocalDate()))) {
					records.expire(now.toLocalDate(), now);
				} else {
					records.retry(target.id(), error, attempted, now);
				}
				log.warn("Week finalization deferred. userId={}, characterId={}, date={}, code={}",
						target.userId(), target.characterId(), target.recordDate(), error);
			} finally {
				onTarget.accept(skipped);
			}
		}
		return changed;
	}

}
