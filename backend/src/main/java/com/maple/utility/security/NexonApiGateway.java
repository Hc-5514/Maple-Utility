package com.maple.utility.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.maple.utility.config.NexonProperties;
import com.maple.utility.config.RedisPolicyProperties;
import com.maple.utility.entity.DataSyncLog;
import com.maple.utility.entity.SyncType;
import com.maple.utility.entity.User;
import com.maple.utility.entity.UserApiKey;
import com.maple.utility.exception.ApiException;
import com.maple.utility.repository.DataSyncLogRepository;
import com.maple.utility.repository.UserApiKeyRepository;
import com.maple.utility.repository.UserRepository;
import com.maple.utility.util.NexonApiCallCounter;
import reactor.core.publisher.Mono;

@Component
public class NexonApiGateway {

	private static final String NEXON_API_KEY_HEADER = "x-nxopen-api-key";
	private static final String CACHE_KEY_PREFIX = "nexon:api:cache:";

	private final WebClient webClient;
	private final ObjectMapper objectMapper;
	private final StringRedisTemplate redisTemplate;
	private final RedisPolicyProperties redisPolicyProperties;
	private final NexonProperties nexonProperties;
	private final UserRepository userRepository;
	private final UserApiKeyRepository userApiKeyRepository;
	private final DataSyncLogRepository dataSyncLogRepository;
	private final ApiKeyCryptoService apiKeyCryptoService;
	private final NexonApiCallCounter apiCallCounter;
	private final Clock clock;
	private final Sleeper sleeper;
	private InstantHolder lastDispatch = new InstantHolder(0L);

	@Autowired
	public NexonApiGateway(
			WebClient nexonWebClient,
			ObjectMapper objectMapper,
			StringRedisTemplate redisTemplate,
			RedisPolicyProperties redisPolicyProperties,
			NexonProperties nexonProperties,
			UserRepository userRepository,
			UserApiKeyRepository userApiKeyRepository,
			DataSyncLogRepository dataSyncLogRepository,
			ApiKeyCryptoService apiKeyCryptoService,
			NexonApiCallCounter apiCallCounter,
			Clock clock
	) {
		this(
				nexonWebClient,
				objectMapper,
				redisTemplate,
				redisPolicyProperties,
				nexonProperties,
				userRepository,
				userApiKeyRepository,
				dataSyncLogRepository,
				apiKeyCryptoService,
				apiCallCounter,
				clock,
				Thread::sleep
		);
	}

	NexonApiGateway(
			WebClient webClient,
			ObjectMapper objectMapper,
			StringRedisTemplate redisTemplate,
			RedisPolicyProperties redisPolicyProperties,
			NexonProperties nexonProperties,
			UserRepository userRepository,
			UserApiKeyRepository userApiKeyRepository,
			DataSyncLogRepository dataSyncLogRepository,
			ApiKeyCryptoService apiKeyCryptoService,
			NexonApiCallCounter apiCallCounter,
			Clock clock,
			Sleeper sleeper
	) {
		this.webClient = webClient;
		this.objectMapper = objectMapper;
		this.redisTemplate = redisTemplate;
		this.redisPolicyProperties = redisPolicyProperties;
		this.nexonProperties = nexonProperties;
		this.userRepository = userRepository;
		this.userApiKeyRepository = userApiKeyRepository;
		this.dataSyncLogRepository = dataSyncLogRepository;
		this.apiKeyCryptoService = apiKeyCryptoService;
		this.apiCallCounter = apiCallCounter;
		this.clock = clock;
		this.sleeper = sleeper;
	}

