package com.maple.utility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.time.Clock;

import org.springframework.cache.CacheManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.maple.utility.dto.response.CharacterResponse;
import com.maple.utility.entity.MapleCharacter;
import com.maple.utility.entity.OAuthProvider;
import com.maple.utility.entity.User;
import com.maple.utility.exception.ApiException;
import com.maple.utility.repository.CharacterRepository;
import com.maple.utility.repository.UserRepository;
import com.maple.utility.security.NexonCharacterSummary;
import com.maple.utility.security.NexonOpenApiClient;

@ExtendWith(MockitoExtension.class)
class CharacterServiceTest {

	@Mock
	private CharacterRepository characterRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private NexonOpenApiClient nexonOpenApiClient;

	@Mock
	private CharacterSyncService characterSyncService;

	@Mock
	private CacheManager cacheManager;

	private CharacterService characterService;

	@BeforeEach
	void setUp() {
		characterService = new CharacterService(
				characterRepository,
				userRepository,
				nexonOpenApiClient,
				characterSyncService,
				cacheManager,
				Clock.systemDefaultZone()
		);
	}

	@Test
	void getCharactersReturnsUserCharacters() {
		User user = user();
		MapleCharacter character = character(user, 10L, "ocid", 1);
		when(characterRepository.findByUserIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(character));

		List<CharacterResponse> responses = characterService.getCharacters(1L);

		assertThat(responses).hasSize(1);
		assertThat(responses.get(0).id()).isEqualTo(10L);
		assertThat(responses.get(0).characterName()).isEqualTo("캐릭터");
	}

	@Test
	void getCharactersPlacesFavoritesFirstThenSortsOthersByLevel() {
		User user = user();
		MapleCharacter low = character(user, 10L, "low", 2);
		MapleCharacter high = character(user, 11L, "high", 1);
		MapleCharacter favorite = character(user, 12L, "favorite", 3);
		low.updateBasicInfo("낮은 레벨", "스카니아", "히어로", 250);
		high.updateBasicInfo("높은 레벨", "스카니아", "히어로", 280);
		favorite.toggleFavorite();
		when(characterRepository.findByUserIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(high, low, favorite));

		assertThat(characterService.getCharacters(1L))
				.extracting(CharacterResponse::id).containsExactly(12L, 11L, 10L);
	}

	@Test
	void updateFavoriteSortOrderPersistsCompleteOrder() {
		User user = user();
		MapleCharacter first = character(user, 10L, "first", 1);
		MapleCharacter second = character(user, 11L, "second", 2);
		first.toggleFavorite();
		second.toggleFavorite();
		when(characterRepository.findByUserIdAndFavoriteTrueOrderBySortOrderAscIdAsc(1L))
				.thenReturn(List.of(first, second));

		assertThat(characterService.updateFavoriteSortOrder(1L, List.of(11L, 10L)))
				.extracting(CharacterResponse::id).containsExactly(11L, 10L);
		assertThat(first.getSortOrder()).isEqualTo(2);
		assertThat(second.getSortOrder()).isEqualTo(1);
	}

	@Test
	void updateFavoriteSortOrderRejectsMissingCharacter() {
		MapleCharacter favorite = character(user(), 10L, "favorite", 1);
		favorite.toggleFavorite();
		when(characterRepository.findByUserIdAndFavoriteTrueOrderBySortOrderAscIdAsc(1L))
				.thenReturn(List.of(favorite));

		assertThatThrownBy(() -> characterService.updateFavoriteSortOrder(1L, List.of()))
				.isInstanceOfSatisfying(ApiException.class,
						exception -> assertThat(exception.getCode()).isEqualTo("INVALID_FAVORITE_ORDER"));
	}

	@Test
	void updateFavoriteSortOrderRejectsDuplicateCharacter() {
		MapleCharacter favorite = character(user(), 10L, "favorite", 1);
		favorite.toggleFavorite();
		when(characterRepository.findByUserIdAndFavoriteTrueOrderBySortOrderAscIdAsc(1L))
				.thenReturn(List.of(favorite));

		assertThatThrownBy(() -> characterService.updateFavoriteSortOrder(1L, List.of(10L, 10L)))
				.isInstanceOfSatisfying(ApiException.class,
						exception -> assertThat(exception.getCode()).isEqualTo("INVALID_FAVORITE_ORDER"));
	}

