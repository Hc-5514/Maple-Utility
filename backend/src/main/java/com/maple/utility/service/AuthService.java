package com.maple.utility.service;

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
import com.maple.utility.exception.ApiException;
import com.maple.utility.repository.UserRepository;
import com.maple.utility.security.JwtToken;
import com.maple.utility.security.JwtTokenProvider;
import com.maple.utility.security.NexonCharacterSummary;
import com.maple.utility.security.OAuthClient;
import com.maple.utility.security.OAuthUserInfo;
import com.maple.utility.security.RefreshTokenRedisService;

@Service
public class AuthService {

	private final Map<OAuthProvider, OAuthClient> oauthClients;
	private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRedisService refreshTokenRedisService;
    private final DataContextService contexts;
    private final CharacterSyncService characterSync;

    public AuthService(List<OAuthClient> oauthClients, UserRepository userRepository,
            JwtTokenProvider jwtTokenProvider, RefreshTokenRedisService refreshTokenRedisService,
            DataContextService contexts, CharacterSyncService characterSync) {
        this.oauthClients = new EnumMap<>(OAuthProvider.class);
        oauthClients.forEach(client -> this.oauthClients.put(client.provider(), client));
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenRedisService = refreshTokenRedisService;
        this.contexts = contexts;
        this.characterSync = characterSync;
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

		contexts.lockConnections();
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

		return createLoginResult(user, isNewUser, contexts.defaultPrincipal(user));
	}

    @Transactional
    public LoginResult loginWithNexonApiKey(String apiKey) {
        String key = DataContextService.normalize(apiKey);
        String fingerprint = com.maple.utility.security.ApiKeyFingerprint.of(key);
        contexts.lockConnections();
        User identity = userRepository.findByOauthProviderAndOauthId(OAuthProvider.NEXON_APIKEY, fingerprint).orElse(null);
        boolean isNewUser = identity == null;
        if (identity == null) identity = userRepository.save(User.create(OAuthProvider.NEXON_APIKEY, fingerprint, null, null));
        var connection = contexts.connect(identity, key);
        String nickname = connection.verified().characters().stream().map(NexonCharacterSummary::characterName).findFirst().orElse(null);
        if (nickname != null) { identity.updateProfile(null, nickname); identity = userRepository.save(identity); }
        characterSync.syncSummaries(contexts.user(connection.principal().userId()), connection.verified().characters());
        return createLoginResult(identity, isNewUser, connection.principal());
    }

	@Transactional(readOnly = true)
	public AuthResponse refresh(String refreshTokenValue) { return refreshSession(refreshTokenValue).response(); }

    @Transactional
    public RefreshResult refreshSession(String refreshTokenValue) {
        contexts.lockBusinessRequest();
		JwtTokenProvider.RefreshTokenClaims claims = jwtTokenProvider.parseRefreshToken(refreshTokenValue);
		String storedRefreshToken = refreshTokenRedisService.find(claims.userId(), claims.tokenId())
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_NOT_FOUND", "Refresh Token 없음"));
		if (!storedRefreshToken.equals(refreshTokenValue)) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_MISMATCH", "Refresh Token 불일치");
		}

		User user = findUser(claims.userId());
		var principal = contexts.resolve(claims.principal());
        JwtToken accessToken = jwtTokenProvider.createAccessToken(principal);

        AuthResponse response = AuthResponse.bearer(accessToken.value(), accessToken.ttl().toSeconds(), AuthUserResponse.from(user, false));
        JwtToken upgraded = null;
        if (claims.principal().contextId() == null) {
            upgraded = jwtTokenProvider.createRefreshToken(principal);
            refreshTokenRedisService.save(user.getId(), upgraded.tokenId(), upgraded.value(), upgraded.ttl());
            refreshTokenRedisService.delete(claims.userId(), claims.tokenId());
        }
        return new RefreshResult(response, upgraded);
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

    public LoginResult createLoginResult(User identity, boolean isNewUser, com.maple.utility.security.JwtAuthentication principal) {
        JwtToken accessToken = jwtTokenProvider.createAccessToken(principal);
        JwtToken refreshToken = jwtTokenProvider.createRefreshToken(principal);
        refreshTokenRedisService.save(identity.getId(), refreshToken.tokenId(), refreshToken.value(), refreshToken.ttl());
        return new LoginResult(AuthResponse.bearer(accessToken.value(), accessToken.ttl().toSeconds(),
                AuthUserResponse.from(identity, isNewUser)), refreshToken);
    }

    @Transactional
    public LoginResult selectContext(Long identityId, Long credentialId, String previousRefreshToken) {
        var principal = contexts.select(identityId, credentialId);
        return switchSession(principal, previousRefreshToken);
    }

    public LoginResult switchSession(com.maple.utility.security.JwtAuthentication principal, String previousRefreshToken) {
        JwtTokenProvider.RefreshTokenClaims previous = null;
        if (previousRefreshToken != null) {
            try { previous = jwtTokenProvider.parseRefreshToken(previousRefreshToken); }
            catch (ApiException staleCookie) { /* A valid access-token session can replace an expired cookie. */ }
            if (previous != null && !previous.userId().equals(principal.identityUserId())) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_MISMATCH", "Refresh Token 사용자 불일치");
            }
        }
        LoginResult result = createLoginResult(findUser(principal.identityUserId()), false, principal);
        if (previous != null) refreshTokenRedisService.delete(previous.userId(), previous.tokenId());
        return result;
    }

    public record RefreshResult(AuthResponse response, JwtToken upgradedRefreshToken) { }

    public record LoginResult(AuthResponse response, JwtToken refreshToken) { }
}
