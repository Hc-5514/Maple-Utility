package com.maple.utility.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BossItemAcquisitionCreateRequest(
		@NotNull Long characterId,
		@NotNull Long bossDropItemId,
		@NotNull LocalDate acquiredDate,
		@Size(max = 255) String memo
) {
}
