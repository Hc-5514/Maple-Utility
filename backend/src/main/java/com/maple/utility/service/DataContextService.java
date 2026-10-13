package com.maple.utility.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.maple.utility.entity.OAuthProvider;
import com.maple.utility.entity.User;
import com.maple.utility.entity.UserApiKey;
import com.maple.utility.exception.ApiException;
import com.maple.utility.repository.UserApiKeyRepository;
import com.maple.utility.repository.UserRepository;
import com.maple.utility.security.ApiKeyCryptoService;
import com.maple.utility.security.ApiKeyFingerprint;
import com.maple.utility.security.JwtAuthentication;
import com.maple.utility.security.NexonAccountCharacters;
import com.maple.utility.security.NexonOpenApiClient;

/** Identity membership is separate from the canonical user owning existing business records. */
@Service
public class DataContextService {
    public static final long CONNECTION_LOCK = 259L;
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final UserApiKeyRepository keys;
    private final ApiKeyCryptoService crypto;
    private final NexonOpenApiClient nexon;
    private final DataContextMergeService merger;
    private final CacheManager caches;
    private final Clock clock;
    private final jakarta.persistence.EntityManager entityManager;

    public DataContextService(JdbcTemplate jdbc, UserRepository users, UserApiKeyRepository keys,
            ApiKeyCryptoService crypto, NexonOpenApiClient nexon, DataContextMergeService merger,
            CacheManager caches, Clock clock, jakarta.persistence.EntityManager entityManager) {
        this.jdbc = jdbc; this.users = users; this.keys = keys; this.crypto = crypto;
        this.nexon = nexon; this.merger = merger; this.caches = caches; this.clock = clock; this.entityManager = entityManager;
    }

    public void lockConnections() { jdbc.execute("SELECT pg_advisory_xact_lock(" + CONNECTION_LOCK + ")"); }
    public void lockBusinessRequest() { jdbc.execute("SELECT pg_advisory_xact_lock_shared(" + CONNECTION_LOCK + ")"); }

    @Transactional
    public Context own(User identity) {
        List<Context> found = jdbc.query("SELECT * FROM data_contexts WHERE owner_user_id = ?", this::map, identity.getId());
        if (!found.isEmpty()) return canonical(found.getFirst().id());
        Long id = jdbc.queryForObject("INSERT INTO data_contexts(owner_user_id,kakao_user_id) VALUES (?,?) RETURNING id",
                Long.class, identity.getId(), identity.getOauthProvider() == OAuthProvider.KAKAO ? identity.getId() : null);
        link(identity.getId(), id);
        return canonical(id);
    }

    public Context canonical(Long id) {
        Set<Long> visited = new LinkedHashSet<>();
        while (id != null && visited.add(id)) {
            Context context = jdbc.query("SELECT * FROM data_contexts WHERE id = ?", this::map, id).stream().findFirst()
                    .orElseThrow(() -> denied("DATA_CONTEXT_NOT_FOUND", "데이터셋 없음"));
            if (context.canonicalId() == null) return context;
            id = context.canonicalId();
        }
        throw denied("DATA_CONTEXT_INVALID", "데이터셋 연결 오류");
    }

