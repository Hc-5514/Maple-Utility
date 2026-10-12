package com.maple.utility.dto.response;

import java.util.List;

public record WorldSelectionResponse(List<String> worldNames) {
	public WorldSelectionResponse {
		worldNames = List.copyOf(worldNames);
	}
}
