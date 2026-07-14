package com.maple.utility.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.maple.utility.config.JwtProperties;
import com.maple.utility.exception.ApiException;

@Component
public class JwtTokenProvider {

	private static final String TOKEN_TYPE_CLAIM = "token_type";
	private static final String HMAC_ALGORITHM = "HmacSHA256";

	private final JwtProperties properties;
	private final Clock clock;
	private final SecretKey secretKey;

	public JwtTokenProvider(JwtProperties properties, Clock clock) {
		this.properties = properties;
		this.clock = clock;
		this.secretKey = createSecretKey(properties.secret());
	}

	public JwtToken createAccessToken(Long userId) {
		return createToken(userId, UUID.randomUUID().toString(), TokenType.ACCESS, properties.accessTokenTtl());
	}

	public JwtToken createRefreshToken(Long userId) {
		return createToken(userId, UUID.randomUUID().toString(), TokenType.REFRESH, properties.refreshTokenTtl());
	}

	public JwtAuthentication parseAccessToken(String token) {
		Claims claims = parseClaims(token);
		validateTokenType(claims, TokenType.ACCESS);
		return new JwtAuthentication(Long.valueOf(claims.getSubject()));
	}

	public RefreshTokenClaims parseRefreshToken(String token) {
		Claims claims = parseClaims(token);
		validateTokenType(claims, TokenType.REFRESH);
		return new RefreshTokenClaims(Long.valueOf(claims.getSubject()), claims.getId());
	}

	public Duration accessTokenTtl() {
		return properties.accessTokenTtl();
	}

	public Duration refreshTokenTtl() {
		return properties.refreshTokenTtl();
	}

	private JwtToken createToken(Long userId, String tokenId, TokenType tokenType, Duration ttl) {
		Instant now = Instant.now(clock);
		Instant expiresAt = now.plus(ttl);
		String token = Jwts.builder()
				.id(tokenId)
				.subject(String.valueOf(userId))
				.claim(TOKEN_TYPE_CLAIM, tokenType.name())
				.issuedAt(Date.from(now))
				.expiration(Date.from(expiresAt))
				.signWith(secretKey)
				.compact();

		return new JwtToken(token, tokenId, ttl);
	}

	private Claims parseClaims(String token) {
		try {
			return Jwts.parser()
					.verifyWith(secretKey)
					.clock(() -> Date.from(Instant.now(clock)))
					.build()
					.parseSignedClaims(token)
					.getPayload();
		} catch (JwtException | IllegalArgumentException | DateTimeException exception) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "유효하지 않은 토큰");
		}
	}

	private void validateTokenType(Claims claims, TokenType expectedType) {
		String actualType = claims.get(TOKEN_TYPE_CLAIM, String.class);
		if (!expectedType.name().equals(actualType)) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN_TYPE", "토큰 유형 불일치");
		}
	}

	private SecretKey createSecretKey(String secret) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
			return new SecretKeySpec(digest, HMAC_ALGORITHM);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 algorithm not available", exception);
		}
	}

	public record RefreshTokenClaims(
			Long userId,
			String tokenId
	) {
	}
}
