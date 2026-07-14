package com.maple.utility.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.maple.utility.dto.response.ApiKeyStatusResponse;
import com.maple.utility.entity.User;
import com.maple.utility.entity.UserApiKey;
import com.maple.utility.exception.ApiException;
import com.maple.utility.repository.UserApiKeyRepository;
import com.maple.utility.repository.UserRepository;
import com.maple.utility.security.ApiKeyCryptoService;
import com.maple.utility.security.NexonCharacterSummary;
import com.maple.utility.security.NexonOpenApiClient;

@Service
public class ApiKeyService {

	private final UserRepository userRepository;
	private final UserApiKeyRepository userApiKeyRepository;
	private final NexonOpenApiClient nexonOpenApiClient;
	private final ApiKeyCryptoService apiKeyCryptoService;
	private final CharacterSyncService characterSyncService;
	private final Clock clock;

	public ApiKeyService(
			UserRepository userRepository,
			UserApiKeyRepository userApiKeyRepository,
			NexonOpenApiClient nexonOpenApiClient,
			ApiKeyCryptoService apiKeyCryptoService,
			CharacterSyncService characterSyncService,
			Clock clock
	) {
		this.userRepository = userRepository;
		this.userApiKeyRepository = userApiKeyRepository;
		this.nexonOpenApiClient = nexonOpenApiClient;
		this.apiKeyCryptoService = apiKeyCryptoService;
		this.characterSyncService = characterSyncService;
		this.clock = clock;
	}

	@Transactional
	public ApiKeyStatusResponse register(Long userId, String apiKey) {
		User user = findUser(userId);
		List<NexonCharacterSummary> characters;
		try {
			characters = nexonOpenApiClient.getCharacters(apiKey);
		} catch (ApiException exception) {
			if ("API_KEY_INVALID".equals(exception.getCode())) {
				markInvalid(userId);
			}
			throw exception;
		}
		String encryptedKey = apiKeyCryptoService.encrypt(apiKey);
		LocalDateTime verifiedAt = LocalDateTime.now(clock);

		UserApiKey userApiKey = userApiKeyRepository.findByUserId(userId)
				.orElse(null);
		if (userApiKey == null) {
			userApiKey = userApiKeyRepository.save(UserApiKey.create(user, encryptedKey, verifiedAt));
		} else {
			userApiKey.replaceKey(encryptedKey, verifiedAt);
		}
		characterSyncService.syncCharacters(user, characters);

		return ApiKeyStatusResponse.registered(userApiKey);
	}

	@Transactional(readOnly = true)
	public ApiKeyStatusResponse status(Long userId) {
		return userApiKeyRepository.findByUserId(userId)
				.map(ApiKeyStatusResponse::registered)
				.orElseGet(ApiKeyStatusResponse::unregistered);
	}

	@Transactional
	public void delete(Long userId) {
		userApiKeyRepository.deleteByUserId(userId);
	}

	private void markInvalid(Long userId) {
		userApiKeyRepository.findByUserId(userId)
				.ifPresent(UserApiKey::invalidate);
	}

	private User findUser(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "사용자 없음"));
	}
}
