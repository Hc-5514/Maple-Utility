package com.maple.utility.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record DataContextSelectRequest(@NotNull @Positive Long credentialId) { }
