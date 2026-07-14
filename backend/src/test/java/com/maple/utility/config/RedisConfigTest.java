package com.maple.utility.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

class RedisConfigTest {

	private final RedisConfig redisConfig = new RedisConfig();
	private final RedisPolicyProperties properties = new RedisPolicyProperties(
			new RedisPolicyProperties.Cache(
					Duration.ofMinutes(5),
					Duration.ofHours(1),
					Duration.ofHours(24),
					Duration.ofMinutes(5)
			),
			new RedisPolicyProperties.RefreshToken(Duration.ofDays(7))
	);

	@Test
	void redisTemplateUsesConfiguredConnectionFactory() {
		RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);

		RedisTemplate<String, Object> redisTemplate = redisConfig.redisTemplate(connectionFactory);

		assertThat(redisTemplate.getConnectionFactory()).isSameAs(connectionFactory);
		assertThat(redisTemplate.getKeySerializer()).isNotNull();
		assertThat(redisTemplate.getValueSerializer()).isNotNull();
		assertThat(redisTemplate.getHashKeySerializer()).isNotNull();
		assertThat(redisTemplate.getHashValueSerializer()).isNotNull();
	}

	@Test
	void cacheManagerCreatesPolicyCaches() {
		RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);

		RedisCacheManager cacheManager = redisConfig.cacheManager(connectionFactory, properties);

		assertThat(cacheManager.getCache(RedisCacheNames.SCHEDULER)).isNotNull();
		assertThat(cacheManager.getCache(RedisCacheNames.CHARACTER_BASIC)).isNotNull();
		assertThat(cacheManager.getCache(RedisCacheNames.STATIC_DATA)).isNotNull();
		assertThat(cacheManager.getCache(RedisCacheNames.NEXON_API)).isNotNull();
	}
}
