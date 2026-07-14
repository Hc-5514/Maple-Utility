package com.maple.utility.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record CharacterSortOrderRequest(
		@NotNull
		@PositiveOrZero
		Integer sortOrder
) {
}
