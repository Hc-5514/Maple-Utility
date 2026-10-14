package com.maple.utility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.maple.utility.config.RedisCacheNames;
import com.maple.utility.dto.request.CharacterPreferencesRequest.Preference;
import com.maple.utility.dto.response.CharacterResponse;
import com.maple.utility.entity.MapleCharacter;
import com.maple.utility.entity.OAuthProvider;
import com.maple.utility.entity.User;
import com.maple.utility.exception.ApiException;
import com.maple.utility.repository.CharacterRepository;
import com.maple.utility.repository.UserRepository;
import com.maple.utility.security.NexonOpenApiClient;

@ExtendWith(MockitoExtension.class)
class CharacterPreferencesServiceTest {
	@Mock CharacterRepository characters;
	@Mock UserRepository users;
	@Mock NexonOpenApiClient nexon;
	@Mock CharacterSyncService sync;
	@Mock CacheManager cacheManager;
	@Mock Cache cache;
	private CharacterService service;
	private User user;

	@BeforeEach
	void setUp() {
		service = new CharacterService(characters, users, nexon, sync, cacheManager, Clock.systemUTC());
		user = User.create(OAuthProvider.KAKAO, "oauth-id", "user@example.com", "user");
		ReflectionTestUtils.setField(user, "id", 1L);
	}

	@Test
	void savesFavoritesAndGlobalOrderOnlyAfterLockAndCompleteValidation() {
		MapleCharacter first = character(10L, 4);
		MapleCharacter second = character(11L, 7);
		first.toggleFavorite();
		when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
		when(characters.findByUserIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(first, second));

		assertThat(service.updatePreferences(1L, List.of(new Preference(11L, true), new Preference(10L, false))))
				.extracting(CharacterResponse::id).containsExactly(11L, 10L);
		assertThat(first.isFavorite()).isFalse();
		assertThat(first.getSortOrder()).isEqualTo(2);
		assertThat(second.isFavorite()).isTrue();
		assertThat(second.getSortOrder()).isEqualTo(1);
		var order = inOrder(users, characters);
		order.verify(users).findByIdForUpdate(1L);
		order.verify(characters).findByUserIdOrderBySortOrderAscIdAsc(1L);
	}

