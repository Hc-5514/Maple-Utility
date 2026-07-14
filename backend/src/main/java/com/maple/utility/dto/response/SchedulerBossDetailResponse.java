package com.maple.utility.dto.response;

import java.io.Serializable;
import java.util.List;

public record SchedulerBossDetailResponse(
		List<SchedulerBossResponse> weeklyBoss,
		List<SchedulerBossResponse> monthlyBoss
) implements Serializable {
}
