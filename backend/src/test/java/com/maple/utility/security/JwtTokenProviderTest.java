package com.maple.utility.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.maple.utility.config.JwtProperties;
import com.maple.utility.exception.ApiException;

class JwtTokenProviderTest {

	private static final Clock CLOCK = Clock.fixed(
			Instant.parse("2026-07-14T00:00:00Z"),
			ZoneId.of("Asia/Seoul")
	);

	private final JwtTokenProvider tokenProvider = new JwtTokenProvider(
			new JwtProperties("test-jwt-secret", Duration.ofMinutes(30), Duration.ofDays(7)),
			CLOCK
	);

	@Test
	void createAccessTokenParsesUserId() {
		JwtToken token = tokenProvider.createAccessToken(1L);

		JwtAuthentication authentication = tokenProvider.parseAccessToken(token.value());

		assertThat(authentication.userId()).isEqualTo(1L);
		assertThat(token.tokenId()).isNotBlank();
		assertThat(token.ttl()).isEqualTo(Duration.ofMinutes(30));
	}

	@Test
	void createRefreshTokenParsesClaims() {
		JwtToken token = tokenProvider.createRefreshToken(2L);

		JwtTokenProvider.RefreshTokenClaims claims = tokenProvider.parseRefreshToken(token.value());

		assertThat(claims.userId()).isEqualTo(2L);
		assertThat(claims.tokenId()).isEqualTo(token.tokenId());
		assertThat(token.ttl()).isEqualTo(Duration.ofDays(7));
	}

	@Test
	void parseAccessTokenRejectsRefreshToken() {
		JwtToken token = tokenProvider.createRefreshToken(1L);

		assertThatThrownBy(() -> tokenProvider.parseAccessToken(token.value()))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
					assertThat(exception.getCode()).isEqualTo("INVALID_TOKEN_TYPE");
				});
	}
}
