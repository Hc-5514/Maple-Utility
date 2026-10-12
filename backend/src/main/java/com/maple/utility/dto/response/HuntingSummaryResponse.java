package com.maple.utility.dto.response;

public record HuntingSummaryResponse(
		long recordCount,
		long totalMeso,
		long totalSolErda,
		long totalPlayDurationMin
) {
}
