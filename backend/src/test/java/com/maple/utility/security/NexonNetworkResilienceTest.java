package com.maple.utility.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.ConnectException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maple.utility.config.NexonProperties;
import com.maple.utility.config.RedisPolicyProperties;
import com.maple.utility.config.RestClientConfig;
import com.maple.utility.entity.DataSyncLog;
import com.maple.utility.entity.OAuthProvider;
import com.maple.utility.entity.SyncStatus;
import com.maple.utility.entity.User;
import com.maple.utility.exception.ApiException;
import com.maple.utility.repository.DataSyncLogRepository;
import com.maple.utility.repository.UserApiKeyRepository;
import com.maple.utility.repository.UserRepository;
import com.maple.utility.util.NexonApiCallCounter;
import io.netty.handler.timeout.ReadTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

@SuppressWarnings("unchecked")
class NexonNetworkResilienceTest {

	private static final String URI_VALUE = "https://example.test/scheduler";
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-04T11:54:13Z"), ZoneId.of("Asia/Seoul"));
	private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
	private final ValueOperations<String, String> values = mock(ValueOperations.class);
	private final UserRepository users = mock(UserRepository.class);
	private final UserApiKeyRepository keys = mock(UserApiKeyRepository.class);
	private final DataSyncLogRepository logs = mock(DataSyncLogRepository.class);
	private final NexonApiCallCounter counter = mock(NexonApiCallCounter.class);
	private final AtomicReference<DataSyncLog> saved = new AtomicReference<>();
	private final List<Long> sleeps = new ArrayList<>();

	@BeforeEach
	void setUp() {
		when(redis.opsForValue()).thenReturn(values);
		when(users.findById(1L)).thenReturn(Optional.of(User.create(OAuthProvider.KAKAO, "id", null, "테스트")));
		when(counter.reserve(anyString(), anyString(), anyLong())).thenReturn(true);
		when(logs.save(any(DataSyncLog.class))).thenAnswer(invocation -> {
			DataSyncLog value = invocation.getArgument(0);
			saved.set(value);
			return value;
		});
	}

	@AfterEach
	void clearInterruptedFlag() {
		Thread.interrupted();
	}

	@Test
	void connectionFailureRetriesThenCachesSuccessAndCountsBothAttempts() {
		AtomicInteger outbound = new AtomicInteger();
		WebClient client = WebClient.builder().exchangeFunction(request -> outbound.incrementAndGet() == 1
				? Mono.error(requestFailure(new ConnectException("connection refused")))
				: Mono.just(response(HttpStatus.OK))).build();

		assertThat(gateway(client, sleeps::add).get(1L, "key", URI_VALUE, NexonRequestMode.REALTIME).isObject()).isTrue();

		assertThat(outbound).hasValue(2);
		assertThat(sleeps).containsExactly(100L);
		assertThat(saved.get().getStatus()).isEqualTo(SyncStatus.COMPLETED);
		assertThat(saved.get().getApiCallsUsed()).isEqualTo(2);
		verify(counter, times(2)).reserve(eq("1"), startsWith("api-key:"), eq(450L));
		verify(values).set(anyString(), eq("{}"), eq(Duration.ofMinutes(5)));
	}

	@Test
	void exhaustedConnectionsStopAfterThreeAttemptsAndRecordFailure() {
		AtomicInteger outbound = new AtomicInteger();
		WebClient client = WebClient.builder().exchangeFunction(request -> {
			outbound.incrementAndGet();
			return Mono.error(requestFailure(new ConnectException("connection refused")));
		}).build();

		assertCode(() -> gateway(client, sleeps::add).get(1L, "key", URI_VALUE, NexonRequestMode.BATCH), "NEXON_NETWORK_ERROR");

		assertThat(outbound).hasValue(3);
		assertThat(sleeps).containsExactly(100L, 200L);
		assertThat(saved.get().getStatus()).isEqualTo(SyncStatus.FAILED);
		assertThat(saved.get().getApiCallsUsed()).isEqualTo(3);
		verify(counter, times(3)).reserve(eq("1"), startsWith("api-key:"), eq(400L));
		verify(values, org.mockito.Mockito.never()).set(anyString(), anyString(), any(Duration.class));
	}

