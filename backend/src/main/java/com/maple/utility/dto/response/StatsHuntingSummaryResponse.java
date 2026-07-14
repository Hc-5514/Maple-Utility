package com.maple.utility.dto.response;

import java.io.Serializable;
import java.util.List;

public record StatsHuntingSummaryResponse(
		long totalMeso,
		int totalSolErda,
		long avgDailyMeso,
		int avgDailySolErda,
		List<StatsHuntingDailyResponse> dailyRecords
) implements Serializable {
}
