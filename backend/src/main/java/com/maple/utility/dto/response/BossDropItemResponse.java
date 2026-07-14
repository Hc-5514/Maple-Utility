package com.maple.utility.dto.response;

import java.io.Serializable;

import com.maple.utility.entity.BossDropItem;
import com.maple.utility.entity.DropRateTier;

public record BossDropItemResponse(
		Long id,
		Long bossId,
		String itemName,
		String itemImage,
		String itemDescription,
		DropRateTier dropRateTier
) implements Serializable {

	public static BossDropItemResponse from(BossDropItem item) {
		return new BossDropItemResponse(
				item.getId(),
				item.getBoss().getId(),
				item.getItemName(),
				item.getItemImage(),
				item.getItemDescription(),
				item.getDropRateTier()
		);
	}
}