	@Test
	void httpAndNetworkFailuresShareOneAttemptLimitAndBackoffSequence() {
		AtomicInteger outbound = new AtomicInteger();
		WebClient client = WebClient.builder().exchangeFunction(request -> switch (outbound.incrementAndGet()) {
			case 1 -> Mono.just(response(HttpStatus.SERVICE_UNAVAILABLE));
			case 2 -> Mono.error(requestFailure(new ConnectException("connection refused")));
			default -> Mono.just(response(HttpStatus.TOO_MANY_REQUESTS));
		}).build();

		assertCode(() -> gateway(client, sleeps::add).get(1L, "key", URI_VALUE, NexonRequestMode.REALTIME), "NEXON_RATE_LIMIT");

		assertThat(outbound).hasValue(3);
		assertThat(sleeps).containsExactly(100L, 200L);
		assertThat(saved.get().getApiCallsUsed()).isEqualTo(3);
	}

	@Test
	void nestedBodyReadTimeoutIsRetriedWithoutAnExtraRequestExceptionWrapper() {
		AtomicInteger outbound = new AtomicInteger();
		WebClient client = WebClient.builder().exchangeFunction(request -> {
			outbound.incrementAndGet();
			return Mono.just(ClientResponse.create(HttpStatus.OK)
					.body(reactor.core.publisher.Flux.error(new IllegalStateException(ReadTimeoutException.INSTANCE))).build());
		}).build();

		assertCode(() -> gateway(client, sleeps::add).get(1L, "key", URI_VALUE, NexonRequestMode.REALTIME), "NEXON_NETWORK_ERROR");

		assertThat(outbound).hasValue(3);
		assertThat(saved.get().getApiCallsUsed()).isEqualTo(3);
	}

