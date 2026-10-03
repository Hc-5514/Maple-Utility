package com.maple.utility.dto.response;

import java.time.LocalDateTime;

public record SyncJobResponse(
		Long id,
		String jobType,
		String status,
		int totalCount,
		int completedCount,
		int skippedCount,
		String errorMessage,
		LocalDateTime startedAt,
		LocalDateTime completedAt
) {
}
