package com.maple.utility.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.maple.utility.config.NexonProperties;

class ApiKeyCryptoServiceTest {

	private static final String SECRET = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

	@Test
	void encryptAndDecryptApiKey() {
		ApiKeyCryptoService service = new ApiKeyCryptoService(
				new NexonProperties(SECRET, "http://localhost/character/list", 500, 450, Duration.ofSeconds(1)),
				new SecureRandom()
		);

		String encrypted = service.encrypt("nexon-api-key");

		assertThat(encrypted).contains(":");
		assertThat(encrypted).doesNotContain("nexon-api-key");
		assertThat(service.decrypt(encrypted)).isEqualTo("nexon-api-key");
	}

	@Test
	void constructorRejectsInvalidSecretLength() {
		assertThatThrownBy(() -> new ApiKeyCryptoService(
				new NexonProperties("c2hvcnQ=", "http://localhost/character/list", 500, 450, Duration.ofSeconds(1)),
				new SecureRandom()
		)).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("NEXON_API_KEY_SECRET");
	}
}