	@Test
	void malformedKeyIsNotRetriedAsNetworkFailure() {
		AtomicInteger outbound = new AtomicInteger();
		WebClient client = WebClient.builder().exchangeFunction(request -> {
			outbound.incrementAndGet();
			return Mono.error(requestFailure(new IllegalArgumentException("invalid header")));
		}).build();

		assertCode(() -> gateway(client, sleeps::add).get(1L, "key", URI_VALUE, NexonRequestMode.REALTIME), "API_KEY_INVALID_FORMAT");

		assertThat(outbound).hasValue(1);
		assertThat(sleeps).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(ints = {400, 401, 403})
	void authenticationAndParameterResponsesAreNotRetried(int status) {
		AtomicInteger outbound = new AtomicInteger();
		WebClient client = WebClient.builder().exchangeFunction(request -> {
			outbound.incrementAndGet();
			return Mono.just(response(HttpStatus.valueOf(status)));
		}).build();

		assertThatThrownBy(() -> gateway(client, sleeps::add).get(1L, "key", URI_VALUE, NexonRequestMode.REALTIME))
				.isInstanceOf(ApiException.class);

		assertThat(outbound).hasValue(1);
		assertThat(sleeps).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(ints = {401, 403})
	void authenticationStatusTakesPrecedenceOverTimeoutWhileReadingItsErrorBody(int status) {
		AtomicInteger outbound = new AtomicInteger();
		WebClient client = WebClient.builder().exchangeFunction(request -> {
			outbound.incrementAndGet();
			return Mono.just(ClientResponse.create(HttpStatus.valueOf(status))
					.body(reactor.core.publisher.Flux.error(ReadTimeoutException.INSTANCE)).build());
		}).build();

		assertCode(() -> gateway(client, sleeps::add).get(1L, "key", URI_VALUE, NexonRequestMode.REALTIME),
				status == 401 ? "API_KEY_INVALID" : "NEXON_ACCESS_DENIED");

		assertThat(outbound).hasValue(1);
		assertThat(sleeps).isEmpty();
		if (status == 401) {
			verify(keys).findByKeyFingerprint(ApiKeyFingerprint.of("key"));
		}
	}

	@Test
	void networkRetryCannotCrossReservedBudgetBoundary() {
		AtomicInteger outbound = new AtomicInteger();
		WebClient client = WebClient.builder().exchangeFunction(request -> {
			outbound.incrementAndGet();
			return Mono.error(requestFailure(new ConnectException("connection refused")));
		}).build();
		when(counter.reserve(anyString(), anyString(), anyLong())).thenReturn(true, false);

		assertCode(() -> gateway(client, sleeps::add).get(1L, "key", URI_VALUE, NexonRequestMode.REALTIME), "NEXON_REALTIME_LIMIT_REACHED");

		assertThat(outbound).hasValue(1);
		assertThat(saved.get().getApiCallsUsed()).isEqualTo(1);
		verify(counter, times(2)).reserve(eq("1"), startsWith("api-key:"), eq(450L));
	}

	@Test
	void interruptionDuringBackoffStopsRetriesAndPreservesInterruptFlag() {
		AtomicInteger outbound = new AtomicInteger();
		WebClient client = WebClient.builder().exchangeFunction(request -> {
			outbound.incrementAndGet();
			return Mono.error(requestFailure(new ConnectException("connection refused")));
		}).build();

		assertCode(() -> gateway(client, millis -> { throw new InterruptedException("cancelled"); })
				.get(1L, "key", URI_VALUE, NexonRequestMode.REALTIME), "NEXON_RETRY_INTERRUPTED");

		assertThat(outbound).hasValue(1);
		assertThat(Thread.currentThread().isInterrupted()).isTrue();
		assertThat(saved.get().getApiCallsUsed()).isEqualTo(1);
	}

	@Test
	void alreadyInterruptedThreadDoesNotReserveOrDispatch() {
		AtomicInteger outbound = new AtomicInteger();
		WebClient client = WebClient.builder().exchangeFunction(request -> {
			outbound.incrementAndGet();
			return Mono.just(response(HttpStatus.OK));
		}).build();
		Thread.currentThread().interrupt();

		assertCode(() -> gateway(client, sleeps::add).get(1L, "key", URI_VALUE, NexonRequestMode.REALTIME), "NEXON_RETRY_INTERRUPTED");

		assertThat(outbound).hasValue(0);
		assertThat(saved.get().getApiCallsUsed()).isZero();
		verify(counter, org.mockito.Mockito.never()).reserve(anyString(), anyString(), anyLong());
	}

	@Test
	void interruptedRequestDoesNotEnterNetworkRetryLoop() {
		AtomicInteger outbound = new AtomicInteger();
		WebClient client = WebClient.builder().exchangeFunction(request -> {
			outbound.incrementAndGet();
			return Mono.error(requestFailure(new InterruptedException("cancelled")));
		}).build();

		assertCode(() -> gateway(client, sleeps::add).get(1L, "key", URI_VALUE, NexonRequestMode.REALTIME), "NEXON_RETRY_INTERRUPTED");

		assertThat(outbound).hasValue(1);
		assertThat(Thread.currentThread().isInterrupted()).isTrue();
		assertThat(sleeps).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void configuredHttpClientTimesOutDelayedHeadersOrBodyAndStopsAfterThreeAttempts(boolean sendHeadersFirst) {
		AtomicInteger outbound = new AtomicInteger();
		DisposableServer server = HttpServer.create().host("127.0.0.1").port(0).handle((request, response) -> {
			outbound.incrementAndGet();
			Mono<Void> delayed = Mono.delay(Duration.ofMillis(500))
					.then(response.sendString(Mono.just("{}")).then());
			return sendHeadersFirst ? response.sendHeaders().then(delayed) : delayed;
		}).bindNow();
		try {
			WebClient client = new RestClientConfig().nexonWebClient(properties(Duration.ofMillis(100)));

			assertCode(() -> gateway(client, sleeps::add).get(1L, "key", "http://127.0.0.1:" + server.port() + "/scheduler",
					NexonRequestMode.REALTIME), "NEXON_NETWORK_ERROR");

			assertThat(outbound).hasValue(3);
			assertThat(saved.get().getApiCallsUsed()).isEqualTo(3);
			assertThat(sleeps).containsExactly(100L, 200L);
		} finally {
			server.disposeNow();
		}
	}

	private NexonApiGateway gateway(WebClient client, NexonApiGateway.Sleeper sleeper) {
		return new NexonApiGateway(client, new ObjectMapper(), redis,
				new RedisPolicyProperties(new RedisPolicyProperties.Cache(Duration.ofMinutes(5), Duration.ofHours(1),
						Duration.ofHours(24), Duration.ofMinutes(5)), new RedisPolicyProperties.RefreshToken(Duration.ofDays(7))),
				properties(Duration.ofSeconds(10)), users, keys, logs,
				mock(ApiKeyCryptoService.class), counter, CLOCK, sleeper);
	}

	private NexonProperties properties(Duration responseTimeout) {
		return new NexonProperties("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", URI_VALUE, URI_VALUE, URI_VALUE,
				500, 450, Duration.ZERO, Duration.ofSeconds(3), responseTimeout);
	}

	private ClientResponse response(HttpStatus status) {
		return ClientResponse.create(status).body("{}").build();
	}

	private WebClientRequestException requestFailure(Throwable cause) {
		return new WebClientRequestException(cause, HttpMethod.GET, URI.create(URI_VALUE), HttpHeaders.EMPTY);
	}

	private void assertCode(Runnable call, String code) {
		assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class,
				exception -> assertThat(exception.getCode()).isEqualTo(code));
	}
}
