package com.maple.utility.dto.response;

import java.io.Serializable;
import java.util.List;

public record SchedulerBossDetailResponse(
		List<SchedulerBossResponse> weeklyBosses,
		List<SchedulerBossResponse> monthlyBosses,
		boolean weeklyCustomizable,
		boolean monthlyCustomizable
) implements Serializable {
}
