package com.maple.utility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import com.maple.utility.dto.response.ApiKeyStatusResponse;
import com.maple.utility.entity.ApiKeyStatus;
import com.maple.utility.entity.OAuthProvider;
import com.maple.utility.entity.User;
import com.maple.utility.entity.UserApiKey;
import com.maple.utility.exception.ApiException;
import com.maple.utility.repository.UserApiKeyRepository;
import com.maple.utility.repository.UserRepository;
import com.maple.utility.security.ApiKeyCryptoService;
import com.maple.utility.security.NexonCharacterSummary;
import com.maple.utility.security.NexonOpenApiClient;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

	private static final Clock CLOCK = Clock.fixed(
			Instant.parse("2026-07-14T12:00:00Z"),
			ZoneId.of("Asia/Seoul")
	);

	@Mock
	private UserRepository userRepository;

	@Mock
	private UserApiKeyRepository userApiKeyRepository;

	@Mock
	private NexonOpenApiClient nexonOpenApiClient;

	@Mock
	private ApiKeyCryptoService apiKeyCryptoService;

	@Mock
	private CharacterSyncService characterSyncService;

	private ApiKeyService apiKeyService;

	@BeforeEach
	void setUp() {
		apiKeyService = new ApiKeyService(
				userRepository,
				userApiKeyRepository,
				nexonOpenApiClient,
				apiKeyCryptoService,
				characterSyncService,
				CLOCK
		);
	}

	@Test
	void registerCreatesEncryptedApiKeyAndSyncsCharacters() {
		User user = user();
		UserApiKey savedApiKey = UserApiKey.create(user, "encrypted-api-key", null);
		ReflectionTestUtils.setField(savedApiKey, "id", 10L);
		List<NexonCharacterSummary> characters = List.of(
				new NexonCharacterSummary("ocid", "캐릭터", "스카니아", "히어로", 280)
		);

		when(userRepository.findById(1L)).thenReturn(Optional.of(user));
		when(nexonOpenApiClient.getCharacters("plain-api-key")).thenReturn(characters);
		when(apiKeyCryptoService.encrypt("plain-api-key")).thenReturn("encrypted-api-key");
		when(userApiKeyRepository.findByUserId(1L)).thenReturn(Optional.empty());
		when(userApiKeyRepository.save(any(UserApiKey.class))).thenReturn(savedApiKey);

		ApiKeyStatusResponse response = apiKeyService.register(1L, "plain-api-key");

		assertThat(response.registered()).isTrue();
		assertThat(response.keyStatus()).isEqualTo(ApiKeyStatus.ACTIVE);
		verify(characterSyncService).syncCharacters(user, characters);
	}

	@Test
	void registerReplacesExistingApiKey() {
		User user = user();
		UserApiKey existingApiKey = UserApiKey.create(user, "old-encrypted-api-key", null);
		List<NexonCharacterSummary> characters = List.of();

		when(userRepository.findById(1L)).thenReturn(Optional.of(user));
		when(nexonOpenApiClient.getCharacters("plain-api-key")).thenReturn(characters);
		when(apiKeyCryptoService.encrypt("plain-api-key")).thenReturn("new-encrypted-api-key");
		when(userApiKeyRepository.findByUserId(1L)).thenReturn(Optional.of(existingApiKey));

		ApiKeyStatusResponse response = apiKeyService.register(1L, "plain-api-key");

		assertThat(existingApiKey.getEncryptedKey()).isEqualTo("new-encrypted-api-key");
		assertThat(response.keyStatus()).isEqualTo(ApiKeyStatus.ACTIVE);
	}

	@Test
	void registerInvalidApiKeyMarksExistingKeyInvalid() {
		User user = user();
		UserApiKey existingApiKey = UserApiKey.create(user, "old-encrypted-api-key", null);

		when(userRepository.findById(1L)).thenReturn(Optional.of(user));
		when(nexonOpenApiClient.getCharacters("invalid-api-key"))
				.thenThrow(new ApiException(HttpStatus.UNAUTHORIZED, "API_KEY_INVALID", "유효하지 않은 Nexon API Key"));
		when(userApiKeyRepository.findByUserId(1L)).thenReturn(Optional.of(existingApiKey));

		assertThatThrownBy(() -> apiKeyService.register(1L, "invalid-api-key"))
				.isInstanceOf(ApiException.class);
		assertThat(existingApiKey.getKeyStatus()).isEqualTo(ApiKeyStatus.INVALID);
	}

	@Test
	void statusReturnsUnregisteredWhenApiKeyDoesNotExist() {
		when(userApiKeyRepository.findByUserId(1L)).thenReturn(Optional.empty());

		ApiKeyStatusResponse response = apiKeyService.status(1L);

		assertThat(response.registered()).isFalse();
		assertThat(response.keyStatus()).isNull();
		assertThat(response.lastVerifiedAt()).isNull();
	}

	@Test
	void deleteRemovesApiKeyByUserId() {
		apiKeyService.delete(1L);

		verify(userApiKeyRepository).deleteByUserId(1L);
	}

	private User user() {
		User user = User.create(OAuthProvider.KAKAO, "oauth-id", "user@example.com", "nickname");
		ReflectionTestUtils.setField(user, "id", 1L);
		return user;
	}
}
