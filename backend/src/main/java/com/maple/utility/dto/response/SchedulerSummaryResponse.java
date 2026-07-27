package com.maple.utility.dto.response;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

public record SchedulerSummaryResponse(
		List<SchedulerCharacterSummaryResponse> characters,
		LocalDateTime syncedAt
) implements Serializable {
}
