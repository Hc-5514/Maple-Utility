package com.maple.utility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import com.maple.utility.repository.*;
import com.maple.utility.security.*;
import org.springframework.cache.CacheManager;

class SchedulerHistoryFinalizationServiceTest {
	@Test void sameCharacterAndDateShareOneResponseAndTwoLogicalProgressResults() {
		var weeks=mock(SchedulerWeekFinalizationService.class); var months=mock(SchedulerMonthlyFinalizationService.class);
		var nexon=mock(NexonOpenApiClient.class); var end=LocalDate.of(2026,9,30);
		var response=new NexonSchedulerResponse(end,List.of(),List.of(),List.of());
		when(nexon.getCharacterSchedulerForDateStrict(1L,"ocid",end,NexonRequestMode.REALTIME)).thenReturn(response);
		when(months.finalizePrepared(eq(1L),anyList(),eq(NexonRequestMode.REALTIME),any(),any())).thenAnswer(call -> {
			SchedulerHistoryFetch fetch=call.getArgument(4); Consumer<Boolean> callback=call.getArgument(3);
			assertThat(fetch.get(1L,"ocid",end,NexonRequestMode.REALTIME)).isSameAs(response); callback.accept(false); return true;
		});
		when(weeks.finalizePrepared(eq(1L),anyList(),eq(NexonRequestMode.REALTIME),any(),any())).thenAnswer(call -> {
			SchedulerHistoryFetch fetch=call.getArgument(4); Consumer<Boolean> callback=call.getArgument(3);
			assertThat(fetch.get(1L,"ocid",end,NexonRequestMode.REALTIME)).isSameAs(response); callback.accept(false); return true;
		});
		var work=new SchedulerHistoryFinalizationService.Work(List.of(new SchedulerWeekFinalizationRepository.Target(1,1,10,end.minusDays(6))),
				List.of(new SchedulerMonthlyFinalizationRepository.Target(2,1,10,end.withDayOfMonth(1))));
		var service=new SchedulerHistoryFinalizationService(weeks,months,nexon,mock(UserRepository.class),mock(CacheManager.class));
		var progress=new ArrayList<Boolean>(); assertThat(service.finalizePrepared(1L,work,progress::add)).isTrue();
		assertThat(work.size()).isEqualTo(2); assertThat(progress).containsExactly(false,false);
		verify(nexon,times(1)).getCharacterSchedulerForDateStrict(1L,"ocid",end,NexonRequestMode.REALTIME);
		var order=inOrder(months,weeks); order.verify(months).finalizePrepared(any(),any(),any(),any(),any()); order.verify(weeks).finalizePrepared(any(),any(),any(),any(),any());
	}
	@Test void monthlyDueWorkPreventsCompletedJobReuseEvenIfWeeklyWorkIsFinalized() {
		var weeks=mock(SchedulerWeekFinalizationService.class); var months=mock(SchedulerMonthlyFinalizationService.class);
		when(months.hasPending(1L)).thenReturn(true);
		var service=new SchedulerHistoryFinalizationService(weeks,months,mock(NexonOpenApiClient.class),mock(UserRepository.class),mock(CacheManager.class));
		assertThat(service.hasPendingHistory(1L)).isTrue();
	}
	@Test void expiredWorkIsCleanedEvenWithoutAnyActiveOwners() {
		var weeks=mock(SchedulerWeekFinalizationService.class); var months=mock(SchedulerMonthlyFinalizationService.class);
		var users=mock(UserRepository.class); when(users.findDataOwnersWithActiveKey()).thenReturn(List.of());
		var service=new SchedulerHistoryFinalizationService(weeks,months,mock(NexonOpenApiClient.class),users,mock(CacheManager.class));
		service.runBatch(false); verify(weeks).expireOldTargets(); verify(months).expireOldTargets();
	}

}
