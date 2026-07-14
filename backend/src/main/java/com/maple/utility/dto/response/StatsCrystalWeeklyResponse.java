package com.maple.utility.dto.response;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

public record StatsCrystalWeeklyResponse(
		LocalDate weekStart,
		long totalIncome,
		List<StatsCrystalBossDetailResponse> bossDetails
) implements Serializable {
}
