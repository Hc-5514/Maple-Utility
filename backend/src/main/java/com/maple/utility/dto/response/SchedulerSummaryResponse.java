package com.maple.utility.dto.response;

import java.io.Serializable;
import java.util.List;

public record SchedulerSummaryResponse(
		List<SchedulerDailyResponse> daily,
		List<SchedulerWeeklyResponse> weekly,
		List<SchedulerBossResponse> weeklyBoss,
		List<SchedulerBossResponse> monthlyBoss
) implements Serializable {
}