	@Test
	void updateFavoriteSortOrderRejectsOtherUserCharacter() {
		MapleCharacter favorite = character(user(), 10L, "favorite", 1);
		favorite.toggleFavorite();
		when(characterRepository.findByUserIdAndFavoriteTrueOrderBySortOrderAscIdAsc(1L))
				.thenReturn(List.of(favorite));

		assertThatThrownBy(() -> characterService.updateFavoriteSortOrder(1L, List.of(11L)))
				.isInstanceOfSatisfying(ApiException.class,
						exception -> assertThat(exception.getCode()).isEqualTo("INVALID_FAVORITE_ORDER"));
	}

	@Test
	void getFavoriteCharactersReturnsOnlyFavorites() {
		User user = user();
		MapleCharacter character = character(user, 10L, "ocid", 1);
		character.toggleFavorite();
		when(characterRepository.findByUserIdAndFavoriteTrueOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(character));

		List<CharacterResponse> responses = characterService.getFavoriteCharacters(1L);

		assertThat(responses).extracting(CharacterResponse::favorite).containsExactly(true);
	}

	@Test
	void toggleFavoriteChangesFavoriteState() {
		User user = user();
		MapleCharacter character = character(user, 10L, "ocid", 1);
		when(characterRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(character));

		CharacterResponse response = characterService.toggleFavorite(1L, 10L);

		assertThat(response.favorite()).isTrue();
	}

	@Test
	void toggleFavoriteAppendsAfterSavedFavorites() {
		User user = user();
		MapleCharacter existing = character(user, 10L, "existing", 5);
		MapleCharacter added = character(user, 11L, "added", 1);
		existing.toggleFavorite();
		when(characterRepository.findByIdAndUserId(11L, 1L)).thenReturn(Optional.of(added));
		when(characterRepository.findByUserIdAndFavoriteTrueOrderBySortOrderAscIdAsc(1L))
				.thenReturn(List.of(existing));

		CharacterResponse response = characterService.toggleFavorite(1L, 11L);

		assertThat(response.favorite()).isTrue();
		assertThat(response.sortOrder()).isEqualTo(6);
	}

	@Test
	void updateSortOrderChangesSortOrder() {
		User user = user();
		MapleCharacter character = character(user, 10L, "ocid", 1);
		when(characterRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(character));

		CharacterResponse response = characterService.updateSortOrder(1L, 10L, 5);

		assertThat(response.sortOrder()).isEqualTo(5);
	}

	@Test
	void updateSortOrderRejectsOtherUserCharacter() {
		when(characterRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> characterService.updateSortOrder(1L, 10L, 5))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("캐릭터 없음");
	}

	@Test
	void syncCharactersUsesStoredApiKeyAndReturnsSyncedCharacters() {
		User user = user();
		MapleCharacter character = character(user, 10L, "ocid", 1);
		List<NexonCharacterSummary> summaries = List.of(new NexonCharacterSummary("ocid", "캐릭터", "스카니아", "히어로", 280));
		when(userRepository.findById(1L)).thenReturn(Optional.of(user));
		when(nexonOpenApiClient.getCharacters(1L)).thenReturn(summaries);
		when(characterSyncService.syncCharacters(user, summaries)).thenReturn(List.of(character));

		List<CharacterResponse> responses = characterService.syncCharacters(1L);

		assertThat(responses).extracting(CharacterResponse::ocid).containsExactly("ocid");
		verify(characterSyncService).syncCharacters(user, summaries);
	}

	private MapleCharacter character(User user, Long id, String ocid, int sortOrder) {
		MapleCharacter character = MapleCharacter.create(user, ocid, "캐릭터", "스카니아", "히어로", 280, sortOrder);
		ReflectionTestUtils.setField(character, "id", id);
		character.updateDetails("캐릭터", "스카니아", "히어로", 280, "image-url", "길드");
		return character;
	}

	private User user() {
		User user = User.create(OAuthProvider.KAKAO, "oauth-id", "user@example.com", "nickname");
		ReflectionTestUtils.setField(user, "id", 1L);
		return user;
	}
}
