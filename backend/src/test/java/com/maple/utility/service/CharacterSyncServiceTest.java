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
import com.maple.utility.security.NexonCharacterSummary;

@ExtendWith(MockitoExtension.class)
class CharacterSyncServiceTest {

	@Mock
	private CharacterRepository characterRepository;

	@Test
	void syncCharactersCreatesNewCharacter() {
		CharacterSyncService service = new CharacterSyncService(characterRepository);
		User user = user();
		when(characterRepository.findByUserIdAndOcid(1L, "ocid")).thenReturn(Optional.empty());
		when(characterRepository.save(any(MapleCharacter.class))).thenAnswer(invocation -> invocation.getArgument(0));

		service.syncCharacters(user, List.of(new NexonCharacterSummary("ocid", "캐릭터", "스카니아", "히어로", 280)));

		ArgumentCaptor<MapleCharacter> captor = ArgumentCaptor.forClass(MapleCharacter.class);
		verify(characterRepository).save(captor.capture());
		MapleCharacter savedCharacter = captor.getValue();
		assertThat(savedCharacter.getOcid()).isEqualTo("ocid");
		assertThat(savedCharacter.getCharacterName()).isEqualTo("캐릭터");
		assertThat(savedCharacter.getSortOrder()).isEqualTo(1);
	}

	@Test
	void syncCharactersUpdatesExistingCharacter() {
		CharacterSyncService service = new CharacterSyncService(characterRepository);
		User user = user();
		MapleCharacter existingCharacter = MapleCharacter.create(user, "ocid", "이전", "리부트", "팔라딘", 270, 3);
		when(characterRepository.findByUserIdAndOcid(1L, "ocid")).thenReturn(Optional.of(existingCharacter));

		service.syncCharacters(user, List.of(new NexonCharacterSummary("ocid", "캐릭터", "스카니아", "히어로", 280)));

		assertThat(existingCharacter.getCharacterName()).isEqualTo("캐릭터");
		assertThat(existingCharacter.getWorldName()).isEqualTo("스카니아");
		assertThat(existingCharacter.getCharacterClass()).isEqualTo("히어로");
		assertThat(existingCharacter.getCharacterLevel()).isEqualTo(280);
		assertThat(existingCharacter.getSortOrder()).isEqualTo(1);
	}

	private User user() {
		User user = User.create(OAuthProvider.KAKAO, "oauth-id", "user@example.com", "nickname");
		ReflectionTestUtils.setField(user, "id", 1L);
		return user;
	}
}
