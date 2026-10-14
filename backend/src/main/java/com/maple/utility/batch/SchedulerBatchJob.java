package com.maple.utility.batch;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.maple.utility.entity.User;
import com.maple.utility.repository.UserRepository;
import com.maple.utility.service.SyncJobService;
import com.maple.utility.service.SchedulerHistoryFinalizationService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SchedulerBatchJob {

	private final UserRepository userRepository;
	private final SyncJobService syncJobService;
	private final SchedulerHistoryFinalizationService finalizationService;

	public SchedulerBatchJob(
			UserRepository userRepository,
			SyncJobService syncJobService,
			SchedulerHistoryFinalizationService finalizationService
	) {
		this.userRepository = userRepository;
		this.syncJobService = syncJobService;
		this.finalizationService = finalizationService;
	}

	@Scheduled(cron = "0 0 1 * * *", zone = "Asia/Seoul")
	public void syncDailyScheduler() {
		try {
			// Existing retries finish before daily jobs can consume the available shared key budget.
			finalizationService.runBatch(false);
		} catch (RuntimeException exception) {
			log.error("Scheduler finalization retry pass failed before daily batch", exception);
		}
		for (User user : userRepository.findDataOwnersWithActiveKey()) {
			try {
				syncJobService.startScheduler(user.getId(), true, true);
			} catch (RuntimeException exception) {
				log.error("Scheduler batch enqueue failed. userId={}", user.getId(), exception);
			}
		}
	}

	@Scheduled(cron = "0 0 2 * * *", zone = "Asia/Seoul")
	public void finalizeWeeklyScheduler() {
		finalizationService.runBatch(true);
	}
}
