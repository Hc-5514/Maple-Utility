package com.maple.utility.dto.response;

import java.io.Serializable;
import java.util.List;

public record BossDropItemAcquisitionStatusResponse(
		BossDropItemResponse dropItem,
		boolean acquired,
		List<BossItemAcquisitionResponse> acquisitions
) implements Serializable {

	public static BossDropItemAcquisitionStatusResponse of(
			BossDropItemResponse dropItem,
			List<BossItemAcquisitionResponse> acquisitions
	) {
		return new BossDropItemAcquisitionStatusResponse(dropItem, !acquisitions.isEmpty(), acquisitions);
	}
}
