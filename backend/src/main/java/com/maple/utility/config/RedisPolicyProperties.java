package com.maple.utility.config;

import java.time.Duration;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.redis")
public record RedisPolicyProperties(
		@NotNull Cache cache,
		@NotNull RefreshToken refreshToken
) {

	public record Cache(
			@NotNull Duration schedulerTtl,
			@NotNull Duration characterBasicTtl,
			@NotNull Duration staticDataTtl
	) {
	}

	public record RefreshToken(
			@NotNull Duration ttl
	) {
	}
}
