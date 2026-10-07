package com.maple.utility.dto.response;

import java.io.Serializable;

import com.maple.utility.entity.BossDropItem;
import com.maple.utility.entity.DropRateTier;
import com.maple.utility.entity.BossItemKind;

public record BossDropItemResponse(
		Long id,
		Long bossId,
		String itemName,
		String itemImage,
		String itemDescription,
		DropRateTier dropRateTier,
		BossItemKind itemKind,
		int defaultQuantity
) implements Serializable {

	public static BossDropItemResponse from(BossDropItem item) {
		return new BossDropItemResponse(
				item.getId(),
				item.getBoss().getId(),
				item.getItemName(),
				item.getItemImage(),
				item.getItemDescription(),
				item.getDropRateTier(),
				item.getItemKind(),
				item.getDefaultQuantity()
		);
	}
}