	@Test
	void malformedAndDuplicateItemsFailWithoutReadingOrChangingState() {
		List<List<Preference>> invalid = Arrays.asList(null, Arrays.asList((Preference) null),
				List.of(new Preference(null, true)), List.of(new Preference(0L, true)),
				List.of(new Preference(10L, null)), List.of(new Preference(10L, true), new Preference(10L, false)));
		for (List<Preference> payload : invalid) {
			assertThatThrownBy(() -> service.updatePreferences(1L, payload)).isInstanceOfSatisfying(ApiException.class,
					error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
		}
		verifyNoInteractions(users, characters, cacheManager);
	}

	@Test
	void foreignIdDoesNotPartiallyApplyEarlierValidItem() {
		MapleCharacter first = character(10L, 4);
		when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
		when(characters.findByUserIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(first));
		assertThatThrownBy(() -> service.updatePreferences(1L,
				List.of(new Preference(10L, true), new Preference(99L, false))))
				.isInstanceOfSatisfying(ApiException.class,
						error -> assertThat(error.getCode()).isEqualTo("INVALID_CHARACTER_PREFERENCES"));
		assertThat(first.isFavorite()).isFalse();
		assertThat(first.getSortOrder()).isEqualTo(4);
		verifyNoInteractions(cacheManager);
	}

	@Test
	void changedMembershipReturnsConflictWithoutPartialSave() {
		MapleCharacter first = character(10L, 4);
		when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
		when(characters.findByUserIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(first, character(11L, 7)));
		assertThatThrownBy(() -> service.updatePreferences(1L, List.of(new Preference(10L, true))))
				.isInstanceOfSatisfying(ApiException.class, error -> {
					assertThat(error.getStatus()).isEqualTo(HttpStatus.CONFLICT);
					assertThat(error.getCode()).isEqualTo("CHARACTER_LIST_CHANGED");
				});
		assertThat(first.isFavorite()).isFalse();
		assertThat(first.getSortOrder()).isEqualTo(4);
		verifyNoInteractions(cacheManager);
	}

	@Test
	void emptyOwnedListCanBeSaved() {
		when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
		when(characters.findByUserIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of());
		assertThat(service.updatePreferences(1L, List.of())).isEmpty();
	}

	@Test
	void savesExactlyEighteenFavoritesAcrossWorlds() {
		List<MapleCharacter> owned = ownedCharacters(19);
		when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
		when(characters.findByUserIdOrderBySortOrderAscIdAsc(1L)).thenReturn(owned);
		List<Preference> preferences = owned.stream()
				.map(item -> new Preference(item.getId(), item.getId() < 28L)).toList();
		assertThat(service.updatePreferences(1L, preferences)).filteredOn(CharacterResponse::favorite).hasSize(18);
	}

	@Test
	void overLimitSaveDoesNotChangeFavoritesOrderOrCache() {
		List<MapleCharacter> owned = ownedCharacters(19);
		when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
		when(characters.findByUserIdOrderBySortOrderAscIdAsc(1L)).thenReturn(owned);
		List<Preference> preferences = owned.reversed().stream()
				.map(item -> new Preference(item.getId(), true)).toList();
		assertThatThrownBy(() -> service.updatePreferences(1L, preferences))
				.isInstanceOfSatisfying(ApiException.class, error -> {
					assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
					assertThat(error.getCode()).isEqualTo("FAVORITE_LIMIT_EXCEEDED");
				});
		assertThat(owned).noneMatch(MapleCharacter::isFavorite);
		assertThat(owned).extracting(MapleCharacter::getSortOrder)
				.containsExactlyElementsOf(IntStream.range(0, 19).map(index -> index + 4).boxed().toList());
		verifyNoInteractions(cacheManager);
	}

	@Test
	void existingOverLimitFavoritesMustBeReducedBeforeSaving() {
		List<MapleCharacter> owned = ownedCharacters(20);
		owned.forEach(MapleCharacter::toggleFavorite);
		when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
		when(characters.findByUserIdOrderBySortOrderAscIdAsc(1L)).thenReturn(owned);
		assertThatThrownBy(() -> service.updatePreferences(1L, owned.stream()
				.map(item -> new Preference(item.getId(), item.getId() < 29L)).toList()))
				.isInstanceOfSatisfying(ApiException.class,
						error -> assertThat(error.getCode()).isEqualTo("FAVORITE_LIMIT_EXCEEDED"));
		assertThat(owned).allMatch(MapleCharacter::isFavorite);
		assertThat(service.updatePreferences(1L, owned.stream()
				.map(item -> new Preference(item.getId(), item.getId() < 28L)).toList()))
				.filteredOn(CharacterResponse::favorite).hasSize(18);
	}

	private List<MapleCharacter> ownedCharacters(int count) {
		return IntStream.range(0, count).mapToObj(index -> {
			MapleCharacter item = character(10L + index, index + 4);
			item.updateBasicInfo("캐릭터" + index, index % 2 == 0 ? "루나" : "베라", "히어로", 280);
			return item;
		}).toList();
	}

	@Test
	void schedulerEvictionOccursOnlyAfterCommit() {
		when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
		when(characters.findByUserIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of());
		when(cacheManager.getCache(RedisCacheNames.SCHEDULER)).thenReturn(cache);
		TransactionSynchronizationManager.initSynchronization();
		try {
			service.updatePreferences(1L, List.of());
			verifyNoInteractions(cache);
			TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
			verify(cache).clear();
		} finally {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	private MapleCharacter character(Long id, int order) {
		MapleCharacter result = MapleCharacter.create(user, "ocid-" + id, "캐릭터", "루나", "히어로", 280, order);
		ReflectionTestUtils.setField(result, "id", id);
		return result;
	}
}
