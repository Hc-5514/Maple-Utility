package com.maple.utility.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.maple.utility.repository.UserRepository;
import com.maple.utility.service.SchedulerHistoryFinalizationService;
import com.maple.utility.service.SyncJobService;

class SchedulerBatchConditionTest {

	private final UserRepository users = mock(UserRepository.class);
	private final SyncJobService sync = mock(SyncJobService.class);
	private final SchedulerHistoryFinalizationService history = mock(SchedulerHistoryFinalizationService.class);
	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withBean(UserRepository.class, () -> users)
			.withBean(SyncJobService.class, () -> sync)
			.withBean(SchedulerHistoryFinalizationService.class, () -> history)
			.withUserConfiguration(SchedulerBatchJob.class);

	@Test
	void missingSettingPreservesProductionBatchBean() {
		runner.run(context -> assertThat(context).hasSingleBean(SchedulerBatchJob.class));
	}

	@Test
	void trueSettingRegistersBatchBean() {
		runner.withPropertyValues("app.scheduling.enabled=true")
				.run(context -> assertThat(context).hasSingleBean(SchedulerBatchJob.class));
	}

	@Test
	void falseSettingDisablesBatchWithoutRemovingManualSyncService() {
		runner.withPropertyValues("app.scheduling.enabled=false").run(context -> {
			assertThat(context).doesNotHaveBean(SchedulerBatchJob.class);
			assertThat(context).hasSingleBean(SyncJobService.class);
			assertThat(context).hasSingleBean(SchedulerHistoryFinalizationService.class);
			verifyNoInteractions(sync, history, users);
		});
	}
}
