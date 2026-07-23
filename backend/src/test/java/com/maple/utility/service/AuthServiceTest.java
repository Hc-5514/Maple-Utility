package com.maple.utility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.maple.utility.dto.response.AuthResponse;
import com.maple.utility.entity.OAuthProvider;
import com.maple.utility.entity.User;
import com.maple.utility.entity.UserApiKey;
import com.maple.utility.exception.ApiException;
import com.maple.utility.repository.UserApiKeyRepository;
import com.maple.utility.repository.UserRepository;
import com.maple.utility.security.ApiKeyCryptoService;
import com.maple.utility.security.JwtToken;
import com.maple.utility.security.JwtTokenProvider;
import com.maple.utility.security.NexonCharacterSummary;
import com.maple.utility.security.NexonOpenApiClient;
import com.maple.utility.security.OAuthClient;
import com.maple.utility.security.OAuthUserInfo;
import com.maple.utility.security.RefreshTokenRedisService;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	@Mock
	private OAuthClient kakaoOAuthClient;

	@Mock
	private UserRepository userRepository;

	@Mock
	private UserApiKeyRepository userApiKeyRepository;

	@Mock
	private JwtTokenProvider jwtTokenProvider;

	@Mock
	private RefreshTokenRedisService refreshTokenRedisService;

	@Mock
	private NexonOpenApiClient nexonOpenApiClient;

	@Mock
	private ApiKeyCryptoService apiKeyCryptoService;

	private AuthService authService;

	@BeforeEach
	void setUp() {
		when(kakaoOAuthClient.provider()).thenReturn(OAuthProvider.KAKAO);
		authService = new AuthService(
				List.of(kakaoOAuthClient),
				userRepository,
				jwtTokenProvider,
				refreshTokenRedisService,
				userApiKeyRepository,
				nexonOpenApiClient,
				apiKeyCryptoService,
				Clock.fixed(Instant.parse("2026-07-23T00:00:00Z"), ZoneId.of("Asia/Seoul"))
		);
	}

	@Test
	void loginCreatesUserAndSavesRefreshToken() {
		OAuthUserInfo userInfo = new OAuthUserInfo(OAuthProvider.KAKAO, "oauth-id", "user@example.com", "nickname");
		User savedUser = User.create(OAuthProvider.KAKAO, "oauth-id", "user@example.com", "nickname");
		ReflectionTestUtils.setField(savedUser, "id", 1L);
		JwtToken accessToken = new JwtToken("access-token", "access-token-id", Duration.ofMinutes(30));
		JwtToken refreshToken = new JwtToken("refresh-token", "refresh-token-id", Duration.ofDays(7));

		when(kakaoOAuthClient.getUserInfo("authorization-code")).thenReturn(userInfo);
		when(userRepository.findByOauthProviderAndOauthId(OAuthProvider.KAKAO, "oauth-id")).thenReturn(Optional.empty());
		when(userRepository.save(any(User.class))).thenReturn(savedUser);
		when(jwtTokenProvider.createAccessToken(1L)).thenReturn(accessToken);
		when(jwtTokenProvider.createRefreshToken(1L)).thenReturn(refreshToken);

		AuthService.LoginResult result = authService.login(OAuthProvider.KAKAO, "authorization-code");

		assertThat(result.response().accessToken()).isEqualTo("access-token");
		assertThat(result.response().expiresIn()).isEqualTo(1800);
		assertThat(result.response().user().isNewUser()).isTrue();
		assertThat(result.refreshToken()).isEqualTo(refreshToken);
		verify(refreshTokenRedisService).save(1L, "refresh-token-id", "refresh-token", Duration.ofDays(7));
	}

	@Test
	void loginWithNexonApiKeyCreatesUserAndStoresEncryptedKey() {
		String oauthId = "aec3e83011b517c7a1fd8a8f6fcb457997a1a43c2009ecb3b54c17199cdb2621";
		User savedUser = User.create(OAuthProvider.NEXON_APIKEY, oauthId, null, null);
		ReflectionTestUtils.setField(savedUser, "id", 1L);
		UserApiKey savedApiKey = UserApiKey.create(savedUser, "encrypted-api-key", null);
		JwtToken accessToken = new JwtToken("access-token", "access-token-id", Duration.ofMinutes(30));
		JwtToken refreshToken = new JwtToken("refresh-token", "refresh-token-id", Duration.ofDays(7));

		when(userRepository.findByOauthProviderAndOauthId(OAuthProvider.NEXON_APIKEY, oauthId))
				.thenReturn(Optional.empty());
		when(userRepository.save(any(User.class))).thenReturn(savedUser);
		when(nexonOpenApiClient.getCharacters(1L, "plain-api-key"))
				.thenReturn(List.of(new NexonCharacterSummary("ocid", "캐릭터", "스카니아", "비숍", 280)));
		when(apiKeyCryptoService.encrypt("plain-api-key")).thenReturn("encrypted-api-key");
		when(userApiKeyRepository.findByUserId(1L)).thenReturn(Optional.empty());
		when(userApiKeyRepository.save(any(UserApiKey.class))).thenReturn(savedApiKey);
		when(jwtTokenProvider.createAccessToken(1L)).thenReturn(accessToken);
		when(jwtTokenProvider.createRefreshToken(1L)).thenReturn(refreshToken);

		AuthService.LoginResult result = authService.loginWithNexonApiKey("plain-api-key");

		assertThat(result.response().accessToken()).isEqualTo("access-token");
		assertThat(result.response().user().nickname()).isEqualTo("캐릭터");
		assertThat(result.response().user().isNewUser()).isTrue();
		verify(userApiKeyRepository).save(any(UserApiKey.class));
		verify(refreshTokenRedisService).save(1L, "refresh-token-id", "refresh-token", Duration.ofDays(7));
	}

	@Test
	void loginWithNexonApiKeyReplacesExistingEncryptedKey() {
		String oauthId = "aec3e83011b517c7a1fd8a8f6fcb457997a1a43c2009ecb3b54c17199cdb2621";
		User user = User.create(OAuthProvider.NEXON_APIKEY, oauthId, null, "기존캐릭터");
		ReflectionTestUtils.setField(user, "id", 1L);
		UserApiKey existingApiKey = UserApiKey.create(user, "old-encrypted-api-key", null);
		JwtToken accessToken = new JwtToken("access-token", "access-token-id", Duration.ofMinutes(30));
		JwtToken refreshToken = new JwtToken("refresh-token", "refresh-token-id", Duration.ofDays(7));

		when(userRepository.findByOauthProviderAndOauthId(OAuthProvider.NEXON_APIKEY, oauthId))
				.thenReturn(Optional.of(user));
		when(nexonOpenApiClient.getCharacters(1L, "plain-api-key")).thenReturn(List.of());
		when(apiKeyCryptoService.encrypt("plain-api-key")).thenReturn("new-encrypted-api-key");
		when(userApiKeyRepository.findByUserId(1L)).thenReturn(Optional.of(existingApiKey));
		when(jwtTokenProvider.createAccessToken(1L)).thenReturn(accessToken);
		when(jwtTokenProvider.createRefreshToken(1L)).thenReturn(refreshToken);

		AuthService.LoginResult result = authService.loginWithNexonApiKey("plain-api-key");

		assertThat(result.response().user().isNewUser()).isFalse();
		assertThat(existingApiKey.getEncryptedKey()).isEqualTo("new-encrypted-api-key");
		verify(userApiKeyRepository, never()).save(any(UserApiKey.class));
	}

	@Test
	void loginWithNexonApiKeyPropagatesInvalidApiKey() {
		String oauthId = "aec3e83011b517c7a1fd8a8f6fcb457997a1a43c2009ecb3b54c17199cdb2621";
		User savedUser = User.create(OAuthProvider.NEXON_APIKEY, oauthId, null, null);
		ReflectionTestUtils.setField(savedUser, "id", 1L);

		when(userRepository.findByOauthProviderAndOauthId(OAuthProvider.NEXON_APIKEY, oauthId))
				.thenReturn(Optional.empty());
		when(userRepository.save(any(User.class))).thenReturn(savedUser);
		when(nexonOpenApiClient.getCharacters(1L, "plain-api-key"))
				.thenThrow(new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "API_KEY_INVALID", "유효하지 않은 Nexon API Key"));

		assertThatThrownBy(() -> authService.loginWithNexonApiKey("plain-api-key"))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("유효하지 않은 Nexon API Key");
		verify(userApiKeyRepository, never()).save(any(UserApiKey.class));
	}

	@Test
	void refreshValidatesStoredRefreshTokenAndCreatesAccessToken() {
		User user = User.create(OAuthProvider.KAKAO, "oauth-id", "user@example.com", "nickname");
		ReflectionTestUtils.setField(user, "id", 1L);
		JwtToken accessToken = new JwtToken("new-access-token", "access-token-id", Duration.ofMinutes(30));

		when(jwtTokenProvider.parseRefreshToken("refresh-token"))
				.thenReturn(new JwtTokenProvider.RefreshTokenClaims(1L, "refresh-token-id"));
		when(refreshTokenRedisService.find(1L, "refresh-token-id")).thenReturn(Optional.of("refresh-token"));
		when(userRepository.findById(1L)).thenReturn(Optional.of(user));
		when(jwtTokenProvider.createAccessToken(1L)).thenReturn(accessToken);

		AuthResponse response = authService.refresh("refresh-token");

		assertThat(response.accessToken()).isEqualTo("new-access-token");
		assertThat(response.user().id()).isEqualTo(1L);
	}

	@Test
	void logoutDeletesStoredRefreshToken() {
		when(jwtTokenProvider.parseRefreshToken("refresh-token"))
				.thenReturn(new JwtTokenProvider.RefreshTokenClaims(1L, "refresh-token-id"));

		authService.logout("refresh-token");

		verify(refreshTokenRedisService).delete(1L, "refresh-token-id");
	}
}
