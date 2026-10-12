package com.maple.utility.util;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
public class NexonApiCallCounter {

	private static final String KEY_PREFIX = "nexon:api:daily";
	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
	private static final DefaultRedisScript<Long> RESERVE = new DefaultRedisScript<>("""
			local count = tonumber(redis.call('GET', KEYS[1]) or '0')
			if count >= tonumber(ARGV[1]) then return 0 end
			redis.call('INCR', KEYS[1])
			redis.call('PEXPIRE', KEYS[1], ARGV[2])
			return 1
			""", Long.class);

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

	/** Reserve immediately before every outbound attempt, including retries. */
	public boolean reserve(String subject, long limit) {
		Assert.isTrue(limit > 0, "limit must be positive");
		Long result = redisTemplate.execute(RESERVE, java.util.List.of(key(subject)),
				Long.toString(limit), Long.toString(Math.max(1, durationUntilNextMidnight().toMillis())));
		if (result == null) {
			throw new IllegalStateException("Nexon API budget reservation unavailable");
		}
		return result == 1L;
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
