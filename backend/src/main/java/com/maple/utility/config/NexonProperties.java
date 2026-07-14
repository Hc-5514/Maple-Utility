package com.maple.utility.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "nexon")
public record NexonProperties(
		@NotBlank String apiKeySecret,
		@NotBlank String characterListUri
) {
}
