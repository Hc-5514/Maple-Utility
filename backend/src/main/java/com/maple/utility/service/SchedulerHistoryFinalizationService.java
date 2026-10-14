package com.maple.utility.service;

import java.util.List;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import com.maple.utility.config.RedisCacheNames;
import com.maple.utility.repository.SchedulerMonthlyFinalizationRepository;
import com.maple.utility.repository.SchedulerWeekFinalizationRepository;
import com.maple.utility.repository.UserRepository;
import com.maple.utility.security.NexonOpenApiClient;
import com.maple.utility.security.NexonRequestMode;

@Service
public class SchedulerHistoryFinalizationService {
	public record Work(List<SchedulerWeekFinalizationRepository.Target> weeks, List<SchedulerMonthlyFinalizationRepository.Target> months) {
		public Work { weeks = List.copyOf(weeks); months = List.copyOf(months); }
		public static Work empty() { return new Work(List.of(), List.of()); }
		public int size() { return weeks.size() + months.size(); }
		public Work favorites(java.util.Set<Long> ids) {
			return new Work(weeks.stream().filter(t -> ids.contains(t.characterId())).toList(), months.stream().filter(t -> ids.contains(t.characterId())).toList());
		}
	}
	private static final Logger log = LoggerFactory.getLogger(SchedulerHistoryFinalizationService.class);
	private final SchedulerWeekFinalizationService weeks;
	private final SchedulerMonthlyFinalizationService months;
	private final NexonOpenApiClient nexon;
	private final UserRepository users;
	private final CacheManager caches;
	public SchedulerHistoryFinalizationService(SchedulerWeekFinalizationService weeks, SchedulerMonthlyFinalizationService months,
			NexonOpenApiClient nexon, UserRepository users, CacheManager caches) {
		this.weeks = weeks; this.months = months; this.nexon = nexon; this.users = users; this.caches = caches;
	}
	public Work prepare(Long userId) { return prepare(userId, true); }
	private Work prepare(Long userId, boolean seed) {
		return new Work(seed ? weeks.prepareRecentWeeks(userId) : weeks.pendingRecentWeeks(userId), months.prepare(userId, seed));
	}
	public boolean hasPendingHistory(Long userId) { return weeks.hasPendingRecentWeeks(userId) || months.hasPending(userId); }
	public boolean finalizePrepared(Long userId, Work work, Consumer<Boolean> callback) {
		return execute(userId, work, NexonRequestMode.REALTIME, callback);
	}
	private boolean execute(Long userId, Work work, NexonRequestMode mode, Consumer<Boolean> callback) {
		var fetch = new SchedulerHistoryFetch(nexon);
		boolean changed = months.finalizePrepared(userId, work.months(), mode, callback, fetch);
		return weeks.finalizePrepared(userId, work.weeks(), mode, callback, fetch) || changed;
	}
	public void runBatch(boolean seed) {
		weeks.expireOldTargets();
		months.expireOldTargets();
		boolean changed = false;
		int[] results = new int[2];
		for (var user : users.findDataOwnersWithActiveKey()) {
			try { changed |= execute(user.getId(), prepare(user.getId(), seed), NexonRequestMode.BATCH, skipped -> results[skipped ? 1 : 0]++); }
			catch (RuntimeException failure) { log.error("Historical finalization failed. userId={}", user.getId(), failure); }
		}
		log.info("Historical finalization batch completed. collected={}, skipped={}", results[0], results[1]);
		if (changed) { var cache = caches.getCache(RedisCacheNames.SCHEDULER); if (cache != null) { cache.clear(); } }
	}
}
