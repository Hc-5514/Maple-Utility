package com.maple.utility.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "oauth")
public record OAuthProperties(
		@Valid Provider kakao,
		@Valid Provider nexon
) {

	public record Provider(
			@NotBlank String clientId,
			@NotBlank String clientSecret,
			@NotBlank String redirectUri,
			@NotBlank String tokenUri,
			@NotBlank String userInfoUri
	) {
	}
}
