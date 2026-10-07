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
		@NotNull List<@Valid Item> items
) {
	public record Item(
			@NotNull Long bossDropItemId,
			boolean acquired,
			@Min(1) int quantity,
			@Min(0) Long mesoAmount
	) {
	}
}
