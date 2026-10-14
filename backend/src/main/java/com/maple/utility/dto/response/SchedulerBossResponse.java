package com.maple.utility.dto.response;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.maple.utility.entity.SchedulerBossRecord;

public record SchedulerBossResponse(
		Long characterId,
		String characterName,
		LocalDate recordDate,
		Long bossId,
		String bossName,
		String difficulty,
		String bossImage,
		Long crystalPrice,
		String resetPeriod,
		boolean isCompleted,
		LocalDateTime syncedAt,
		Long acquiredCrystalMeso
) implements Serializable {

	public static SchedulerBossResponse from(SchedulerBossRecord record) {
		return new SchedulerBossResponse(
				record.getCharacter().getId(),
				record.getCharacter().getCharacterName(),
				record.getRecordDate(),
				record.getBoss().getId(),
				record.getBoss().getBossName(),
				record.getBoss().getDifficulty().name(),
				record.getBoss().getBossImage(),
				record.getBoss().getCrystalPrice(),
				record.getResetPeriod().name(),
				record.isCompleted(),
				record.getSyncedAt(),
				record.getBoss().getCrystalPrice()
		);
	}

	public SchedulerBossResponse withAcquiredCrystalMeso(Long acquiredCrystalMeso) {
		return new SchedulerBossResponse(characterId, characterName, recordDate, bossId, bossName, difficulty,
				bossImage, crystalPrice, resetPeriod, isCompleted, syncedAt, acquiredCrystalMeso);
	}
}
