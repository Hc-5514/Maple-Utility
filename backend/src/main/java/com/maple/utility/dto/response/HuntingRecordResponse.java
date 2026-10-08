package com.maple.utility.dto.response;

import java.io.Serializable;
import java.time.LocalDate;

import com.maple.utility.entity.HuntingRecord;

public record HuntingRecordResponse(
		Long id,
		Long characterId,
		LocalDate recordDate,
		long mesoEarned,
		int solErdaEarned,
		Integer playDurationMin,
		Long huntingGroundId,
		String regionName,
		String huntingGround,
		String memo
) implements Serializable {

	public static HuntingRecordResponse from(HuntingRecord record) {
		return new HuntingRecordResponse(
				record.getId(),
				record.getCharacter().getId(),
				record.getRecordDate(),
				record.getMesoEarned(),
				record.getSolErdaEarned(),
				record.getPlayDurationMin(),
				record.getHuntingGroundCatalog() == null ? null : record.getHuntingGroundCatalog().getId(),
				record.getHuntingGroundCatalog() == null ? null : record.getHuntingGroundCatalog().getRegionName(),
				record.getHuntingGround(),
				record.getMemo()
		);
	}
}
