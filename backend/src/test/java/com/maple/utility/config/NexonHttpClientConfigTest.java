package com.maple.utility.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.stream.Stream;

import io.netty.channel.ChannelOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.netty.http.client.HttpClient;

class NexonHttpClientConfigTest {

	@Test
	void nexonClientUsesExplicitTimeoutsAndDisablesUncountedNettyRetries() {
		var client = new RestClientConfig().nexonWebClient(properties(Duration.ofMillis(1234), Duration.ofMillis(5678)));
		// Inspect the actual connector used by WebClient; a black-holed TCP peer is OS-dependent.
		Object exchange = ReflectionTestUtils.getField(client, "exchangeFunction");
		Object connector = ReflectionTestUtils.getField(exchange, "connector");
		HttpClient transport = (HttpClient) ReflectionTestUtils.getField(connector, "httpClient");

		assertThat(transport).isNotNull();
		assertThat(transport.configuration().options().get(ChannelOption.CONNECT_TIMEOUT_MILLIS)).isEqualTo(1234);
		assertThat(transport.configuration().responseTimeout()).isEqualTo(Duration.ofMillis(5678));
		assertThat(transport.configuration().isRetryDisabled()).isTrue();
	}

	@ParameterizedTest
	@MethodSource("invalidTimeouts")
	void connectTimeoutMustBeAtLeastOneMillisecond(Duration timeout) {
		assertThatThrownBy(() -> properties(timeout, Duration.ofSeconds(10)))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nexon.connect-timeout");
	}

	@ParameterizedTest
	@MethodSource("invalidTimeouts")
	void responseTimeoutMustBeAtLeastOneMillisecond(Duration timeout) {
		assertThatThrownBy(() -> properties(Duration.ofSeconds(3), timeout))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nexon.response-timeout");
	}

	@Test
	void connectTimeoutCannotOverflowNettyIntegerOption() {
		assertThatThrownBy(() -> properties(Duration.ofMillis(Integer.MAX_VALUE).plusMillis(1), Duration.ofSeconds(10)))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("integer milliseconds");
	}

	private static Stream<Duration> invalidTimeouts() {
		return Stream.of(null, Duration.ZERO, Duration.ofMillis(-1), Duration.ofNanos(999_999));
	}

	private NexonProperties properties(Duration connect, Duration response) {
		return new NexonProperties("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", "https://example.test/list",
				"https://example.test/basic", "https://example.test/scheduler", 500, 450, Duration.ofSeconds(1), connect, response);
	}
}