	public JsonNode getWithStoredKey(Long userId, String uri, NexonRequestMode mode) {
		UserApiKey apiKey = userApiKeyRepository.findByUserId(userId)
				.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "API_KEY_NOT_REGISTERED", "Nexon API Key 미등록"));
		String decryptedApiKey = apiKeyCryptoService.decrypt(apiKey.getEncryptedKey());
		return get(userId, decryptedApiKey, uri, mode);
	}

	public JsonNode get(Long userId, String apiKey, String uri, NexonRequestMode mode) {
		String cacheKey = cacheKey(userId, "GET", uri);
		String cachedResponse = redisTemplate.opsForValue().get(cacheKey);
		if (cachedResponse != null) {
			return readJson(cachedResponse);
		}

		validateDailyLimit(userId, mode);
		User user = findUser(userId);
		DataSyncLog log = dataSyncLogRepository.save(DataSyncLog.start(user, SyncType.CHARACTER_SYNC, now()));
		int callsUsed = 0;
		try {
			throttleDispatch();
			String body = callWithRetry(uri, apiKey);
			callsUsed = 1;
			apiCallCounter.increment(String.valueOf(userId));
			redisTemplate.opsForValue().set(cacheKey, body, redisPolicyProperties.cache().nexonApiTtl());
			log.complete(callsUsed, now());
			dataSyncLogRepository.save(log);
			return readJson(body);
		} catch (ApiException exception) {
			callsUsed = exception.getStatus() == HttpStatus.TOO_MANY_REQUESTS ? 0 : callsUsed;
			if ("API_KEY_INVALID".equals(exception.getCode())) {
				userApiKeyRepository.findByUserId(userId).ifPresent(UserApiKey::invalidate);
			}
			log.fail(callsUsed, exception.getMessage(), now());
			dataSyncLogRepository.save(log);
			throw exception;
		}
	}

	private void validateDailyLimit(Long userId, NexonRequestMode mode) {
		long count = apiCallCounter.getCount(String.valueOf(userId));
		if (count >= nexonProperties.dailyCallLimit()) {
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "NEXON_DAILY_LIMIT_EXCEEDED", "Nexon API 일일 호출 한도 초과");
		}
		if (mode == NexonRequestMode.REALTIME && count >= nexonProperties.realtimeCallThreshold()) {
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "NEXON_REALTIME_LIMIT_REACHED", "Nexon API 실시간 호출 제한");
		}
	}

	private synchronized void throttleDispatch() {
		long nowMillis = clock.millis();
		long waitMillis = lastDispatch.value + nexonProperties.dispatchInterval().toMillis() - nowMillis;
		if (waitMillis > 0) {
			try {
				sleeper.sleep(waitMillis);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "NEXON_DISPATCH_INTERRUPTED", "Nexon API 호출 대기 중단");
			}
			lastDispatch = new InstantHolder(clock.millis());
			return;
		}
		lastDispatch = new InstantHolder(nowMillis);
	}

	private String callWithRetry(String uri, String apiKey) {
		int attempts = 0;
		while (true) {
			attempts++;
			GatewayResponse response = webClient.get()
					.uri(uri)
					.header(NEXON_API_KEY_HEADER, apiKey)
					.exchangeToMono(clientResponse -> clientResponse.bodyToMono(String.class)
							.defaultIfEmpty("")
							.map(body -> new GatewayResponse(clientResponse.statusCode().value(), body)))
					.switchIfEmpty(Mono.just(new GatewayResponse(502, "")))
					.block();
			if (response == null) {
				throw new ApiException(HttpStatus.BAD_GATEWAY, "NEXON_API_ERROR", "Nexon OpenAPI 응답 없음");
			}
			HttpStatus status = HttpStatus.resolve(response.status());
			if (status != null && status.is2xxSuccessful()) {
				return response.body() == null ? "{}" : response.body();
			}
			ApiException mappedException = mapException(status);
			if (shouldRetry(status, attempts)) {
				sleepBackoff(attempts);
				continue;
			}
			throw mappedException;
		}
	}

	private ApiException mapException(HttpStatus status) {
		if (status == HttpStatus.BAD_REQUEST) {
			return new ApiException(HttpStatus.BAD_REQUEST, "NEXON_PARAMETER_ERROR", "Nexon API 파라미터 오류");
		}
		if (status == HttpStatus.UNAUTHORIZED) {
			return new ApiException(HttpStatus.UNAUTHORIZED, "API_KEY_INVALID", "유효하지 않은 Nexon API Key");
		}
		if (status == HttpStatus.TOO_MANY_REQUESTS) {
			return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "NEXON_RATE_LIMIT", "Nexon API Rate Limit 초과");
		}
		if (status == HttpStatus.INTERNAL_SERVER_ERROR || status == HttpStatus.SERVICE_UNAVAILABLE) {
			return new ApiException(HttpStatus.BAD_GATEWAY, "NEXON_SERVER_ERROR", "Nexon API 서버 오류");
		}
		return new ApiException(HttpStatus.BAD_GATEWAY, "NEXON_API_ERROR", "Nexon OpenAPI 호출 실패");
	}

	private boolean shouldRetry(HttpStatus status, int attempts) {
		if (status == null) {
			return attempts < 3;
		}
		return attempts < 3 && (
				status == HttpStatus.TOO_MANY_REQUESTS
						|| status == HttpStatus.INTERNAL_SERVER_ERROR
						|| status == HttpStatus.SERVICE_UNAVAILABLE
		);
	}

	private void sleepBackoff(int attempts) {
		try {
			sleeper.sleep((long) Math.pow(2, attempts - 1) * 100L);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "NEXON_RETRY_INTERRUPTED", "Nexon API 재시도 중단");
		}
	}

	private User findUser(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "사용자 없음"));
	}

	private JsonNode readJson(String body) {
		try {
			return objectMapper.readTree(body);
		} catch (Exception exception) {
			throw new ApiException(HttpStatus.BAD_GATEWAY, "NEXON_RESPONSE_PARSE_ERROR", "Nexon API 응답 파싱 실패");
		}
	}

	private String cacheKey(Long userId, String method, String uri) {
		return CACHE_KEY_PREFIX + userId + ":" + method + ":" + sha256(uri);
	}

	private String sha256(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 algorithm not available", exception);
		}
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}

	@FunctionalInterface
	interface Sleeper {
		void sleep(long millis) throws InterruptedException;
	}

	private record InstantHolder(long value) {
	}

	private record GatewayResponse(int status, String body) {
	}
}
