package com.maple.utility.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record BossPeriodResponse(
		Long bossId,
		Long characterId,
		LocalDate periodStart,
		int partySize,
		long crystalPrice,
		LocalDateTime savedAt,
		List<Item> items
) {
	public record Item(
			BossDropItemResponse dropItem,
			boolean acquired,
			int quantity,
			Long mesoAmount
	) {
	}
}
