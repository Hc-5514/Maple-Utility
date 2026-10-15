package com.maple.utility.service;

import java.util.List;
import java.util.HashSet;
import java.time.Clock;

import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.maple.utility.dto.response.CharacterResponse;
import com.maple.utility.dto.request.CharacterPreferencesRequest.Preference;
import com.maple.utility.config.RedisCacheNames;
import com.maple.utility.entity.MapleCharacter;
import com.maple.utility.entity.User;
import com.maple.utility.exception.ApiException;
import com.maple.utility.repository.CharacterRepository;
import com.maple.utility.repository.UserRepository;
import com.maple.utility.security.NexonCharacterSummary;
import com.maple.utility.security.NexonOpenApiClient;

@Service
public class CharacterService {

	private static final int MAX_FAVORITES = 18;

	private final CharacterRepository characterRepository;
	private final UserRepository userRepository;
	private final NexonOpenApiClient nexonOpenApiClient;
	private final CharacterSyncService characterSyncService;
	private final CacheManager cacheManager;
	private final Clock clock;
	private final DataPreferenceSourceService preferenceSources;

	public CharacterService(
			CharacterRepository characterRepository,
			UserRepository userRepository,
			NexonOpenApiClient nexonOpenApiClient,
			CharacterSyncService characterSyncService,
			CacheManager cacheManager,
			Clock clock
	) {
		this(characterRepository, userRepository, nexonOpenApiClient, characterSyncService, cacheManager, clock, null);
	}

	@org.springframework.beans.factory.annotation.Autowired
	public CharacterService(CharacterRepository characterRepository, UserRepository userRepository,
			NexonOpenApiClient nexonOpenApiClient, CharacterSyncService characterSyncService,
			CacheManager cacheManager, Clock clock, DataPreferenceSourceService preferenceSources) {
		this.preferenceSources = preferenceSources;
		this.characterRepository = characterRepository;
		this.userRepository = userRepository;
		this.nexonOpenApiClient = nexonOpenApiClient;
		this.characterSyncService = characterSyncService;
		this.cacheManager = cacheManager;
		this.clock = clock;
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
		return toggleFavorite(userId, userId, characterId);
	}

	@Transactional
	public CharacterResponse toggleFavorite(Long userId, Long identityUserId, Long characterId) {
		lockUser(userId);
		MapleCharacter character = findCharacter(userId, characterId);
		if (!character.isFavorite() && characterRepository.countByUserIdAndFavoriteTrue(userId) >= MAX_FAVORITES) {
			throw favoriteLimitExceeded();
		}
		character.toggleFavorite();
		recordPreference(DataPreferenceSourceService.FAVORITE, characterId, identityUserId);
		evictSchedulerAfterCommit();
		return CharacterResponse.from(character);
	}

	@Transactional
	public List<CharacterResponse> updateFavoriteSortOrder(Long userId, List<Long> characterIds) {
		return updateFavoriteSortOrder(userId, userId, characterIds);
	}

