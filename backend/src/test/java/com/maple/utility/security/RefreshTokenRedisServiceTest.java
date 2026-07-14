package com.maple.utility.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.maple.utility.config.RedisPolicyProperties;

@SuppressWarnings("unchecked")
class RefreshTokenRedisServiceTest {

	private final StringRedisTemplate redisTemplate = org.mockito.Mockito.mock(StringRedisTemplate.class);
	private final ValueOperations<String, String> valueOperations = org.mockito.Mockito.mock(ValueOperations.class);
	private final RedisPolicyProperties properties = new RedisPolicyProperties(
			new RedisPolicyProperties.Cache(
					Duration.ofMinutes(5),
					Duration.ofHours(1),
					Duration.ofHours(24),
					Duration.ofMinutes(5)
			),
			new RedisPolicyProperties.RefreshToken(Duration.ofDays(7))
	);
	private final RefreshTokenRedisService service = new RefreshTokenRedisService(redisTemplate, properties);

	@Test
	void saveStoresRefreshTokenWithDefaultTtl() {
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);

		service.save(1L, "token-id", "refresh-token");

		verify(valueOperations).set("jwt:refresh-token:1:token-id", "refresh-token", Duration.ofDays(7));
	}

	@Test
	void findReturnsStoredRefreshToken() {
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.get("jwt:refresh-token:1:token-id")).thenReturn("refresh-token");

		Optional<String> refreshToken = service.find(1L, "token-id");

		assertThat(refreshToken).contains("refresh-token");
	}

	@Test
	void deleteRemovesRefreshToken() {
		service.delete(1L, "token-id");

		verify(redisTemplate).delete("jwt:refresh-token:1:token-id");
	}
}
