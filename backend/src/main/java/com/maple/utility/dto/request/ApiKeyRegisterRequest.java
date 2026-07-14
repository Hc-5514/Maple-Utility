package com.maple.utility.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ApiKeyRegisterRequest(
		@NotBlank String apiKey
) {
}
