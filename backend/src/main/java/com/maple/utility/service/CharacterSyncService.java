package com.maple.utility.service;

import java.util.List;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.maple.utility.entity.MapleCharacter;
import com.maple.utility.entity.User;
import com.maple.utility.exception.NexonApiException;
import com.maple.utility.repository.CharacterRepository;
import com.maple.utility.security.NexonCharacterBasic;
import com.maple.utility.security.NexonCharacterSummary;
import com.maple.utility.security.NexonOpenApiClient;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CharacterSyncService {

	private final CharacterRepository characterRepository;
	private final NexonOpenApiClient nexonOpenApiClient;

	public CharacterSyncService(CharacterRepository characterRepository, NexonOpenApiClient nexonOpenApiClient) {
		this.characterRepository = characterRepository;
		this.nexonOpenApiClient = nexonOpenApiClient;
	}

	@Transactional
	public List<MapleCharacter> syncSummaries(User user, List<NexonCharacterSummary> characterSummaries) {
		int sortOrder = 1;
		for (NexonCharacterSummary summary : characterSummaries) {
			int currentSortOrder = sortOrder;
			MapleCharacter character = characterRepository.findByUserIdAndOcid(user.getId(), summary.ocid())
					.orElseGet(() -> characterRepository.save(MapleCharacter.create(
							user,
							summary.ocid(),
							summary.characterName(),
							summary.worldName(),
							summary.characterClass(),
							summary.characterLevel(),
							currentSortOrder
					)));
			character.updateBasicInfo(
					summary.characterName(),
					summary.worldName(),
					summary.characterClass(),
					summary.characterLevel(),
					currentSortOrder
			);
			sortOrder++;
		}
		return characterRepository.findByUserIdOrderBySortOrderAscIdAsc(user.getId());
	}

	@Transactional
	public List<MapleCharacter> syncCharacters(User user, List<NexonCharacterSummary> characterSummaries) {
		return syncCharacters(user, characterSummaries, summary -> nexonOpenApiClient.getCharacterBasic(user.getId(), summary.ocid()));
	}

	@Transactional
	public List<MapleCharacter> syncCharacters(User user, List<NexonCharacterSummary> characterSummaries, String apiKey) {
		return syncCharacters(user, characterSummaries, summary -> nexonOpenApiClient.getCharacterBasic(user.getId(), apiKey, summary.ocid()));
	}

	@Transactional
	public boolean syncCharacterBasic(Long userId, NexonCharacterSummary summary, int sortOrder) {
		NexonCharacterBasic basic;
		try {
			basic = nexonOpenApiClient.getCharacterBasic(userId, summary.ocid());
		} catch (NexonApiException exception) {
			if (!isInvalidCharacterId(exception)) {
				throw exception;
			}
			log.warn("Skipping Nexon character basic sync. userId={}, characterName={}, ocid={}, nexonErrorName={}, nexonErrorMessage={}",
					userId, summary.characterName(), summary.ocid(), exception.getNexonErrorName(), exception.getNexonErrorMessage());
			return false;
		}
		MapleCharacter character = characterRepository.findByUserIdAndOcid(userId, summary.ocid()).orElseThrow();
		character.updateDetails(
				valueOrFallback(basic.characterName(), summary.characterName()),
				valueOrFallback(basic.worldName(), summary.worldName()),
				valueOrFallback(basic.characterClass(), summary.characterClass()),
				valueOrFallback(basic.characterLevel(), summary.characterLevel()),
				basic.characterImage(), basic.guildName(), sortOrder);
		return true;
	}

	private List<MapleCharacter> syncCharacters(
			User user,
			List<NexonCharacterSummary> characterSummaries,
			Function<NexonCharacterSummary, NexonCharacterBasic> basicFetcher
	) {
		// The list endpoint is authoritative for membership even when basic rejects an OCID.
		syncSummaries(user, characterSummaries);
		int sortOrder = 1;
		for (NexonCharacterSummary summary : characterSummaries) {
			NexonCharacterBasic basic;
			try {
				basic = basicFetcher.apply(summary);
			} catch (NexonApiException exception) {
				if (isInvalidCharacterId(exception)) {
					log.warn(
							"Skipping Nexon character basic sync. userId={}, characterName={}, ocid={}, nexonErrorName={}, nexonErrorMessage={}",
							user.getId(),
							summary.characterName(),
							summary.ocid(),
							exception.getNexonErrorName(),
							exception.getNexonErrorMessage()
					);
					sortOrder++;
					continue;
				}
				throw exception;
			}
			int currentSortOrder = sortOrder;
			MapleCharacter character = characterRepository.findByUserIdAndOcid(user.getId(), summary.ocid())
					.orElseGet(() -> characterRepository.save(MapleCharacter.create(
							user,
							summary.ocid(),
							valueOrFallback(basic.characterName(), summary.characterName()),
							valueOrFallback(basic.worldName(), summary.worldName()),
							valueOrFallback(basic.characterClass(), summary.characterClass()),
							valueOrFallback(basic.characterLevel(), summary.characterLevel()),
							currentSortOrder
					)));
			character.updateDetails(
					valueOrFallback(basic.characterName(), summary.characterName()),
					valueOrFallback(basic.worldName(), summary.worldName()),
					valueOrFallback(basic.characterClass(), summary.characterClass()),
					valueOrFallback(basic.characterLevel(), summary.characterLevel()),
					basic.characterImage(),
					basic.guildName(),
					currentSortOrder
			);
			sortOrder++;
		}
		return characterRepository.findByUserIdOrderBySortOrderAscIdAsc(user.getId());
	}

	private boolean isInvalidCharacterId(NexonApiException exception) {
		return "NEXON_PARAMETER_ERROR".equals(exception.getCode())
				&& "OPENAPI00003".equals(exception.getNexonErrorName());
	}

	private String valueOrFallback(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}

	private Integer valueOrFallback(Integer value, Integer fallback) {
		return value == null ? fallback : value;
	}
}
