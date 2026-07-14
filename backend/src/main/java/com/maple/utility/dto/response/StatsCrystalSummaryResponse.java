package com.maple.utility.dto.response;

import java.io.Serializable;
import java.util.List;

public record StatsCrystalSummaryResponse(
		long totalCrystalIncome,
		long weeklyAverage,
		List<StatsCrystalWeeklyResponse> weeklyRecords
) implements Serializable {
}
