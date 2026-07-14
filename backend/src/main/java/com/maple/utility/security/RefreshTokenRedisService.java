package com.maple.utility.security;

import java.time.Duration;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import com.maple.utility.config.RedisPolicyProperties;

@Service
public class RefreshTokenRedisService {

	private static final String KEY_PREFIX = "jwt:refresh-token";

	private final StringRedisTemplate redisTemplate;
	private final RedisPolicyProperties properties;

	public RefreshTokenRedisService(StringRedisTemplate redisTemplate, RedisPolicyProperties properties) {
		this.redisTemplate = redisTemplate;
		this.properties = properties;
	}

	public void save(Long userId, String tokenId, String refreshToken) {
		save(userId, tokenId, refreshToken, properties.refreshToken().ttl());
	}

	public void save(Long userId, String tokenId, String refreshToken, Duration ttl) {
		Assert.notNull(userId, "userId must not be null");
		Assert.hasText(tokenId, "tokenId must not be blank");
		Assert.hasText(refreshToken, "refreshToken must not be blank");
		Assert.notNull(ttl, "ttl must not be null");

		redisTemplate.opsForValue().set(key(userId, tokenId), refreshToken, ttl);
	}

	public Optional<String> find(Long userId, String tokenId) {
		Assert.notNull(userId, "userId must not be null");
		Assert.hasText(tokenId, "tokenId must not be blank");

		return Optional.ofNullable(redisTemplate.opsForValue().get(key(userId, tokenId)));
	}

	public void delete(Long userId, String tokenId) {
		Assert.notNull(userId, "userId must not be null");
		Assert.hasText(tokenId, "tokenId must not be blank");

		redisTemplate.delete(key(userId, tokenId));
	}

	public String key(Long userId, String tokenId) {
		return KEY_PREFIX + ":" + userId + ":" + tokenId;
	}
}
