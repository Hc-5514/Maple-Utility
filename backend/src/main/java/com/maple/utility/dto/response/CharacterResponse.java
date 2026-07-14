package com.maple.utility.dto.response;

import com.maple.utility.entity.MapleCharacter;

public record CharacterResponse(
		Long id,
		String ocid,
		String characterName,
		String worldName,
		String characterClass,
		Integer characterLevel,
		String characterImage,
		String guildName,
		boolean favorite,
		int sortOrder
) {

	public static CharacterResponse from(MapleCharacter character) {
		return new CharacterResponse(
				character.getId(),
				character.getOcid(),
				character.getCharacterName(),
				character.getWorldName(),
				character.getCharacterClass(),
				character.getCharacterLevel(),
				character.getCharacterImage(),
				character.getGuildName(),
				character.isFavorite(),
				character.getSortOrder()
		);
	}
}
