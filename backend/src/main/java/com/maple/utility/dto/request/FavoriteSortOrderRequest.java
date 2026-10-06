package com.maple.utility.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotNull;

public record FavoriteSortOrderRequest(@NotNull List<Long> characterIds) {
}
