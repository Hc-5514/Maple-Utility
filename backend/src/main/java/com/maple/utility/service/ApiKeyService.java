package com.maple.utility.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.maple.utility.dto.response.ApiKeyStatusResponse;
import com.maple.utility.dto.response.ApiKeyRegistrationResponse;
import com.maple.utility.entity.UserApiKey;
import com.maple.utility.exception.ApiException;
import com.maple.utility.repository.UserApiKeyRepository;
import com.maple.utility.security.ApiKeyCryptoService;
import com.maple.utility.security.JwtAuthentication;
import com.maple.utility.security.JwtToken;

@Service
public class ApiKeyService {
    private final UserApiKeyRepository keys;
    private final ApiKeyCryptoService crypto;
    private final DataContextService contexts;
    private final CharacterSyncService characters;
    private final AuthService auth;
    public ApiKeyService(UserApiKeyRepository keys, ApiKeyCryptoService crypto, DataContextService contexts,
            CharacterSyncService characters, AuthService auth) {
        this.keys = keys; this.crypto = crypto; this.contexts = contexts; this.characters = characters; this.auth = auth;
    }
    @Transactional
    public RegistrationResult register(JwtAuthentication principal, String apiKey, String refreshToken) {
        var connection = contexts.connect(contexts.user(principal.identityUserId()), apiKey);
        characters.syncSummaries(contexts.user(connection.principal().userId()), connection.verified().characters());
        var login = auth.switchSession(connection.principal(), refreshToken);
        var status = registeredStatus(connection.credential());
        return new RegistrationResult(new ApiKeyRegistrationResponse(status.registered(), status.keyStatus(),
                status.lastVerifiedAt(), status.keySuffix(), login.response()), login.refreshToken());
    }
    @Transactional(readOnly = true)
    public ApiKeyStatusResponse status(JwtAuthentication principal) {
        return selected(principal).map(this::registeredStatus).orElseGet(ApiKeyStatusResponse::unregistered);
    }
    @Transactional
    public void delete(JwtAuthentication principal) {
        contexts.lockConnections();
        selected(principal).ifPresent(keys::delete);
    }
    private java.util.Optional<UserApiKey> selected(JwtAuthentication principal) {
        if (principal.credentialId() == null) return java.util.Optional.empty();
        var credential = keys.findById(principal.credentialId());
        credential.ifPresent(key -> {
            if (!contexts.canonical(key.getContextId()).id().equals(principal.contextId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "DATA_CONTEXT_ACCESS_DENIED", "데이터셋 접근 권한 없음");
            }
        });
        return credential;
    }
    private ApiKeyStatusResponse registeredStatus(UserApiKey key) {
        return ApiKeyStatusResponse.registered(key, DataContextService.suffix(crypto.decrypt(key.getEncryptedKey())));
    }
    public record RegistrationResult(ApiKeyRegistrationResponse response, JwtToken refreshToken) { }
}
