package com.maple.utility.dto.response;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.maple.utility.entity.SchedulerWeeklyRecord;

public record SchedulerWeeklyResponse(
		Long characterId,
		String characterName,
		LocalDate weekStartDate,
		String contentName,
		boolean completed,
		Integer score,
		LocalDateTime syncedAt
) implements Serializable {

	public static SchedulerWeeklyResponse from(SchedulerWeeklyRecord record) {
		return new SchedulerWeeklyResponse(
				record.getCharacter().getId(),
				record.getCharacter().getCharacterName(),
				record.getWeekStartDate(),
				record.getContentName(),
				record.isCompleted(),
				record.getScore(),
				record.getSyncedAt()
		);
	}
}
