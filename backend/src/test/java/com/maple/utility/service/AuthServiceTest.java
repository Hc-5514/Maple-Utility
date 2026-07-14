package com.maple.utility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
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
import com.maple.utility.repository.UserRepository;
import com.maple.utility.security.JwtToken;
import com.maple.utility.security.JwtTokenProvider;
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
	private JwtTokenProvider jwtTokenProvider;

	@Mock
	private RefreshTokenRedisService refreshTokenRedisService;

	private AuthService authService;

	@BeforeEach
	void setUp() {
		when(kakaoOAuthClient.provider()).thenReturn(OAuthProvider.KAKAO);
		authService = new AuthService(
				List.of(kakaoOAuthClient),
				userRepository,
				jwtTokenProvider,
				refreshTokenRedisService
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
