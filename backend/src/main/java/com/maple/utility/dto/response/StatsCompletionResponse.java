package com.maple.utility.dto.response;

import java.io.Serializable;

public record StatsCompletionResponse(
		StatsCompletionDetailResponse daily,
		StatsCompletionDetailResponse boss
) implements Serializable {
}
