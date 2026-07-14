package com.maple.utility.security;

public record NexonCharacterBasic(
		String ocid,
		String characterName,
		String worldName,
		String characterClass,
		Integer characterLevel,
		String characterImage,
		String guildName
) {
}
