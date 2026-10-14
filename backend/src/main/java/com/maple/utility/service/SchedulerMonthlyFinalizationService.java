package com.maple.utility.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.maple.utility.entity.ResetPeriod;
import com.maple.utility.exception.ApiException;
import com.maple.utility.repository.BossMasterRepository;
import com.maple.utility.repository.CharacterRepository;
import com.maple.utility.repository.SchedulerMonthlyFinalizationRepository;
import com.maple.utility.repository.SchedulerMonthlyFinalizationRepository.BossSnapshot;
import com.maple.utility.repository.SchedulerMonthlyFinalizationRepository.Target;
import com.maple.utility.security.NexonRequestMode;
import com.maple.utility.util.SchedulerPeriodPolicy;

@Service
public class SchedulerMonthlyFinalizationService {
	private static final Logger log = LoggerFactory.getLogger(SchedulerMonthlyFinalizationService.class);
	private final SchedulerMonthlyFinalizationRepository records;
	private final CharacterRepository characters;
	private final BossMasterRepository bosses;
	private final SchedulerSyncLock lock;
	private final TransactionTemplate transaction;
	private final Clock clock;
	public SchedulerMonthlyFinalizationService(SchedulerMonthlyFinalizationRepository records, CharacterRepository characters,
			BossMasterRepository bosses, SchedulerSyncLock lock, PlatformTransactionManager manager, Clock clock) {
		this.records = records; this.characters = characters; this.bosses = bosses; this.lock = lock;
		this.transaction = new TransactionTemplate(manager); this.clock = clock;
	}
	public void expireOldTargets() {
		var now = LocalDateTime.now(clock);
		int expired = records.expire(now.toLocalDate(), now);
		if (expired > 0) { log.info("Monthly finalizations expired. count={}", expired); }
	}
	public List<Target> prepare(Long userId, boolean seed) {
		var now = LocalDateTime.now(clock);
		expireOldTargets();
		var month = SchedulerPeriodPolicy.lastMonthStart(now.toLocalDate());
		if (!SchedulerPeriodPolicy.canFinalizeMonth(month.plusMonths(1).minusDays(1), now)) { return List.of(); }
		if (seed) { records.seed(userId, month); }
		return List.copyOf(records.due(userId, now.toLocalDate()));
	}
	public boolean hasPending(Long userId) {
		var now = LocalDateTime.now(clock);
		var month = SchedulerPeriodPolicy.lastMonthStart(now.toLocalDate());
		return SchedulerPeriodPolicy.canFinalizeMonth(month.plusMonths(1).minusDays(1), now)
				&& records.hasDueOrUnseeded(userId, month, now.toLocalDate());
	}
	public boolean finalizePrepared(Long userId, List<Target> targets, NexonRequestMode mode,
			Consumer<Boolean> onTarget, SchedulerHistoryFetch fetch) {
		boolean changed = false;
		for (var target : targets) {
			boolean skipped = true;
			try {
				Boolean saved = transaction.execute(status -> {
					var now = LocalDateTime.now(clock);
					if (target.userId() != userId || !SchedulerPeriodPolicy.canFinalizeMonth(target.recordDate(), now)) {
						records.expire(now.toLocalDate(), now); return false;
					}
					if (!lock.tryAcquire(userId)) {
						records.retry(target.id(), "SCHEDULER_SYNC_IN_PROGRESS", false, now); return false;
					}
					if (records.lockDue(target.id(), now.toLocalDate()).isEmpty()) { return false; }
					var character = characters.findByIdAndUserId(target.characterId(), userId).orElse(null);
					if (character == null || !character.isFavorite()) { return false; }
					log.info("Monthly finalization attempt. userId={}, characterId={}, date={}", userId, target.characterId(), target.recordDate());
					var response = fetch.get(userId, character.getOcid(), target.recordDate(), mode);
					if (!SchedulerPeriodPolicy.canFinalizeMonth(target.recordDate(), LocalDateTime.now(clock))) {
						throw new ApiException(HttpStatus.CONFLICT, "NEXON_HISTORY_SCOPE_CHANGED", "월말 조회 기간 변경");
					}
					if (response == null) {
						records.noData(target.id(), LocalDateTime.now(clock));
						log.info("Monthly finalization has no data. userId={}, characterId={}, date={}", userId, target.characterId(), target.recordDate());
						return false;
					}
					if (!target.recordDate().equals(response.date()) || response.boss().stream().anyMatch(row -> !target.recordDate().equals(row.recordDate()))) {
						throw new ApiException(HttpStatus.BAD_GATEWAY, "NEXON_HISTORY_DATE_MISMATCH", "월말 조회 날짜 불일치");
					}
					List<BossSnapshot> snapshots = new ArrayList<>();
					var ids = new HashSet<Long>();
					for (var row : response.boss()) {
						if (row.resetPeriod() != ResetPeriod.MONTHLY) { continue; }
						var boss = bosses.findByBossNameAndDifficulty(row.bossName(), row.difficulty())
								.orElseThrow(() -> new ApiException(HttpStatus.BAD_GATEWAY, "NEXON_MONTH_BOSS_INVALID", "월간 보스 매핑 누락"));
						if (boss.getResetPeriod() != ResetPeriod.MONTHLY || !ids.add(boss.getId())) {
							throw new ApiException(HttpStatus.BAD_GATEWAY, "NEXON_MONTH_BOSS_INVALID", "월간 보스 매핑 오류");
						}
						snapshots.add(new BossSnapshot(boss.getId(), row.completed()));
					}
					var savedAt = LocalDateTime.now(clock);
					if (!SchedulerPeriodPolicy.canFinalizeMonth(target.recordDate(), savedAt)) {
						throw new ApiException(HttpStatus.CONFLICT, "NEXON_HISTORY_SCOPE_CHANGED", "월말 조회 기간 변경");
					}
					records.finish(target, List.copyOf(snapshots), savedAt);
					log.info("Monthly finalization completed. userId={}, characterId={}, date={}, bosses={}", userId, target.characterId(), target.recordDate(), snapshots.size());
					return true;
				});
				skipped = !Boolean.TRUE.equals(saved); changed |= !skipped;
			} catch (RuntimeException exception) {
				var now = LocalDateTime.now(clock);
				String code = exception instanceof ApiException api ? api.getCode() : "FINALIZATION_FAILED";
				boolean attempted = !code.endsWith("LIMIT_REACHED") && !code.endsWith("LIMIT_EXCEEDED")
						&& !code.equals("API_KEY_NOT_REGISTERED") && !code.equals("SCHEDULER_SYNC_IN_PROGRESS");
				try {
					if (!SchedulerPeriodPolicy.withinHistoryWindow(target.recordDate(), now.toLocalDate())) { records.expire(now.toLocalDate(), now); }
					else { records.retry(target.id(), code, attempted, now); }
				} catch (RuntimeException storageFailure) {
					log.error("Monthly retry state unavailable. userId={}, targetId={}", userId, target.id(), storageFailure);
				}
				log.warn("Monthly finalization deferred. userId={}, characterId={}, date={}, code={}", userId, target.characterId(), target.recordDate(), code);
			} finally { onTarget.accept(skipped); }
		}
		return changed;
	}
}
