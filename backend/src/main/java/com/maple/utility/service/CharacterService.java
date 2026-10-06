package com.maple.utility.service;

import java.util.List;
import java.util.Comparator;
import java.util.HashSet;
import java.time.Clock;
import java.time.LocalDate;

import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.maple.utility.dto.response.CharacterResponse;
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

	private final CharacterRepository characterRepository;
	private final UserRepository userRepository;
	private final NexonOpenApiClient nexonOpenApiClient;
	private final CharacterSyncService characterSyncService;
	private final CacheManager cacheManager;
	private final Clock clock;

	public CharacterService(
			CharacterRepository characterRepository,
			UserRepository userRepository,
			NexonOpenApiClient nexonOpenApiClient,
			CharacterSyncService characterSyncService,
			CacheManager cacheManager,
			Clock clock
	) {
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
				.sorted((left, right) -> {
					if (left.isFavorite() != right.isFavorite()) {
						return left.isFavorite() ? -1 : 1;
					}
					if (left.isFavorite()) {
						int order = Integer.compare(left.getSortOrder(), right.getSortOrder());
						return order != 0 ? order : left.getId().compareTo(right.getId());
					}
					int level = Comparator.nullsLast(Comparator.<Integer>reverseOrder())
							.compare(left.getCharacterLevel(), right.getCharacterLevel());
					return level != 0 ? level : left.getId().compareTo(right.getId());
				})
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
		if (!character.isFavorite()) {
			int lastOrder = characterRepository.findByUserIdAndFavoriteTrueOrderBySortOrderAscIdAsc(userId).stream()
					.mapToInt(MapleCharacter::getSortOrder).max().orElse(0);
			character.updateSortOrder(lastOrder + 1);
		}
		character.toggleFavorite();
		Runnable evictSummary = () -> {
			var cache = cacheManager.getCache(RedisCacheNames.SCHEDULER);
			if (cache != null) {
				cache.clear();
			}
		};
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				evictSummary.run();
			}
			});
		} else {
			evictSummary.run();
		}
		return CharacterResponse.from(character);
	}

	@Transactional
	public List<CharacterResponse> updateFavoriteSortOrder(Long userId, List<Long> characterIds) {
		List<MapleCharacter> favorites = characterRepository.findByUserIdAndFavoriteTrueOrderBySortOrderAscIdAsc(userId);
		if (characterIds == null || characterIds.stream().anyMatch(id -> id == null || id <= 0)
				|| new HashSet<>(characterIds).size() != characterIds.size()
				|| !new HashSet<>(characterIds).equals(favorites.stream().map(MapleCharacter::getId).collect(java.util.stream.Collectors.toSet()))) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FAVORITE_ORDER", "즐겨찾기 순서 오류");
		}
		var byId = favorites.stream().collect(java.util.stream.Collectors.toMap(MapleCharacter::getId, character -> character));
		for (int index = 0; index < characterIds.size(); index++) {
			byId.get(characterIds.get(index)).updateSortOrder(index + 1);
		}
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
		return characterIds.stream().map(id -> CharacterResponse.from(byId.get(id))).toList();
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