    private Context map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new Context(rs.getLong("id"), rs.getLong("owner_user_id"),
                rs.getObject("kakao_user_id", Long.class), rs.getObject("canonical_context_id", Long.class));
    }

    public JwtAuthentication resolve(JwtAuthentication principal) {
        User identity = user(principal.identityUserId());
        Context context = principal.contextId() == null ? own(identity) : canonical(principal.contextId());
        if (!member(identity.getId(), context.id())) throw denied("DATA_CONTEXT_ACCESS_DENIED", "데이터셋 접근 권한 없음");
        Long credentialId = principal.credentialId();
        // Legacy tokens alone can pick a credential. New tokens with a deleted credential remain pinned to it.
        if (principal.contextId() == null && credentialId == null) {
            credentialId = preferredCredential(context.id()).map(UserApiKey::getId).orElse(null);
        }
        if (credentialId != null) {
            UserApiKey key = keys.findById(credentialId).orElse(null);
            if (key != null && !Objects.equals(canonical(key.getContextId()).id(), context.id())) {
                throw denied("DATA_CONTEXT_ACCESS_DENIED", "자격 증명 데이터셋 불일치");
            }
        }
        return new JwtAuthentication(context.ownerUserId(), identity.getId(), context.id(), credentialId);
    }

    @Transactional
    public Connection connect(User identity, String rawKey) {
        return connectInternal(identity, rawKey, true);
    }

    private Connection connectInternal(User identity, String rawKey, boolean reconcileLegacy) {
        String key = normalize(rawKey);
        // Always bypass response caches before linking, merging, or changing any credentials.
        NexonAccountCharacters verified = nexon.verifyAccounts(identity.getId(), key);
        lockConnections();
        if (reconcileLegacy) reconcileOwnLegacyKeys(identity, key);
        String fingerprint = ApiKeyFingerprint.of(key);
        Context own = own(identity);
        List<UserApiKey> matches = new ArrayList<>();
        keys.findByKeyFingerprint(fingerprint).ifPresent(matches::add);
        for (UserApiKey legacy : keys.findByKeyFingerprintIsNullOrderByIdAsc()) {
            if (ApiKeyFingerprint.of(crypto.decrypt(legacy.getEncryptedKey())).equals(fingerprint)) matches.add(legacy);
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (String accountId : verified.accountIds()) {
            jdbc.query("SELECT context_id FROM data_context_accounts WHERE account_id = ?",
                    (rs, row) -> rs.getLong(1), accountId).forEach(id -> ids.add(canonical(id).id()));
        }
        matches.forEach(match -> ids.add(canonical(match.getContextId()).id()));
        boolean ownUnverified = jdbc.queryForObject("SELECT COUNT(*) FROM data_context_accounts WHERE context_id = ?", Long.class, own.id()) == 0;
        List<UserApiKey> ownKeys = keys.findByContextIdOrderByIdAsc(own.id());
        // First registration can bring pre-key preferences along. A different verified account never moves old data.
        boolean ownHasData = Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM characters WHERE user_id=?) OR EXISTS(SELECT 1 FROM user_selected_worlds WHERE user_id=?) OR EXISTS(SELECT 1 FROM user_hunting_ground_favorites WHERE user_id=?)", Boolean.class, own.ownerUserId(), own.ownerUserId(), own.ownerUserId()));
        if (ownUnverified && ((ownKeys.isEmpty() && (ids.isEmpty() || ownHasData)) || matches.stream().anyMatch(match -> canonical(match.getContextId()).id().equals(own.id())))) {
            ids.add(own.id());
        }
        if (ids.isEmpty()) {
            // A new data owner is an API-key identity; an OAuth identity can own multiple independent datasets.
            User owner = users.findByOauthProviderAndOauthId(OAuthProvider.NEXON_APIKEY, fingerprint)
                    .orElseGet(() -> users.save(User.create(OAuthProvider.NEXON_APIKEY, fingerprint, null, firstName(verified))));
            ids.add(own(owner).id());
        }
        List<Context> contexts = ids.stream().map(this::canonical).sorted(Comparator.comparing(Context::ownerUserId)).toList();
        Set<Long> kakaoIds = new LinkedHashSet<>();
        contexts.stream().map(Context::kakaoUserId).filter(Objects::nonNull).forEach(kakaoIds::add);
        if (identity.getOauthProvider() == OAuthProvider.KAKAO) kakaoIds.add(identity.getId());
        if (kakaoIds.size() > 1) throw new ApiException(HttpStatus.CONFLICT, "DATA_CONTEXT_CONFLICT", "이미 다른 카카오 계정에 연결된 데이터셋");
        // The canonical owner follows direct-login source priority; record-level conflicts use provenance independently.
        Context target = contexts.stream().min(Comparator
                .comparingInt((Context c) -> user(c.ownerUserId()).getOauthProvider() == OAuthProvider.NEXON_APIKEY ? 0 : 1)
                .thenComparing(c -> jdbc.queryForObject("SELECT created_at FROM users WHERE id = ?", java.sql.Timestamp.class, c.ownerUserId()))
                .thenComparing(Context::ownerUserId)).orElseThrow();
        for (Context source : contexts) {
            if (!source.id().equals(target.id())) mergeContext(target, source);
        }
        Long kakao = kakaoIds.stream().findFirst().orElse(null);
        jdbc.update("UPDATE data_contexts SET kakao_user_id = ? WHERE id = ?", kakao, target.id());
        for (String accountId : verified.accountIds()) {
            jdbc.update("INSERT INTO data_context_accounts(account_id,context_id) VALUES (?,?) ON CONFLICT(account_id) DO UPDATE SET context_id = EXCLUDED.context_id", accountId, target.id());
        }
        link(identity.getId(), target.id());
        UserApiKey credential = matches.stream().filter(match -> fingerprint.equals(match.getKeyFingerprint())).findFirst()
                .orElse(matches.isEmpty() ? null : matches.getFirst());
        if (credential == null) credential = UserApiKey.create(identity, crypto.encrypt(key), LocalDateTime.now(clock));
        else credential.replaceKey(crypto.encrypt(key), LocalDateTime.now(clock));
        credential.assignContext(target.id(), fingerprint);
        credential = keys.saveAndFlush(credential);
        // Preserve duplicate legacy credential IDs for older sessions; they belong to the same verified context.
        for (UserApiKey duplicate : matches) {
            if (!duplicate.getId().equals(credential.getId())) duplicate.assignContext(target.id(), null);
        }
        return new Connection(new JwtAuthentication(target.ownerUserId(), identity.getId(), target.id(), credential.getId()), credential, verified);
    }

    private void reconcileOwnLegacyKeys(User identity, String incomingKey) {
        Context context = own(identity);
        Long known = jdbc.queryForObject("SELECT COUNT(*) FROM data_context_accounts WHERE context_id=?", Long.class, context.id());
        if (known != null && known > 0) return;
        for (UserApiKey legacy : keys.findByContextIdOrderByIdAsc(context.id())) {
            String legacyKey = crypto.decrypt(legacy.getEncryptedKey());
            if (legacyKey.equals(incomingKey)) continue;
            try {
                connectInternal(identity, legacyKey, false);
            } catch (ApiException exception) {
                // No unverified credential can authorize a merge. Keep its historical dataset intact.
                if (exception.getStatus() == HttpStatus.CONFLICT) throw exception;
            }
        }
    }

    /** Operator maintenance reconciliation of one existing credential with live account verification. */
    @Transactional
    public void reconcileCredential(Long credentialId) {
        UserApiKey credential = keys.findById(credentialId).orElse(null);
        if (credential != null) connectInternal(user(credential.getUser().getId()), crypto.decrypt(credential.getEncryptedKey()), false);
    }

    private void mergeContext(Context target, Context source) {
        entityManager.flush();
        merger.merge(target.ownerUserId(), source.ownerUserId());
        entityManager.clear();
        jdbc.update("UPDATE user_api_keys SET context_id = ? WHERE context_id = ?", target.id(), source.id());
        jdbc.update("UPDATE data_context_accounts SET context_id = ? WHERE context_id = ?", target.id(), source.id());
        jdbc.update("INSERT INTO user_data_contexts(user_id,context_id) SELECT user_id,? FROM user_data_contexts WHERE context_id = ? ON CONFLICT DO NOTHING", target.id(), source.id());
        jdbc.update("UPDATE data_contexts SET canonical_context_id = ? WHERE id = ?", target.id(), source.id());
        for (String cacheName : caches.getCacheNames()) {
            var cache = caches.getCache(cacheName);
            if (cache != null) cache.clear();
        }
    }

    @Transactional
    public JwtAuthentication select(Long identityId, Long credentialId) {
        UserApiKey key = keys.findById(credentialId).orElseThrow(() -> denied("API_KEY_NOT_REGISTERED", "Nexon API Key 미등록"));
        Context context = canonical(key.getContextId());
        if (!member(identityId, context.id())) throw denied("DATA_CONTEXT_ACCESS_DENIED", "데이터셋 접근 권한 없음");
        Connection connection = connect(user(identityId), crypto.decrypt(key.getEncryptedKey()));
        // An existing selected key keeps its ID, including legacy duplicate credentials.
        key.replaceKey(key.getEncryptedKey(), LocalDateTime.now(clock));
        key.assignContext(connection.principal().contextId(), key.getKeyFingerprint());
        keys.saveAndFlush(key);
        return new JwtAuthentication(connection.principal().userId(), identityId, connection.principal().contextId(), credentialId);
    }

    public JwtAuthentication defaultPrincipal(User identity) {
        Context context = own(identity);
        List<Long> connected = jdbc.query("SELECT context_id FROM user_data_contexts WHERE user_id = ? ORDER BY created_at,context_id",
                (rs, row) -> rs.getLong(1), identity.getId());
        for (Long id : connected) {
            Context candidate = canonical(id);
            var credential = preferredCredential(candidate.id());
            if (credential.isPresent()) return new JwtAuthentication(candidate.ownerUserId(), identity.getId(), candidate.id(), credential.get().getId());
        }
        return new JwtAuthentication(context.ownerUserId(), identity.getId(), context.id(), null);
    }

    private java.util.Optional<UserApiKey> preferredCredential(Long contextId) {
        return keys.findByContextIdOrderByIdAsc(contextId).stream()
                .filter(key -> key.getKeyStatus() == com.maple.utility.entity.ApiKeyStatus.ACTIVE)
                .max(Comparator.comparing(UserApiKey::getLastVerifiedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(UserApiKey::getId));
    }

    @Transactional(readOnly = true)
    public List<DataContextResponse> list(Long identityId) {
        return jdbc.query("SELECT DISTINCT c.id,c.owner_user_id,k.id credential_id,k.encrypted_key,k.key_status FROM user_data_contexts l JOIN data_contexts c ON c.id=l.context_id JOIN user_api_keys k ON k.context_id=c.id WHERE l.user_id=? AND c.canonical_context_id IS NULL ORDER BY c.id,k.id",
                (rs, row) -> new DataContextResponse(rs.getLong("id"), rs.getLong("owner_user_id"), rs.getLong("credential_id"), suffix(crypto.decrypt(rs.getString("encrypted_key"))), rs.getString("key_status")), identityId);
    }

    @Transactional
    public int backfillFingerprints() {
        lockConnections();
        int count = 0;
        for (UserApiKey key : keys.findByKeyFingerprintIsNullOrderByIdAsc()) {
            String fingerprint = ApiKeyFingerprint.of(crypto.decrypt(key.getEncryptedKey()));
            if (keys.findByKeyFingerprint(fingerprint).isEmpty()) {
                key.assignContext(key.getContextId(), fingerprint);
                keys.saveAndFlush(key); count++;
            }
        }
        return count;
    }

    private void link(Long identityId, Long contextId) {
        jdbc.update("INSERT INTO user_data_contexts(user_id,context_id) VALUES (?,?) ON CONFLICT DO NOTHING", identityId, contextId);
    }
    private boolean member(Long identityId, Long contextId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM user_data_contexts WHERE user_id=? AND context_id=?)", Boolean.class, identityId, contextId));
    }
    public User user(Long id) { return users.findById(id).orElseThrow(() -> denied("USER_NOT_FOUND", "사용자 없음")); }
    public static String normalize(String key) {
        String normalized = key == null ? "" : key.strip();
        if (normalized.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "API_KEY_REQUIRED", "Nexon API Key 입력 필요");
        return normalized;
    }
    public static String suffix(String key) { return key.length() > 4 ? key.substring(key.length()-4) : null; }
    private String firstName(NexonAccountCharacters response) { return response.characters().stream().map(c -> c.characterName()).findFirst().orElse(null); }
    private ApiException denied(String code, String message) { return new ApiException(HttpStatus.UNAUTHORIZED, code, message); }
    public record Context(Long id, Long ownerUserId, Long kakaoUserId, Long canonicalId) { }
    public record Connection(JwtAuthentication principal, UserApiKey credential, NexonAccountCharacters verified) { }
    public record DataContextResponse(Long contextId, Long ownerUserId, Long credentialId, String keySuffix, String status) { }
}
