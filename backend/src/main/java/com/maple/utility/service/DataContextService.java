package com.maple.utility.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    /** Maximum number of independent Nexon account datasets one Kakao identity may connect. */
    public static final int MAX_CONTEXTS_PER_KAKAO = 5;
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
            if (ApiKeyFingerprint.of(normalize(crypto.decrypt(legacy.getEncryptedKey()))).equals(fingerprint)) matches.add(legacy);
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
        if (identity.getOauthProvider() == OAuthProvider.KAKAO && exceedsContextLimit(identity.getId(), contexts)) {
            throw new ApiException(HttpStatus.CONFLICT, "DATA_CONTEXT_LIMIT_EXCEEDED",
                    "넥슨 계정은 최대 " + MAX_CONTEXTS_PER_KAKAO + "개까지 연결할 수 있습니다");
        }
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
        // Only a user-initiated connection counts as use; legacy reconciliation and maintenance must not.
        if (reconcileLegacy) markUsed(identity.getId(), target.id());
        // A merge clears the persistence context after JDBC updates. Reload credentials so saving a
        // detached pre-merge entity cannot restore an old context or lose duplicate assignments.
        matches = keys.findAllById(matches.stream().map(UserApiKey::getId).toList()).stream()
                .sorted(credentialOrder()).toList();
        UserApiKey credential = matches.isEmpty() ? null : matches.getFirst();
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

    /**
     * Detaches a Nexon account dataset from a Kakao identity while keeping every record.
     * Returns true when the detached dataset was the one the caller's session was using.
     */
    @Transactional
    public boolean unlink(Long identityId, Long contextId, Long sessionContextId) {
        lockConnections();
        User identity = user(identityId);
        if (identity.getOauthProvider() != OAuthProvider.KAKAO) {
            throw new ApiException(HttpStatus.FORBIDDEN, "DATA_CONTEXT_UNLINK_NOT_ALLOWED", "카카오 로그인에서만 연결 해제 가능");
        }
        Context target = canonical(contextId);
        if (!member(identityId, target.id()) || (target.kakaoUserId() != null && !target.kakaoUserId().equals(identityId))) {
            throw denied("DATA_CONTEXT_ACCESS_DENIED", "데이터셋 접근 권한 없음");
        }
        boolean wasCurrent = sessionContextId != null && canonical(sessionContextId).id().equals(target.id());
        entityManager.flush();
        if (target.ownerUserId().equals(identityId)) target = handOverOwnership(identity, target);
        Set<Long> chain = mergedInto(target.id());
        // The identity's own dataset was merged into this one; make it independent again, empty and reusable.
        List<Long> own = jdbc.query("SELECT id FROM data_contexts WHERE owner_user_id = ?", (rs, row) -> rs.getLong(1), identityId);
        for (Long ownId : own) {
            if (chain.contains(ownId) && !ownId.equals(target.id())) {
                jdbc.update("UPDATE data_contexts SET canonical_context_id = NULL WHERE id = ?", ownId);
                link(identityId, ownId);
            }
        }
        Set<Long> detach = new LinkedHashSet<>(chain);
        detach.removeAll(own);
        for (Long id : detach) {
            jdbc.update("DELETE FROM user_data_contexts WHERE user_id = ? AND context_id = ?", identityId, id);
            jdbc.update("UPDATE data_contexts SET kakao_user_id = NULL WHERE id = ? AND kakao_user_id = ?", id, identityId);
            jdbc.update("DELETE FROM user_api_keys WHERE user_id = ? AND context_id = ?", identityId, id);
        }
        entityManager.clear();
        clearCaches();
        own(identity);
        return wasCurrent;
    }

    /** A dataset owned by the Kakao identity itself moves its records to a dedicated key-owner user. */
    private Context handOverOwnership(User identity, Context current) {
        List<UserApiKey> credentials = keys.findByContextIdOrderByIdAsc(current.id());
        String oauthId = credentials.stream().map(UserApiKey::getKeyFingerprint).filter(Objects::nonNull).findFirst()
                .orElseGet(() -> credentials.isEmpty() ? null
                        : ApiKeyFingerprint.of(normalize(crypto.decrypt(credentials.getFirst().getEncryptedKey()))));
        // A fingerprint that already owns a dataset would have been merged on connect; never reuse it here.
        if (oauthId == null || users.findByOauthProviderAndOauthId(OAuthProvider.NEXON_APIKEY, oauthId).isPresent()) {
            oauthId = "detached-" + java.util.UUID.randomUUID();
        }
        User owner = users.save(User.create(OAuthProvider.NEXON_APIKEY, oauthId, null, identity.getNickname()));
        Context fresh = own(owner);
        entityManager.flush();
        merger.merge(owner.getId(), identity.getId());
        entityManager.clear();
        jdbc.update("UPDATE user_api_keys SET context_id = ? WHERE context_id = ?", fresh.id(), current.id());
        jdbc.update("UPDATE data_context_accounts SET context_id = ? WHERE context_id = ?", fresh.id(), current.id());
        jdbc.update("UPDATE data_contexts SET canonical_context_id = ? WHERE canonical_context_id = ?", fresh.id(), current.id());
        jdbc.update("UPDATE user_data_contexts SET context_id = ? WHERE context_id = ? AND user_id <> ?",
                fresh.id(), current.id(), identity.getId());
        jdbc.update("UPDATE data_contexts SET kakao_user_id = NULL WHERE id = ?", fresh.id());
        // The identity keeps its now empty own dataset, so later logins and connections still resolve.
        jdbc.update("UPDATE data_contexts SET kakao_user_id = ? WHERE id = ?", identity.getId(), current.id());
        return canonical(fresh.id());
    }

    /** The dataset itself plus every dataset whose canonical pointer chain ends at it. */
    private Set<Long> mergedInto(Long rootId) {
        Map<Long, Long> parents = new HashMap<>();
        jdbc.query("SELECT id, canonical_context_id FROM data_contexts WHERE canonical_context_id IS NOT NULL",
                rs -> { parents.put(rs.getLong(1), rs.getLong(2)); });
        Set<Long> chain = new LinkedHashSet<>();
        chain.add(rootId);
        boolean grew = true;
        while (grew) {
            grew = false;
            for (Map.Entry<Long, Long> entry : parents.entrySet()) {
                if (chain.contains(entry.getValue()) && chain.add(entry.getKey())) grew = true;
            }
        }
        return chain;
    }

    private void clearCaches() {
        for (String cacheName : caches.getCacheNames()) {
            var cache = caches.getCache(cacheName);
            if (cache != null) cache.clear();
        }
    }

    /** Canonical datasets of the identity that hold at least one credential, the same set the list endpoint shows. */
    private Set<Long> connectedContextIds(Long identityId) {
        return new LinkedHashSet<>(jdbc.query("""
                SELECT DISTINCT c.id
                FROM user_data_contexts l
                JOIN data_contexts c ON c.id = l.context_id
                JOIN user_api_keys k ON k.context_id = c.id
                WHERE l.user_id = ? AND c.canonical_context_id IS NULL
                """, (rs, row) -> rs.getLong(1), identityId));
    }

    /** Datasets merged into one target count once, so only unrelated connected datasets add to the total. */
    private boolean exceedsContextLimit(Long identityId, List<Context> incoming) {
        Set<Long> incomingIds = new LinkedHashSet<>();
        incoming.forEach(context -> incomingIds.add(context.id()));
        long others = connectedContextIds(identityId).stream().filter(id -> !incomingIds.contains(id)).count();
        return others + 1 > MAX_CONTEXTS_PER_KAKAO;
    }

    /**
     * Read-only check of whether a key can be connected, without linking, merging or saving anything.
     * The key is still verified against Nexon so the answer reflects the real account ownership.
     */
    @Transactional(readOnly = true)
    public Precheck precheck(User identity, String rawKey) {
        String key = normalize(rawKey);
        NexonAccountCharacters verified = nexon.verifyAccounts(identity.getId(), key);
        String fingerprint = ApiKeyFingerprint.of(key);
        Set<Long> ids = new LinkedHashSet<>();
        for (String accountId : verified.accountIds()) {
            jdbc.query("SELECT context_id FROM data_context_accounts WHERE account_id = ?",
                    (rs, row) -> rs.getLong(1), accountId).forEach(id -> ids.add(canonical(id).id()));
        }
        keys.findByKeyFingerprint(fingerprint).ifPresent(match -> ids.add(canonical(match.getContextId()).id()));
        for (UserApiKey legacy : keys.findByKeyFingerprintIsNullOrderByIdAsc()) {
            if (ApiKeyFingerprint.of(normalize(crypto.decrypt(legacy.getEncryptedKey()))).equals(fingerprint)) {
                ids.add(canonical(legacy.getContextId()).id());
            }
        }
        Set<Long> connected = connectedContextIds(identity.getId());
        boolean kakao = identity.getOauthProvider() == OAuthProvider.KAKAO;
        int count = connected.size();
        if (kakao) {
            for (Long id : ids) {
                Long owner = canonical(id).kakaoUserId();
                if (owner != null && !owner.equals(identity.getId())) {
                    return new Precheck(PrecheckStatus.LINKED_TO_OTHER_KAKAO, count, MAX_CONTEXTS_PER_KAKAO);
                }
            }
        }
        if (!ids.isEmpty() && connected.containsAll(ids)) {
            return new Precheck(PrecheckStatus.ALREADY_LINKED, count, MAX_CONTEXTS_PER_KAKAO);
        }
        if (kakao) {
            long others = connected.stream().filter(id -> !ids.contains(id)).count();
            if (others + 1 > MAX_CONTEXTS_PER_KAKAO) {
                return new Precheck(PrecheckStatus.LIMIT_EXCEEDED, count, MAX_CONTEXTS_PER_KAKAO);
            }
        }
        return new Precheck(PrecheckStatus.AVAILABLE, count, MAX_CONTEXTS_PER_KAKAO);
    }

    private void reconcileOwnLegacyKeys(User identity, String incomingKey) {
        Context context = own(identity);
        Long known = jdbc.queryForObject("SELECT COUNT(*) FROM data_context_accounts WHERE context_id=?", Long.class, context.id());
        if (known != null && known > 0) return;
        for (UserApiKey legacy : keys.findByContextIdOrderByIdAsc(context.id())) {
            String legacyKey = normalize(crypto.decrypt(legacy.getEncryptedKey()));
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
        // connect may merge contexts and clear managed entities; keep the refreshed fingerprint.
        key = keys.findById(credentialId).orElseThrow(() -> denied("API_KEY_NOT_REGISTERED", "Nexon API Key 미등록"));
        // An existing selected key keeps its ID, including legacy duplicate credentials.
        key.replaceKey(key.getEncryptedKey(), LocalDateTime.now(clock));
        key.assignContext(connection.principal().contextId(), key.getKeyFingerprint());
        keys.saveAndFlush(key);
        return new JwtAuthentication(connection.principal().userId(), identityId, connection.principal().contextId(), credentialId);
    }

    public JwtAuthentication defaultPrincipal(User identity) {
        Context context = own(identity);
        // The most recently used dataset wins; datasets never used keep the registration order.
        List<Long> connected = jdbc.query("SELECT context_id FROM user_data_contexts WHERE user_id = ? ORDER BY last_used_at DESC NULLS LAST,created_at,context_id",
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
        return list(identityId, null);
    }

    @Transactional(readOnly = true)
    public List<DataContextResponse> list(JwtAuthentication principal) {
        return list(principal.identityUserId(), principal.credentialId());
    }

    private List<DataContextResponse> list(Long identityId, Long currentCredentialId) {
        // Read all candidates once so credentials sharing a context receive the same representative.
        Collator korean = Collator.getInstance(Locale.KOREAN);
        Map<Long, Representative> representatives = new HashMap<>();
        jdbc.query("""
                SELECT c.id AS context_id, ch.id AS character_id, ch.ocid,
                       ch.character_name, ch.character_class, ch.world_name,
                       ch.character_level, ch.character_image, ch.is_favorite, ch.sort_order
                FROM user_data_contexts l
                JOIN data_contexts c ON c.id = l.context_id
                JOIN characters ch ON ch.user_id = c.owner_user_id
                WHERE l.user_id = ? AND c.canonical_context_id IS NULL
                """, rs -> {
            Long contextId = rs.getLong("context_id");
            Representative candidate = new Representative(rs.getLong("character_id"), rs.getString("ocid"),
                    rs.getString("character_name"), rs.getString("character_class"), rs.getString("world_name"),
                    (Integer) rs.getObject("character_level"), rs.getString("character_image"),
                    rs.getBoolean("is_favorite"), rs.getInt("sort_order"));
            representatives.merge(contextId, candidate,
                    (previous, next) -> representativeOrder(korean, previous, next) <= 0 ? previous : next);
        }, identityId);
        List<CredentialListing> credentials = jdbc.query("""
                SELECT DISTINCT c.id, c.owner_user_id, k.id AS credential_id,
                       k.encrypted_key, k.key_fingerprint, k.key_status, k.last_verified_at,
                       l.created_at AS linked_at
                FROM user_data_contexts l
                JOIN data_contexts c ON c.id = l.context_id
                JOIN user_api_keys k ON k.context_id = c.id
                WHERE l.user_id = ? AND c.canonical_context_id IS NULL
                ORDER BY c.id, k.id
                """, (rs, row) -> {
            Long contextId = rs.getLong("id");
            Long credentialId = rs.getLong("credential_id");
            String normalizedKey = normalize(crypto.decrypt(rs.getString("encrypted_key")));
            return new CredentialListing(contextId, rs.getLong("owner_user_id"), credentialId,
                    ApiKeyFingerprint.of(normalizedKey), rs.getString("key_fingerprint") != null,
                    suffix(normalizedKey), rs.getString("key_status"),
                    rs.getObject("last_verified_at", LocalDateTime.class),
                    // user_data_contexts timestamps are TIMESTAMPTZ; read them in the JVM zone (Asia/Seoul).
                    rs.getTimestamp("linked_at") == null ? null : rs.getTimestamp("linked_at").toLocalDateTime());
        }, identityId);
        Map<CredentialGroup, CredentialListing> unique = new LinkedHashMap<>();
        Set<CredentialGroup> currentGroups = new LinkedHashSet<>();
        for (CredentialListing credential : credentials) {
            CredentialGroup group = new CredentialGroup(credential.contextId(), credential.fingerprint());
            unique.merge(group, credential, (previous, next) ->
                    listingOrder().compare(previous, next) <= 0 ? previous : next);
            if (Objects.equals(credential.id(), currentCredentialId)) currentGroups.add(group);
        }
        // The dataset in use comes first, then datasets in the order they were connected.
        Set<Long> currentContexts = new LinkedHashSet<>();
        currentGroups.forEach(group -> currentContexts.add(group.contextId()));
        Comparator<Map.Entry<CredentialGroup, CredentialListing>> displayOrder = Comparator
                .comparingInt((Map.Entry<CredentialGroup, CredentialListing> entry) ->
                        currentContexts.contains(entry.getValue().contextId()) ? 0 : 1)
                .thenComparing(entry -> entry.getValue().linkedAt(), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(entry -> entry.getValue().contextId());
        return unique.entrySet().stream().sorted(displayOrder).map(entry -> {
            CredentialListing credential = entry.getValue();
            Representative representative = representatives.get(credential.contextId());
            return new DataContextResponse(credential.contextId(), credential.ownerId(), credential.id(),
                    credential.suffix(), credential.status(), credential.verifiedAt(), credential.linkedAt(),
                    currentGroups.contains(entry.getKey()),
                    representative == null ? null : representative.name(),
                    representative == null ? null : representative.characterClass(),
                    representative == null ? null : representative.worldName(),
                    representative == null ? null : representative.level(),
                    representative == null ? null : representative.image());
        }).toList();
    }

    private Comparator<UserApiKey> credentialOrder() {
        return Comparator.comparingInt((UserApiKey key) -> key.getKeyFingerprint() == null ? 1 : 0)
                .thenComparing(UserApiKey::getId);
    }

    private Comparator<CredentialListing> listingOrder() {
        return Comparator.comparingInt((CredentialListing key) -> key.fingerprinted() ? 0 : 1)
                .thenComparing(CredentialListing::id);
    }

    private int representativeOrder(Collator korean, Representative left, Representative right) {
        if (left.favorite() != right.favorite()) return left.favorite() ? -1 : 1;
        if (left.favorite()) {
            int order = Integer.compare(left.sortOrder(), right.sortOrder());
            if (order != 0) return order;
            return Long.compare(left.id(), right.id());
        }
        int level = Comparator.nullsLast(Comparator.<Integer>reverseOrder()).compare(left.level(), right.level());
        if (level != 0) return level;
        int name = korean.compare(left.name(), right.name());
        if (name != 0) return name;
        return left.ocid().compareTo(right.ocid());
    }

    @Transactional
    public int backfillFingerprints() {
        lockConnections();
        int count = 0;
        for (UserApiKey key : keys.findByKeyFingerprintIsNullOrderByIdAsc()) {
            String fingerprint = ApiKeyFingerprint.of(normalize(crypto.decrypt(key.getEncryptedKey())));
            if (keys.findByKeyFingerprint(fingerprint).isEmpty()) {
                key.assignContext(key.getContextId(), fingerprint);
                keys.saveAndFlush(key); count++;
            }
        }
        return count;
    }

    private void markUsed(Long identityId, Long contextId) {
        jdbc.update("UPDATE user_data_contexts SET last_used_at = ?, updated_at = ? WHERE user_id = ? AND context_id = ?",
                java.sql.Timestamp.valueOf(LocalDateTime.now(clock)), java.sql.Timestamp.valueOf(LocalDateTime.now(clock)),
                identityId, contextId);
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
    public enum PrecheckStatus { AVAILABLE, ALREADY_LINKED, LINKED_TO_OTHER_KAKAO, LIMIT_EXCEEDED }
    public record Precheck(PrecheckStatus status, int connectedCount, int maxCount) { }
    public record Context(Long id, Long ownerUserId, Long kakaoUserId, Long canonicalId) { }
    public record Connection(JwtAuthentication principal, UserApiKey credential, NexonAccountCharacters verified) { }
    private record CredentialGroup(Long contextId, String fingerprint) { }
    private record CredentialListing(Long contextId, Long ownerId, Long id, String fingerprint,
                                     boolean fingerprinted, String suffix, String status, LocalDateTime verifiedAt,
                                     LocalDateTime linkedAt) { }
    private record Representative(Long id, String ocid, String name, String characterClass, String worldName,
                                  Integer level, String image, boolean favorite, int sortOrder) { }
    public record DataContextResponse(Long contextId, Long ownerUserId, Long credentialId, String keySuffix,
                                      String status, LocalDateTime lastVerifiedAt, LocalDateTime linkedAt, boolean current,
                                      String representativeCharacterName, String characterClass, String worldName,
                                      Integer characterLevel, String characterImage) { }
}
