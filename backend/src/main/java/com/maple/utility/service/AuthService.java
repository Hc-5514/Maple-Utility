package com.maple.utility.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.maple.utility.dto.response.AuthResponse;
import com.maple.utility.dto.response.AuthUserResponse;
import com.maple.utility.dto.response.MeResponse;
import com.maple.utility.entity.OAuthProvider;
import com.maple.utility.entity.User;
import com.maple.utility.entity.UserApiKey;
import com.maple.utility.exception.ApiException;
import com.maple.utility.repository.UserApiKeyRepository;
import com.maple.utility.repository.UserRepository;
import com.maple.utility.security.ApiKeyCryptoService;
import com.maple.utility.security.JwtToken;
import com.maple.utility.security.JwtTokenProvider;
import com.maple.utility.security.NexonCharacterSummary;
import com.maple.utility.security.NexonOpenApiClient;
import com.maple.utility.security.OAuthClient;
import com.maple.utility.security.OAuthUserInfo;
import com.maple.utility.security.RefreshTokenRedisService;

@Service
public class AuthService {

	private final Map<OAuthProvider, OAuthClient> oauthClients;
	private final UserRepository userRepository;
	private final UserApiKeyRepository userApiKeyRepository;
	private final JwtTokenProvider jwtTokenProvider;
	private final RefreshTokenRedisService refreshTokenRedisService;
	private final NexonOpenApiClient nexonOpenApiClient;
	private final ApiKeyCryptoService apiKeyCryptoService;
	private final Clock clock;

	public AuthService(
			List<OAuthClient> oauthClients,
			UserRepository userRepository,
			JwtTokenProvider jwtTokenProvider,
			RefreshTokenRedisService refreshTokenRedisService,
			UserApiKeyRepository userApiKeyRepository,
			NexonOpenApiClient nexonOpenApiClient,
			ApiKeyCryptoService apiKeyCryptoService,
			Clock clock
	) {
		this.oauthClients = new EnumMap<>(OAuthProvider.class);
		oauthClients.forEach(oauthClient -> this.oauthClients.put(oauthClient.provider(), oauthClient));
		this.userRepository = userRepository;
		this.jwtTokenProvider = jwtTokenProvider;
		this.refreshTokenRedisService = refreshTokenRedisService;
		this.userApiKeyRepository = userApiKeyRepository;
		this.nexonOpenApiClient = nexonOpenApiClient;
		this.apiKeyCryptoService = apiKeyCryptoService;
		this.clock = clock;
	}

	@Transactional
	public LoginResult login(OAuthProvider provider, String code) {
		OAuthClient oauthClient = oauthClients.get(provider);
		if (oauthClient == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_OAUTH_PROVIDER", "지원하지 않는 OAuth Provider");
		}

		OAuthUserInfo userInfo = oauthClient.getUserInfo(code);
		if (userInfo.oauthId() == null || userInfo.oauthId().isBlank()) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_OAUTH_USER", "OAuth 사용자 정보 조회 실패");
		}

		User user = userRepository.findByOauthProviderAndOauthId(provider, userInfo.oauthId())
				.map(existingUser -> {
					existingUser.updateProfile(userInfo.email(), userInfo.nickname());
					return existingUser;
				})
				.orElse(null);
		boolean isNewUser = user == null;
		if (isNewUser) {
			user = userRepository.save(User.create(
					provider,
					userInfo.oauthId(),
					userInfo.email(),
					userInfo.nickname()
			));
		}

		return createLoginResult(user, isNewUser);
	}

	@Transactional
	public LoginResult loginWithNexonApiKey(String apiKey) {
		String oauthId = sha256Hex(apiKey);
		User user = userRepository.findByOauthProviderAndOauthId(OAuthProvider.NEXON_APIKEY, oauthId)
				.orElse(null);
		boolean isNewUser = user == null;
		if (isNewUser) {
			user = userRepository.save(User.create(
					OAuthProvider.NEXON_APIKEY,
					oauthId,
					null,
					null
			));
		}

		List<NexonCharacterSummary> characters = nexonOpenApiClient.getCharacters(user.getId(), apiKey);
		String nickname = firstCharacterName(characters);
		if (nickname != null) {
			user.updateProfile(null, nickname);
		}

		String encryptedKey = apiKeyCryptoService.encrypt(apiKey);
		LocalDateTime verifiedAt = LocalDateTime.now(clock);
		UserApiKey userApiKey = userApiKeyRepository.findByUserId(user.getId()).orElse(null);
		if (userApiKey == null) {
			userApiKeyRepository.save(UserApiKey.create(user, encryptedKey, verifiedAt));
		} else {
			userApiKey.replaceKey(encryptedKey, verifiedAt);
		}

		return createLoginResult(user, isNewUser);
	}

	@Transactional(readOnly = true)
	public AuthResponse refresh(String refreshTokenValue) {
		JwtTokenProvider.RefreshTokenClaims claims = jwtTokenProvider.parseRefreshToken(refreshTokenValue);
		String storedRefreshToken = refreshTokenRedisService.find(claims.userId(), claims.tokenId())
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_NOT_FOUND", "Refresh Token 없음"));
		if (!storedRefreshToken.equals(refreshTokenValue)) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_MISMATCH", "Refresh Token 불일치");
		}

		User user = findUser(claims.userId());
		JwtToken accessToken = jwtTokenProvider.createAccessToken(user.getId());

		return AuthResponse.bearer(
				accessToken.value(),
				accessToken.ttl().toSeconds(),
				AuthUserResponse.from(user, false)
		);
	}

	public void logout(String refreshTokenValue) {
		JwtTokenProvider.RefreshTokenClaims claims = jwtTokenProvider.parseRefreshToken(refreshTokenValue);
		refreshTokenRedisService.delete(claims.userId(), claims.tokenId());
	}

	@Transactional(readOnly = true)
	public MeResponse me(Long userId) {
		return MeResponse.from(findUser(userId));
	}

	private User findUser(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "사용자 없음"));
	}

	private LoginResult createLoginResult(User user, boolean isNewUser) {
		JwtToken accessToken = jwtTokenProvider.createAccessToken(user.getId());
		JwtToken refreshToken = jwtTokenProvider.createRefreshToken(user.getId());
		refreshTokenRedisService.save(user.getId(), refreshToken.tokenId(), refreshToken.value(), refreshToken.ttl());

		AuthResponse response = AuthResponse.bearer(
				accessToken.value(),
				accessToken.ttl().toSeconds(),
				AuthUserResponse.from(user, isNewUser)
		);
		return new LoginResult(response, refreshToken);
	}

	private String firstCharacterName(List<NexonCharacterSummary> characters) {
		return characters.stream()
				.map(NexonCharacterSummary::characterName)
				.filter(characterName -> characterName != null && !characterName.isBlank())
				.findFirst()
				.orElse(null);
	}

	private String sha256Hex(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
			StringBuilder builder = new StringBuilder(hash.length * 2);
			for (byte b : hash) {
				builder.append(String.format("%02x", b));
			}
			return builder.toString();
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 algorithm not available", exception);
		}
	}

	public record LoginResult(
			AuthResponse response,
			JwtToken refreshToken
	) {
	}
}
