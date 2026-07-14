package com.maple.utility.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record HuntingRecordRequest(
		@NotNull Long characterId,
		@NotNull LocalDate recordDate,
		@PositiveOrZero Long mesoEarned,
		@PositiveOrZero Integer solErdaEarned,
		@PositiveOrZero Integer playDurationMin,
		@Size(max = 100) String huntingGround,
		String memo
) {
}
