package com.maple.utility.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.maple.utility.dto.response.CharacterResponse;
import com.maple.utility.entity.MapleCharacter;
import com.maple.utility.entity.User;
import com.maple.utility.exception.ApiException;
import com.maple.utility.repository.CharacterRepository;
import com.maple.utility.repository.UserRepository;
import com.maple.utility.security.NexonCharacterSummary;
import com.maple.utility.security.NexonOpenApiClient;

@Service
public class CharacterService {

	private final CharacterRepository characterRepository;
	private final UserRepository userRepository;
	private final NexonOpenApiClient nexonOpenApiClient;
	private final CharacterSyncService characterSyncService;

	public CharacterService(
			CharacterRepository characterRepository,
			UserRepository userRepository,
			NexonOpenApiClient nexonOpenApiClient,
			CharacterSyncService characterSyncService
	) {
		this.characterRepository = characterRepository;
		this.userRepository = userRepository;
		this.nexonOpenApiClient = nexonOpenApiClient;
		this.characterSyncService = characterSyncService;
	}

	@Transactional(readOnly = true)
	public List<CharacterResponse> getCharacters(Long userId) {
		return characterRepository.findByUserIdOrderBySortOrderAscIdAsc(userId).stream()
				.map(CharacterResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<CharacterResponse> getFavoriteCharacters(Long userId) {
		return characterRepository.findByUserIdAndFavoriteTrueOrderBySortOrderAscIdAsc(userId).stream()
				.map(CharacterResponse::from)
				.toList();
	}

	@Transactional
	public CharacterResponse toggleFavorite(Long userId, Long characterId) {
		MapleCharacter character = findCharacter(userId, characterId);
		character.toggleFavorite();
		return CharacterResponse.from(character);
	}

	@Transactional
	public CharacterResponse updateSortOrder(Long userId, Long characterId, int sortOrder) {
		MapleCharacter character = findCharacter(userId, characterId);
		character.updateSortOrder(sortOrder);
		return CharacterResponse.from(character);
	}

	@Transactional
	public List<CharacterResponse> syncCharacters(Long userId) {
		User user = findUser(userId);
		List<NexonCharacterSummary> summaries = nexonOpenApiClient.getCharacters(userId);
		return characterSyncService.syncCharacters(user, summaries).stream()
				.map(CharacterResponse::from)
				.toList();
	}

	private MapleCharacter findCharacter(Long userId, Long characterId) {
		return characterRepository.findByIdAndUserId(characterId, userId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CHARACTER_NOT_FOUND", "캐릭터 없음"));
	}

	private User findUser(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "사용자 없음"));
	}
}
