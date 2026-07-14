package com.maple.utility.dto.response;

import java.io.Serializable;

public record StatsCompletionDetailResponse(
		int completed,
		int total,
		int completionRate
) implements Serializable {
}
