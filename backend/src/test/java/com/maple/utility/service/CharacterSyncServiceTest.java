package com.maple.utility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.maple.utility.entity.MapleCharacter;
import com.maple.utility.entity.OAuthProvider;
import com.maple.utility.entity.User;
import com.maple.utility.repository.CharacterRepository;
import com.maple.utility.security.NexonCharacterBasic;
import com.maple.utility.security.NexonCharacterSummary;
import com.maple.utility.security.NexonOpenApiClient;

@ExtendWith(MockitoExtension.class)
class CharacterSyncServiceTest {

	@Mock
	private CharacterRepository characterRepository;

	@Mock
	private NexonOpenApiClient nexonOpenApiClient;

	@Test
	void syncCharactersCreatesNewCharacter() {
		CharacterSyncService service = new CharacterSyncService(characterRepository, nexonOpenApiClient);
		User user = user();
		when(characterRepository.findByUserIdAndOcid(1L, "ocid")).thenReturn(Optional.empty());
		when(characterRepository.save(any(MapleCharacter.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(characterRepository.findByUserIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of());
		when(nexonOpenApiClient.getCharacterBasic(1L, "plain-api-key", "ocid"))
				.thenReturn(new NexonCharacterBasic("ocid", "상세캐릭터", "스카니아", "히어로", 281, "image-url", "길드"));

		service.syncCharacters(
				user,
				List.of(new NexonCharacterSummary("ocid", "캐릭터", "스카니아", "히어로", 280)),
				"plain-api-key"
		);

		ArgumentCaptor<MapleCharacter> captor = ArgumentCaptor.forClass(MapleCharacter.class);
		verify(characterRepository).save(captor.capture());
		MapleCharacter savedCharacter = captor.getValue();
		assertThat(savedCharacter.getOcid()).isEqualTo("ocid");
		assertThat(savedCharacter.getCharacterName()).isEqualTo("상세캐릭터");
		assertThat(savedCharacter.getCharacterLevel()).isEqualTo(281);
		assertThat(savedCharacter.getCharacterImage()).isEqualTo("image-url");
		assertThat(savedCharacter.getGuildName()).isEqualTo("길드");
		assertThat(savedCharacter.getSortOrder()).isEqualTo(1);
	}

	@Test
	void syncCharactersUpdatesExistingCharacterAndKeepsFavorite() {
		CharacterSyncService service = new CharacterSyncService(characterRepository, nexonOpenApiClient);
		User user = user();
		MapleCharacter existingCharacter = MapleCharacter.create(user, "ocid", "이전", "리부트", "팔라딘", 270, 3);
		existingCharacter.toggleFavorite();
		when(characterRepository.findByUserIdAndOcid(1L, "ocid")).thenReturn(Optional.of(existingCharacter));
		when(characterRepository.findByUserIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(existingCharacter));
		when(nexonOpenApiClient.getCharacterBasic(1L, "ocid"))
				.thenReturn(new NexonCharacterBasic("ocid", "캐릭터", "스카니아", "히어로", 280, "image-url", "길드"));

		service.syncCharacters(user, List.of(new NexonCharacterSummary("ocid", "캐릭터", "스카니아", "히어로", 280)));

		assertThat(existingCharacter.getCharacterName()).isEqualTo("캐릭터");
		assertThat(existingCharacter.getWorldName()).isEqualTo("스카니아");
		assertThat(existingCharacter.getCharacterClass()).isEqualTo("히어로");
		assertThat(existingCharacter.getCharacterLevel()).isEqualTo(280);
		assertThat(existingCharacter.getCharacterImage()).isEqualTo("image-url");
		assertThat(existingCharacter.getGuildName()).isEqualTo("길드");
		assertThat(existingCharacter.isFavorite()).isTrue();
		assertThat(existingCharacter.getSortOrder()).isEqualTo(1);
	}

	private User user() {
		User user = User.create(OAuthProvider.KAKAO, "oauth-id", "user@example.com", "nickname");
		ReflectionTestUtils.setField(user, "id", 1L);
		return user;
	}
}
