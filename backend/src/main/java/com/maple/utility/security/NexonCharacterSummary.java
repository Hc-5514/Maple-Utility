package com.maple.utility.security;

public record NexonCharacterSummary(
		String ocid,
		String characterName,
		String worldName,
		String characterClass,
		Integer characterLevel
) {
}
