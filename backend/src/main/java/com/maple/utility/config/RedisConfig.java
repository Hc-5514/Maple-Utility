package com.maple.utility.config;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Map;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@EnableCaching
public class RedisConfig {

	@Bean
	public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
		RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
		StringRedisSerializer stringSerializer = new StringRedisSerializer();
		JdkSerializationRedisSerializer valueSerializer = new JdkSerializationRedisSerializer();

		redisTemplate.setConnectionFactory(connectionFactory);
		redisTemplate.setKeySerializer(stringSerializer);
		redisTemplate.setHashKeySerializer(stringSerializer);
		redisTemplate.setValueSerializer(valueSerializer);
		redisTemplate.setHashValueSerializer(valueSerializer);
		redisTemplate.afterPropertiesSet();

		return redisTemplate;
	}

	@Bean
	public RedisCacheManager cacheManager(
			RedisConnectionFactory connectionFactory,
			RedisPolicyProperties properties
	) {
		RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
				.disableCachingNullValues()
				.serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()))
				.serializeValuesWith(SerializationPair.fromSerializer(new JdkSerializationRedisSerializer()));

		Map<String, RedisCacheConfiguration> cacheConfigurations = Map.of(
				RedisCacheNames.SCHEDULER,
				defaultConfig.entryTtl(properties.cache().schedulerTtl()),
				RedisCacheNames.CHARACTER_BASIC,
				defaultConfig.entryTtl(properties.cache().characterBasicTtl()),
				RedisCacheNames.STATIC_DATA,
				defaultConfig.entryTtl(properties.cache().staticDataTtl()),
				RedisCacheNames.NEXON_API,
				defaultConfig.entryTtl(properties.cache().nexonApiTtl())
		);

		return RedisCacheManager.builder(connectionFactory)
				.cacheDefaults(defaultConfig)
				.withInitialCacheConfigurations(cacheConfigurations)
				.build();
	}

	@Bean
	public Clock seoulClock() {
		return Clock.system(ZoneId.of("Asia/Seoul"));
	}
}
