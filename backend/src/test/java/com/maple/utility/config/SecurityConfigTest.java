package com.maple.utility.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

class SecurityConfigTest {

	@Test
	void corsConfigurationAllowsConfiguredOrigins() {
		SecurityConfig securityConfig = new SecurityConfig();
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/characters");

		CorsConfiguration configuration = securityConfig
				.corsConfigurationSource("https://app.example.com, http://localhost:5173")
				.getCorsConfiguration(request);

		assertThat(configuration).isNotNull();
		assertThat(configuration.getAllowedOrigins())
				.containsExactly("https://app.example.com", "http://localhost:5173");
		assertThat(configuration.getAllowedMethods()).contains("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
		assertThat(configuration.getAllowedHeaders()).contains("Authorization", "Content-Type");
		assertThat(configuration.getAllowCredentials()).isTrue();
	}
}
