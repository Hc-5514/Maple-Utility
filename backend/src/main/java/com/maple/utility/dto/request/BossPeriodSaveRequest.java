package com.maple.utility.dto.request;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record BossPeriodSaveRequest(
		@NotNull Long characterId,
		@NotNull LocalDate periodStart,
		@Min(1) @Max(6) int partySize,
		@Min(0) @Max(BossPeriodSaveRequest.MAX_CRYSTAL_PRICE) Long crystalPrice,
		@NotNull List<@Valid Item> items
) {
	/** 결정 가격 입력 상한(1,000억 메소) */
	public static final long MAX_CRYSTAL_PRICE = 100_000_000_000L;

	public record Item(
			@NotNull Long bossDropItemId,
			boolean acquired,
			@Min(1) int quantity,
			@Min(0) Long mesoAmount
	) {
	}
}
