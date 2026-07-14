package com.maple.utility.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

import com.maple.utility.config.NexonProperties;
import com.maple.utility.config.RedisPolicyProperties;
import com.maple.utility.entity.DataSyncLog;
import com.maple.utility.entity.OAuthProvider;
import com.maple.utility.entity.User;
import com.maple.utility.entity.UserApiKey;
import com.maple.utility.exception.ApiException;
import com.maple.utility.repository.DataSyncLogRepository;
import com.maple.utility.repository.UserApiKeyRepository;
import com.maple.utility.repository.UserRepository;
import com.maple.utility.util.NexonApiCallCounter;
import reactor.core.publisher.Mono;

@SuppressWarnings("unchecked")
class NexonApiGatewayTest {

	private static final Clock CLOCK = Clock.fixed(
			Instant.parse("2026-07-14T12:00:00Z"),
			ZoneId.of("Asia/Seoul")
	);

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final StringRedisTemplate redisTemplate = org.mockito.Mockito.mock(StringRedisTemplate.class);
	private final ValueOperations<String, String> valueOperations = org.mockito.Mockito.mock(ValueOperations.class);
	private final UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);
	private final UserApiKeyRepository userApiKeyRepository = org.mockito.Mockito.mock(UserApiKeyRepository.class);
	private final DataSyncLogRepository dataSyncLogRepository = org.mockito.Mockito.mock(DataSyncLogRepository.class);
	private final ApiKeyCryptoService apiKeyCryptoService = org.mockito.Mockito.mock(ApiKeyCryptoService.class);
	private final NexonApiCallCounter apiCallCounter = org.mockito.Mockito.mock(NexonApiCallCounter.class);
	private final RedisPolicyProperties redisPolicyProperties = new RedisPolicyProperties(
			new RedisPolicyProperties.Cache(
					Duration.ofMinutes(5),
					Duration.ofHours(1),
					Duration.ofHours(24),
					Duration.ofMinutes(5)
			),
			new RedisPolicyProperties.RefreshToken(Duration.ofDays(7))
	);
	private final NexonProperties nexonProperties = new NexonProperties(
			"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
			"https://example.test/character/list",
			"https://example.test/character/basic",
			"https://example.test/character/scheduler",
			500,
			450,
			Duration.ofSeconds(1)
	);

	@Test
	void getUsesDecryptedStoredApiKeyHeaderAndCachesResponse() {
		AtomicReference<ClientRequest> capturedRequest = new AtomicReference<>();
		WebClient webClient = WebClient.builder()
				.exchangeFunction(request -> {
					capturedRequest.set(request);
					return Mono.just(ClientResponse.create(HttpStatus.OK)
							.header(HttpHeaders.CONTENT_TYPE, "application/json")
							.body("{\"ok\":true}")
							.build());
				})
				.build();
		NexonApiGateway gateway = gateway(webClient, millis -> {
		});
		User user = user();
		UserApiKey userApiKey = UserApiKey.create(user, "encrypted-api-key", null);

		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.get(org.mockito.ArgumentMatchers.startsWith("nexon:api:cache:1:GET:"))).thenReturn(null);
		when(userApiKeyRepository.findByUserId(1L)).thenReturn(Optional.of(userApiKey));
		when(apiKeyCryptoService.decrypt("encrypted-api-key")).thenReturn("plain-api-key");
		when(apiCallCounter.getCount("1")).thenReturn(0L);
		when(userRepository.findById(1L)).thenReturn(Optional.of(user));
		when(dataSyncLogRepository.save(org.mockito.ArgumentMatchers.any(DataSyncLog.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		when(apiCallCounter.increment("1")).thenReturn(1L);

		assertThat(gateway.getWithStoredKey(1L, "https://example.test/character/list", NexonRequestMode.REALTIME)
				.path("ok").asBoolean()).isTrue();

		assertThat(capturedRequest.get().headers().getFirst("x-nxopen-api-key")).isEqualTo("plain-api-key");
		verify(valueOperations).set(
				org.mockito.ArgumentMatchers.startsWith("nexon:api:cache:1:GET:"),
				org.mockito.ArgumentMatchers.eq("{\"ok\":true}"),
				org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(5))
		);
		verify(apiCallCounter).increment("1");
	}

	@Test
	void getReturnsCachedResponseWithoutApiCallCounter() {
		AtomicInteger calls = new AtomicInteger();
		WebClient webClient = WebClient.builder()
				.exchangeFunction(request -> {
					calls.incrementAndGet();
					return Mono.just(ClientResponse.create(HttpStatus.OK).body("{}").build());
				})
				.build();
		NexonApiGateway gateway = gateway(webClient, millis -> {
		});

		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.get(org.mockito.ArgumentMatchers.startsWith("nexon:api:cache:1:GET:")))
				.thenReturn("{\"cached\":true}");

		assertThat(gateway.get(1L, "plain-api-key", "https://example.test/character/list", NexonRequestMode.REALTIME)
				.path("cached").asBoolean()).isTrue();
		assertThat(calls).hasValue(0);
		verify(apiCallCounter, never()).increment("1");
	}

	@Test
	void getBlocksRealtimeCallsNearDailyLimit() {
		NexonApiGateway gateway = gateway(WebClient.builder().build(), millis -> {
		});

		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.get(org.mockito.ArgumentMatchers.anyString())).thenReturn(null);
		when(apiCallCounter.getCount("1")).thenReturn(450L);

		assertThatThrownBy(() -> gateway.get(1L, "plain-api-key", "https://example.test", NexonRequestMode.REALTIME))
				.isInstanceOfSatisfying(ApiException.class, exception ->
						assertThat(exception.getCode()).isEqualTo("NEXON_REALTIME_LIMIT_REACHED"));
	}

	@Test
	void getInvalidatesStoredKeyOnUnauthorizedResponse() {
		WebClient webClient = WebClient.builder()
				.exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.UNAUTHORIZED).body("{}").build()))
				.build();
		NexonApiGateway gateway = gateway(webClient, millis -> {
		});
		User user = user();
		UserApiKey userApiKey = UserApiKey.create(user, "encrypted-api-key", null);

		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.get(org.mockito.ArgumentMatchers.anyString())).thenReturn(null);
		when(apiCallCounter.getCount("1")).thenReturn(0L);
		when(userRepository.findById(1L)).thenReturn(Optional.of(user));
		when(dataSyncLogRepository.save(org.mockito.ArgumentMatchers.any(DataSyncLog.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		when(userApiKeyRepository.findByUserId(1L)).thenReturn(Optional.of(userApiKey));

		assertThatThrownBy(() -> gateway.get(1L, "plain-api-key", "https://example.test", NexonRequestMode.REALTIME))
				.isInstanceOf(ApiException.class);
		assertThat(userApiKey.getKeyStatus().name()).isEqualTo("INVALID");
	}

	@Test
	void getWaitsBeforeSecondDispatch() {
		WebClient webClient = WebClient.builder()
				.exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK).body("{}").build()))
				.build();
		AtomicReference<Long> waitedMillis = new AtomicReference<>(0L);
		NexonApiGateway gateway = gateway(webClient, waitedMillis::set);
		User user = user();
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.get(org.mockito.ArgumentMatchers.anyString())).thenReturn(null);
		when(apiCallCounter.getCount("1")).thenReturn(0L);
		when(userRepository.findById(1L)).thenReturn(Optional.of(user));
		when(dataSyncLogRepository.save(org.mockito.ArgumentMatchers.any(DataSyncLog.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		gateway.get(1L, "plain-api-key", "https://example.test/one", NexonRequestMode.REALTIME);
		gateway.get(1L, "plain-api-key", "https://example.test/two", NexonRequestMode.REALTIME);

		assertThat(waitedMillis.get()).isEqualTo(Duration.ofSeconds(1).toMillis());
	}

	private NexonApiGateway gateway(WebClient webClient, NexonApiGateway.Sleeper sleeper) {
		return new NexonApiGateway(
				webClient,
				objectMapper,
				redisTemplate,
				redisPolicyProperties,
				nexonProperties,
				userRepository,
				userApiKeyRepository,
				dataSyncLogRepository,
				apiKeyCryptoService,
				apiCallCounter,
				CLOCK,
				sleeper
		);
	}

	private User user() {
		User user = User.create(OAuthProvider.KAKAO, "oauth-id", "user@example.com", "nickname");
		ReflectionTestUtils.setField(user, "id", 1L);
		return user;
	}
}
