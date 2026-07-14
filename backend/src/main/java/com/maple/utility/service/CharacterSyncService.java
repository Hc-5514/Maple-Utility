package com.maple.utility.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.maple.utility.entity.MapleCharacter;
import com.maple.utility.entity.User;
import com.maple.utility.repository.CharacterRepository;
import com.maple.utility.security.NexonCharacterSummary;

@Service
public class CharacterSyncService {

	private final CharacterRepository characterRepository;

	public CharacterSyncService(CharacterRepository characterRepository) {
		this.characterRepository = characterRepository;
	}

	public void syncCharacters(User user, List<NexonCharacterSummary> characterSummaries) {
		for (int index = 0; index < characterSummaries.size(); index++) {
			NexonCharacterSummary summary = characterSummaries.get(index);
			int sortOrder = index + 1;
			MapleCharacter character = characterRepository.findByUserIdAndOcid(user.getId(), summary.ocid())
					.orElseGet(() -> characterRepository.save(MapleCharacter.create(
							user,
							summary.ocid(),
							summary.characterName(),
							summary.worldName(),
							summary.characterClass(),
							summary.characterLevel(),
							sortOrder
					)));
			character.updateBasicInfo(
					summary.characterName(),
					summary.worldName(),
					summary.characterClass(),
					summary.characterLevel(),
					sortOrder
			);
		}
	}
}
