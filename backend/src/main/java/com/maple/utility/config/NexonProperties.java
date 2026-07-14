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
		@NotNull Duration dispatchInterval
) {
}
