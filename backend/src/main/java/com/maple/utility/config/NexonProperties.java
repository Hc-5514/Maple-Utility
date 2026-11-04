package com.maple.utility.config;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "nexon")
public record NexonProperties(
		@NotBlank String apiKeySecret,
		@NotBlank String characterListUri,
		@NotBlank String characterBasicUri,
		@NotBlank String characterSchedulerUri,
		@Positive int dailyCallLimit,
		@Positive int realtimeCallThreshold,
		@NotNull Duration dispatchInterval,
		@NotNull Duration connectTimeout,
		@NotNull Duration responseTimeout
) {
	public NexonProperties {
		requirePositiveMillis(connectTimeout, "nexon.connect-timeout");
		requirePositiveMillis(responseTimeout, "nexon.response-timeout");
		if (connectTimeout.compareTo(Duration.ofMillis(Integer.MAX_VALUE)) > 0) {
			throw new IllegalArgumentException("nexon.connect-timeout must fit in integer milliseconds");
		}
	}

	private static void requirePositiveMillis(Duration timeout, String name) {
		if (timeout == null || timeout.compareTo(Duration.ofMillis(1)) < 0) {
			throw new IllegalArgumentException(name + " must be at least 1ms");
		}
	}
}
