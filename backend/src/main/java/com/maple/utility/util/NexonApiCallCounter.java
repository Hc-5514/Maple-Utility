package com.maple.utility.util;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
public class NexonApiCallCounter {

	private static final String KEY_PREFIX = "nexon:api:daily";
	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

	private final StringRedisTemplate redisTemplate;
	private final Clock clock;

	public NexonApiCallCounter(StringRedisTemplate redisTemplate, Clock clock) {
		this.redisTemplate = redisTemplate;
		this.clock = clock;
	}

	public long increment(String subject) {
		String key = key(subject);
		Long count = redisTemplate.opsForValue().increment(key);
		redisTemplate.expire(key, durationUntilNextMidnight());

		return count == null ? 0L : count;
	}

	public long getCount(String subject) {
		String value = redisTemplate.opsForValue().get(key(subject));
		return value == null ? 0L : Long.parseLong(value);
	}

	public void reset(String subject) {
		redisTemplate.delete(key(subject));
	}

	public String key(String subject) {
		Assert.hasText(subject, "subject must not be blank");
		return KEY_PREFIX + ":" + LocalDate.now(clock).format(DATE_FORMATTER) + ":" + subject;
	}

	Duration durationUntilNextMidnight() {
		LocalDateTime now = LocalDateTime.now(clock);
		LocalDateTime nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay();
		return Duration.between(now, nextMidnight);
	}
}
