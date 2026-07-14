package com.maple.utility.dto.response;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.maple.utility.entity.SchedulerBossRecord;

public record SchedulerBossResponse(
		Long characterId,
		String characterName,
		LocalDate recordDate,
		String bossName,
		String difficulty,
		String resetPeriod,
		boolean completed,
		LocalDateTime syncedAt
) implements Serializable {

	public static SchedulerBossResponse from(SchedulerBossRecord record) {
		return new SchedulerBossResponse(
				record.getCharacter().getId(),
				record.getCharacter().getCharacterName(),
				record.getRecordDate(),
				record.getBoss().getBossName(),
				record.getBoss().getDifficulty().name(),
				record.getResetPeriod().name(),
				record.isCompleted(),
				record.getSyncedAt()
		);
	}
}
