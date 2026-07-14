package com.maple.utility.dto.response;

import java.io.Serializable;
import java.time.LocalDate;

import com.maple.utility.entity.BossItemAcquisition;

public record BossItemAcquisitionResponse(
		Long id,
		Long characterId,
		Long bossDropItemId,
		LocalDate acquiredDate,
		String memo
) implements Serializable {

	public static BossItemAcquisitionResponse from(BossItemAcquisition acquisition) {
		return new BossItemAcquisitionResponse(
				acquisition.getId(),
				acquisition.getCharacter().getId(),
				acquisition.getBossDropItem().getId(),
				acquisition.getAcquiredDate(),
				acquisition.getMemo()
		);
	}
}
