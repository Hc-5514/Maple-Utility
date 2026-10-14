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
import org.junit.jupiter.api.BeforeEach;
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
import com.maple.utility.entity.SyncType;
import com.maple.utility.entity.User;
import com.maple.utility.entity.UserApiKey;
import com.maple.utility.exception.ApiException;
import com.maple.utility.exception.NexonApiException;
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

	@BeforeEach
	void reserveBudgets() {
		org.mockito.Mockito.lenient().when(apiCallCounter.reserve(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong())).thenReturn(true);
	}

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
		when(userApiKeyRepository.findActiveByDataOwnerId(1L)).thenReturn(Optional.of(userApiKey));
		when(apiKeyCryptoService.decrypt("encrypted-api-key")).thenReturn("plain-api-key");
		when(apiCallCounter.getCount("1")).thenReturn(0L);
		when(userRepository.findById(1L)).thenReturn(Optional.of(user));
		when(dataSyncLogRepository.save(org.mockito.ArgumentMatchers.any(DataSyncLog.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));


		assertThat(gateway.getWithStoredKey(1L, "https://example.test/character/list", NexonRequestMode.REALTIME)
				.path("ok").asBoolean()).isTrue();

		assertThat(capturedRequest.get().headers().getFirst("x-nxopen-api-key")).isEqualTo("plain-api-key");
		verify(valueOperations).set(
				org.mockito.ArgumentMatchers.startsWith("nexon:api:cache:1:GET:"),
				org.mockito.ArgumentMatchers.eq("{\"ok\":true}"),
				org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(5))
		);
		verify(apiCallCounter).reserve(org.mockito.ArgumentMatchers.eq("1"), org.mockito.ArgumentMatchers.startsWith("api-key:"), org.mockito.ArgumentMatchers.eq(450L));
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
		verify(apiCallCounter, never()).reserve(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong());
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
		when(userApiKeyRepository.findByKeyFingerprint(ApiKeyFingerprint.of("plain-api-key"))).thenReturn(Optional.of(userApiKey));

		assertThatThrownBy(() -> gateway.get(1L, "plain-api-key", "https://example.test", NexonRequestMode.REALTIME))
				.isInstanceOf(ApiException.class);
		assertThat(userApiKey.getKeyStatus().name()).isEqualTo("INVALID");
	}

	@Test
	void getMapsNexonBadRequestBodyToNexonApiException() {
		WebClient webClient = WebClient.builder()
				.exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.BAD_REQUEST)
						.body("{\"error\":{\"name\":\"OPENAPI00003\",\"message\":\"Please input valid id\"}}")
						.build()))
				.build();
		NexonApiGateway gateway = gateway(webClient, millis -> {
		});
		User user = user();

		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.get(org.mockito.ArgumentMatchers.anyString())).thenReturn(null);
		when(apiCallCounter.getCount("1")).thenReturn(0L);
		when(userRepository.findById(1L)).thenReturn(Optional.of(user));
		when(dataSyncLogRepository.save(org.mockito.ArgumentMatchers.any(DataSyncLog.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		assertThatThrownBy(() -> gateway.get(1L, "plain-api-key", "https://example.test/character/basic?ocid=invalid", NexonRequestMode.REALTIME))
				.isInstanceOfSatisfying(NexonApiException.class, exception -> {
					assertThat(exception.getCode()).isEqualTo("NEXON_PARAMETER_ERROR");
					assertThat(exception.getNexonErrorName()).isEqualTo("OPENAPI00003");
					assertThat(exception.getNexonErrorMessage()).isEqualTo("Please input valid id");
				});
	}

	@Test
	void getPreservesNexonForbiddenCode() {
		WebClient webClient = WebClient.builder()
				.exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.FORBIDDEN)
						.body("{\"error\":{\"name\":\"OPENAPI00002\",\"message\":\"Access Denied\"}}")
						.build()))
				.build();
		NexonApiGateway gateway = gateway(webClient, millis -> {
		});
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.get(org.mockito.ArgumentMatchers.anyString())).thenReturn(null);
		when(apiCallCounter.getCount("1")).thenReturn(0L);
		when(userRepository.findById(1L)).thenReturn(Optional.of(user()));
		when(dataSyncLogRepository.save(org.mockito.ArgumentMatchers.any(DataSyncLog.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		assertThatThrownBy(() -> gateway.get(1L, "plain-api-key", "https://example.test/scheduler/character-state?ocid=ocid", NexonRequestMode.REALTIME))
				.isInstanceOfSatisfying(NexonApiException.class, exception -> {
					assertThat(exception.getCode()).isEqualTo("NEXON_ACCESS_DENIED");
					assertThat(exception.getNexonErrorName()).isEqualTo("OPENAPI00002");
				});
	}

	@Test
	void getWithBatchSyncTypeWritesSchedulerBatchLog() {
		WebClient webClient = WebClient.builder()
				.exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK).body("{}").build()))
				.build();
		NexonApiGateway gateway = gateway(webClient, millis -> {
		});
		User user = user();
		UserApiKey userApiKey = UserApiKey.create(user, "encrypted-api-key", null);
		AtomicReference<DataSyncLog> startedLog = new AtomicReference<>();

		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.get(org.mockito.ArgumentMatchers.anyString())).thenReturn(null);
		when(userApiKeyRepository.findActiveByDataOwnerId(1L)).thenReturn(Optional.of(userApiKey));
		when(apiKeyCryptoService.decrypt("encrypted-api-key")).thenReturn("plain-api-key");
		when(apiCallCounter.getCount("1")).thenReturn(399L);
		when(userRepository.findById(1L)).thenReturn(Optional.of(user));
		when(dataSyncLogRepository.save(org.mockito.ArgumentMatchers.any(DataSyncLog.class)))
				.thenAnswer(invocation -> {
					DataSyncLog log = invocation.getArgument(0);
					startedLog.compareAndSet(null, log);
					return log;
				});


		gateway.getWithStoredKey(1L, "https://example.test/character/scheduler", NexonRequestMode.BATCH, SyncType.SCHEDULER_BATCH);

		assertThat(startedLog.get().getSyncType()).isEqualTo(SyncType.SCHEDULER_BATCH);
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

	@Test
	void retriesConsumeEveryOutboundAttemptAndLogFailures() {
		AtomicInteger outbound = new AtomicInteger();
		WebClient client = WebClient.builder().exchangeFunction(request -> {
			outbound.incrementAndGet();
			return Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).body("{}").build());
		}).build();
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(userRepository.findById(1L)).thenReturn(Optional.of(user()));
		AtomicReference<DataSyncLog> saved = new AtomicReference<>();
		when(dataSyncLogRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
			DataSyncLog record = invocation.getArgument(0);
			saved.set(record);
			return record;
		});
		assertThatThrownBy(() -> gateway(client, millis -> {}).get(1L, "key", "https://example.test/retry", NexonRequestMode.REALTIME))
				.isInstanceOf(ApiException.class);
		assertThat(outbound).hasValue(3);
		verify(apiCallCounter, org.mockito.Mockito.times(3)).reserve(org.mockito.ArgumentMatchers.eq("1"), org.mockito.ArgumentMatchers.startsWith("api-key:"), org.mockito.ArgumentMatchers.eq(450L));
		assertThat(saved.get().getApiCallsUsed()).isEqualTo(3);
	}

	@Test
	void retryCannotCrossAtomicBudgetBoundary() {
		AtomicInteger outbound = new AtomicInteger();
		WebClient client = WebClient.builder().exchangeFunction(request -> {
			outbound.incrementAndGet();
			return Mono.just(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).body("{}").build());
		}).build();
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(userRepository.findById(1L)).thenReturn(Optional.of(user()));
		when(dataSyncLogRepository.save(org.mockito.ArgumentMatchers.any(DataSyncLog.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		when(apiCallCounter.reserve(org.mockito.ArgumentMatchers.eq("1"), org.mockito.ArgumentMatchers.startsWith("api-key:"), org.mockito.ArgumentMatchers.eq(450L))).thenReturn(true, false);
		assertThatThrownBy(() -> gateway(client, millis -> {}).get(1L, "key", "https://example.test/retry", NexonRequestMode.REALTIME))
				.isInstanceOfSatisfying(ApiException.class, api -> assertThat(api.getCode()).isEqualTo("NEXON_REALTIME_LIMIT_REACHED"));
		assertThat(outbound).hasValue(1);
		verify(apiCallCounter, org.mockito.Mockito.times(2)).reserve(org.mockito.ArgumentMatchers.eq("1"), org.mockito.ArgumentMatchers.startsWith("api-key:"), org.mockito.ArgumentMatchers.eq(450L));
	}

	@Test
	void batchStopsAtFourHundredTotalCallsAndPreservesRealtimeHeadroom() {
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(apiCallCounter.getCount("1")).thenReturn(400L);
		assertThatThrownBy(() -> gateway(WebClient.builder().build(), millis -> {})
				.get(1L, "key", "https://example.test", NexonRequestMode.BATCH))
				.isInstanceOfSatisfying(ApiException.class, api -> assertThat(api.getCode()).isEqualTo("NEXON_BATCH_LIMIT_REACHED"));
		verify(apiCallCounter, never()).reserve(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong());
	}

	@Test
	void identicalApiKeySharesBudgetAcrossUsersAndOtherKeysRemainIndependent() throws Exception {
		String shared = "api-key:" + java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
				.digest("shared-key".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		java.util.Map<String, Long> counts = new java.util.HashMap<>();
		counts.put(shared, 449L);
		when(apiCallCounter.getCount(org.mockito.ArgumentMatchers.anyString()))
				.thenAnswer(invocation -> counts.getOrDefault(invocation.getArgument(0), 0L));
		when(apiCallCounter.reserve(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.anyLong())).thenAnswer(invocation -> {
			String userBudget = invocation.getArgument(0);
			String keyBudget = invocation.getArgument(1);
			long limit = invocation.getArgument(2);
			if (counts.getOrDefault(userBudget, 0L) >= limit || counts.getOrDefault(keyBudget, 0L) >= limit) { return false; }
			counts.merge(userBudget, 1L, Long::sum);
			counts.merge(keyBudget, 1L, Long::sum);
			return true;
		});
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(userRepository.findById(org.mockito.ArgumentMatchers.anyLong())).thenReturn(Optional.of(user()));
		when(dataSyncLogRepository.save(org.mockito.ArgumentMatchers.any(DataSyncLog.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		AtomicInteger outbound = new AtomicInteger();
		var client = WebClient.builder().exchangeFunction(request -> {
			outbound.incrementAndGet();
			return Mono.just(ClientResponse.create(HttpStatus.OK).body("{}").build());
		}).build();
		var gateway = gateway(client, millis -> {});
		gateway.get(1L, "shared-key", "https://example.test/user-one", NexonRequestMode.REALTIME);
		assertThatThrownBy(() -> gateway.get(2L, "shared-key", "https://example.test/user-two", NexonRequestMode.REALTIME))
				.isInstanceOfSatisfying(ApiException.class, api -> assertThat(api.getCode()).isEqualTo("NEXON_REALTIME_LIMIT_REACHED"));
		assertThat(counts.get(shared)).isEqualTo(450);
		assertThat(counts.get("1")).isEqualTo(1);
		assertThat(counts).doesNotContainKey("2");
		assertThat(outbound).hasValue(1);
		gateway.get(2L, "different-key", "https://example.test/other-key", NexonRequestMode.REALTIME);
		assertThat(outbound).hasValue(2);
		assertThat(counts.get("2")).isEqualTo(1);
		assertThat(counts.keySet()).noneMatch(key -> key.contains("shared-key") || key.contains("different-key"));
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
