package com.maple.utility.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CharacterPreferencesRequest(
		@NotNull List<@NotNull @Valid Preference> characters
) {
	public record Preference(@NotNull @Positive Long characterId, @NotNull Boolean favorite) {
	}
}