	@Transactional
	public List<CharacterResponse> updateFavoriteSortOrder(Long userId, Long identityUserId, List<Long> characterIds) {
		lockUser(userId);
		List<MapleCharacter> favorites = characterRepository.findByUserIdAndFavoriteTrueOrderBySortOrderAscIdAsc(userId);
		if (characterIds == null || characterIds.stream().anyMatch(id -> id == null || id <= 0)
				|| new HashSet<>(characterIds).size() != characterIds.size()
				|| !new HashSet<>(characterIds).equals(favorites.stream().map(MapleCharacter::getId).collect(java.util.stream.Collectors.toSet()))) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FAVORITE_ORDER", "즐겨찾기 순서 오류");
		}
		List<Integer> slots = favorites.stream().map(MapleCharacter::getSortOrder).sorted().toList();
		var byId = favorites.stream().collect(java.util.stream.Collectors.toMap(MapleCharacter::getId, character -> character));
		for (int index = 0; index < characterIds.size(); index++) {
			byId.get(characterIds.get(index)).updateSortOrder(slots.get(index));
		}
		evictSchedulerAfterCommit();
		recordPreference(DataPreferenceSourceService.SORT_ORDER, userId, identityUserId);
		return characterIds.stream().map(id -> CharacterResponse.from(byId.get(id))).toList();
	}

	@Transactional
	public CharacterResponse updateSortOrder(Long userId, Long characterId, int sortOrder) {
		return updateSortOrder(userId, userId, characterId, sortOrder);
	}

	@Transactional
	public CharacterResponse updateSortOrder(Long userId, Long identityUserId, Long characterId, int sortOrder) {
		lockUser(userId);
		MapleCharacter character = findCharacter(userId, characterId);
		character.updateSortOrder(sortOrder);
		recordPreference(DataPreferenceSourceService.SORT_ORDER, userId, identityUserId);
		evictSchedulerAfterCommit();
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

	@Transactional
	public List<CharacterResponse> updatePreferences(Long userId, List<Preference> preferences) {
		return updatePreferences(userId, userId, preferences);
	}

	@Transactional
	public List<CharacterResponse> updatePreferences(Long userId, Long identityUserId, List<Preference> preferences) {
		if (preferences == null || preferences.stream().anyMatch(item -> item == null
				|| item.characterId() == null || item.characterId() <= 0 || item.favorite() == null)
				|| preferences.stream().map(Preference::characterId).distinct().count() != preferences.size()) {
			throw invalidPreferences();
		}
		lockUser(userId);
		List<MapleCharacter> characters = characterRepository.findByUserIdOrderBySortOrderAscIdAsc(userId);
		var byId = characters.stream().collect(java.util.stream.Collectors.toMap(MapleCharacter::getId, character -> character));
		if (preferences.stream().anyMatch(item -> !byId.containsKey(item.characterId()))) {
			throw invalidPreferences();
		}
		if (preferences.size() != characters.size()) {
			throw new ApiException(HttpStatus.CONFLICT, "CHARACTER_LIST_CHANGED", "캐릭터 목록 변경, 새로고침 후 저장 필요");
		}
		if (preferences.stream().filter(Preference::favorite).count() > MAX_FAVORITES) {
			throw favoriteLimitExceeded();
		}
		for (int index = 0; index < preferences.size(); index++) {
			Preference preference = preferences.get(index);
			byId.get(preference.characterId()).updatePreferences(preference.favorite(), index + 1);
			recordPreference(DataPreferenceSourceService.FAVORITE, preference.characterId(), identityUserId);
		}
		evictSchedulerAfterCommit();
		recordPreference(DataPreferenceSourceService.SORT_ORDER, userId, identityUserId);
		return preferences.stream().map(item -> CharacterResponse.from(byId.get(item.characterId()))).toList();
	}

	private void recordPreference(String type, long recordId, long identityUserId) {
		if (preferenceSources != null) {
			preferenceSources.saveExplicit(type, recordId, identityUserId);
		}
	}

	private ApiException invalidPreferences() {
		return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CHARACTER_PREFERENCES", "캐릭터 설정 목록 오류");
	}

	private ApiException favoriteLimitExceeded() {
		return new ApiException(HttpStatus.BAD_REQUEST, "FAVORITE_LIMIT_EXCEEDED", "즐겨찾기는 전체 월드 합산 최대 18명까지 선택 가능");
	}

	private void lockUser(Long userId) {
		userRepository.findByIdForUpdate(userId)
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "사용자 없음"));
	}

	private void evictSchedulerAfterCommit() {
		Runnable evict = () -> {
			var cache = cacheManager.getCache(RedisCacheNames.SCHEDULER);
			if (cache != null) {
				cache.clear();
			}
		};
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					evict.run();
				}
			});
		} else {
			evict.run();
		}
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
