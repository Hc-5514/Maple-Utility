package com.maple.utility.dto.response;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.maple.utility.entity.SchedulerDailyRecord;

public record SchedulerDailyResponse(
		Long characterId,
		String characterName,
		LocalDate recordDate,
		String contentName,
		int completedCount,
		int totalCount,
		LocalDateTime syncedAt
) implements Serializable {

	public static SchedulerDailyResponse from(SchedulerDailyRecord record) {
		return new SchedulerDailyResponse(
				record.getCharacter().getId(),
				record.getCharacter().getCharacterName(),
				record.getRecordDate(),
				record.getContentName(),
				record.getCompletedCount(),
				record.getTotalCount(),
				record.getSyncedAt()
		);
	}
}
