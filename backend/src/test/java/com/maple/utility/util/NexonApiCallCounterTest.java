package com.maple.utility.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@SuppressWarnings("unchecked")
class NexonApiCallCounterTest {

	private static final Clock CLOCK = Clock.fixed(
			Instant.parse("2026-07-14T14:30:00Z"),
			ZoneId.of("Asia/Seoul")
	);

	private final StringRedisTemplate redisTemplate = org.mockito.Mockito.mock(StringRedisTemplate.class);
	private final ValueOperations<String, String> valueOperations = org.mockito.Mockito.mock(ValueOperations.class);
	private final NexonApiCallCounter counter = new NexonApiCallCounter(redisTemplate, CLOCK);

	@Test
	void incrementIncreasesDailyCounterAndSetsMidnightExpiration() {
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.increment("nexon:api:daily:20260714:user-1")).thenReturn(3L);

		long count = counter.increment("user-1");

		assertThat(count).isEqualTo(3L);
		verify(redisTemplate).expire("nexon:api:daily:20260714:user-1", Duration.ofMinutes(30));
	}

	@Test
	void getCountReturnsZeroWhenKeyDoesNotExist() {
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.get("nexon:api:daily:20260714:user-1")).thenReturn(null);

		long count = counter.getCount("user-1");

		assertThat(count).isZero();
	}

	@Test
	void resetDeletesDailyCounter() {
		counter.reset("user-1");

		verify(redisTemplate).delete("nexon:api:daily:20260714:user-1");
	}
}
