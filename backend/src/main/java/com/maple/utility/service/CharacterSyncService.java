package com.maple.utility.service;

import java.util.List;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.maple.utility.entity.MapleCharacter;
import com.maple.utility.entity.User;
import com.maple.utility.repository.CharacterRepository;
import com.maple.utility.security.NexonCharacterBasic;
import com.maple.utility.security.NexonCharacterSummary;
import com.maple.utility.security.NexonOpenApiClient;

@Service
public class CharacterSyncService {

	private final CharacterRepository characterRepository;
	private final NexonOpenApiClient nexonOpenApiClient;

	public CharacterSyncService(CharacterRepository characterRepository, NexonOpenApiClient nexonOpenApiClient) {
		this.characterRepository = characterRepository;
		this.nexonOpenApiClient = nexonOpenApiClient;
	}

	@Transactional
	public List<MapleCharacter> syncCharacters(User user, List<NexonCharacterSummary> characterSummaries) {
		return syncCharacters(user, characterSummaries, summary -> nexonOpenApiClient.getCharacterBasic(user.getId(), summary.ocid()));
	}

	@Transactional
	public List<MapleCharacter> syncCharacters(User user, List<NexonCharacterSummary> characterSummaries, String apiKey) {
		return syncCharacters(user, characterSummaries, summary -> nexonOpenApiClient.getCharacterBasic(user.getId(), apiKey, summary.ocid()));
	}

	private List<MapleCharacter> syncCharacters(
			User user,
			List<NexonCharacterSummary> characterSummaries,
			Function<NexonCharacterSummary, NexonCharacterBasic> basicFetcher
	) {
		for (int index = 0; index < characterSummaries.size(); index++) {
			NexonCharacterSummary summary = characterSummaries.get(index);
			NexonCharacterBasic basic = basicFetcher.apply(summary);
			int sortOrder = index + 1;
			MapleCharacter character = characterRepository.findByUserIdAndOcid(user.getId(), summary.ocid())
					.orElseGet(() -> characterRepository.save(MapleCharacter.create(
							user,
							summary.ocid(),
							valueOrFallback(basic.characterName(), summary.characterName()),
							valueOrFallback(basic.worldName(), summary.worldName()),
							valueOrFallback(basic.characterClass(), summary.characterClass()),
							valueOrFallback(basic.characterLevel(), summary.characterLevel()),
							sortOrder
					)));
			character.updateDetails(
					valueOrFallback(basic.characterName(), summary.characterName()),
					valueOrFallback(basic.worldName(), summary.worldName()),
					valueOrFallback(basic.characterClass(), summary.characterClass()),
					valueOrFallback(basic.characterLevel(), summary.characterLevel()),
					basic.characterImage(),
					basic.guildName(),
					sortOrder
			);
		}
		return characterRepository.findByUserIdOrderBySortOrderAscIdAsc(user.getId());
	}

	private String valueOrFallback(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}

	private Integer valueOrFallback(Integer value, Integer fallback) {
		return value == null ? fallback : value;
	}
}
