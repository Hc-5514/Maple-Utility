package com.maple.utility.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WorldSelectionRequest(@NotNull @Size(max = 14) List<@NotBlank String> worldNames) {
}
