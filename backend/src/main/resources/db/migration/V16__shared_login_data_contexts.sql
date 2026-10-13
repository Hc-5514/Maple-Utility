SET LOCAL TIME ZONE 'Asia/Seoul';

-- Existing user foreign keys continue to identify the data owner, independently
-- of the authenticated user and the credential selected by a login session.
CREATE TABLE data_contexts (
    id BIGSERIAL PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    kakao_user_id BIGINT,
    canonical_context_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_data_contexts_owner_user_id UNIQUE (owner_user_id),
    CONSTRAINT fk_data_contexts_owner_user_id FOREIGN KEY (owner_user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_data_contexts_kakao_user_id FOREIGN KEY (kakao_user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_data_contexts_canonical_context_id FOREIGN KEY (canonical_context_id) REFERENCES data_contexts (id) ON DELETE RESTRICT,
    CONSTRAINT chk_data_contexts_canonical_context_id CHECK (canonical_context_id IS NULL OR canonical_context_id <> id)
);

CREATE INDEX idx_data_contexts_kakao_user_id ON data_contexts (kakao_user_id) WHERE kakao_user_id IS NOT NULL;
CREATE INDEX idx_data_contexts_canonical_context_id ON data_contexts (canonical_context_id) WHERE canonical_context_id IS NOT NULL;

CREATE TABLE data_context_accounts (
    account_id VARCHAR(200) PRIMARY KEY,
    context_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_data_context_accounts_context_id FOREIGN KEY (context_id) REFERENCES data_contexts (id) ON DELETE RESTRICT
);

CREATE INDEX idx_data_context_accounts_context_id ON data_context_accounts (context_id);

CREATE TABLE user_data_contexts (
    user_id BIGINT NOT NULL,
    context_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, context_id),
    CONSTRAINT fk_user_data_contexts_user_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_data_contexts_context_id FOREIGN KEY (context_id) REFERENCES data_contexts (id) ON DELETE CASCADE
);

CREATE INDEX idx_user_data_contexts_context_id ON user_data_contexts (context_id);

-- Source identity survives movement of records between data owners. The merger
-- registers provenance before moving a row; record IDs belong to each table.
CREATE TABLE data_merge_provenance (
    table_name VARCHAR(100) NOT NULL,
    record_id BIGINT NOT NULL,
    source_user_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (table_name, record_id),
    CONSTRAINT fk_data_merge_provenance_source_user_id FOREIGN KEY (source_user_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX idx_data_merge_provenance_source_user_id ON data_merge_provenance (source_user_id);

INSERT INTO data_contexts (owner_user_id, kakao_user_id, created_at, updated_at)
SELECT id, CASE WHEN oauth_provider = 'KAKAO' THEN id END,
       created_at AT TIME ZONE 'Asia/Seoul', updated_at AT TIME ZONE 'Asia/Seoul'
FROM users
ORDER BY id;

INSERT INTO user_data_contexts (user_id, context_id, created_at, updated_at)
SELECT owner_user_id, id, created_at, updated_at
FROM data_contexts;

ALTER TABLE user_api_keys
    ADD COLUMN context_id BIGINT,
    ADD COLUMN key_fingerprint CHAR(64);

UPDATE user_api_keys k
SET context_id = c.id
FROM data_contexts c
WHERE c.owner_user_id = k.user_id;

-- Legacy ciphertext is deliberately untouched. Fingerprints and account IDs
-- require decryption/server validation and are populated by the application.
ALTER TABLE user_api_keys
    ALTER COLUMN context_id SET NOT NULL,
    DROP CONSTRAINT uk_user_api_keys_user_id,
    ADD CONSTRAINT fk_user_api_keys_context_id FOREIGN KEY (context_id) REFERENCES data_contexts (id) ON DELETE RESTRICT,
    ADD CONSTRAINT uk_user_api_keys_key_fingerprint UNIQUE (key_fingerprint);

CREATE INDEX idx_user_api_keys_user_id ON user_api_keys (user_id);
CREATE INDEX idx_user_api_keys_context_id ON user_api_keys (context_id);

CREATE TRIGGER trg_data_contexts_updated_at BEFORE UPDATE ON data_contexts FOR EACH ROW EXECUTE FUNCTION update_updated_at();
CREATE TRIGGER trg_data_context_accounts_updated_at BEFORE UPDATE ON data_context_accounts FOR EACH ROW EXECUTE FUNCTION update_updated_at();
CREATE TRIGGER trg_user_data_contexts_updated_at BEFORE UPDATE ON user_data_contexts FOR EACH ROW EXECUTE FUNCTION update_updated_at();
CREATE TRIGGER trg_data_merge_provenance_updated_at BEFORE UPDATE ON data_merge_provenance FOR EACH ROW EXECUTE FUNCTION update_updated_at();

-- Preserve old application INSERTs during the DB-first rollout. This only
-- creates the user's own unverified data context; account IDs and fingerprints
-- still require server-side API verification.
CREATE FUNCTION ensure_user_data_context(p_user_id BIGINT)
RETURNS BIGINT AS $$
DECLARE
    own_context_id BIGINT;
BEGIN
    INSERT INTO data_contexts (owner_user_id, kakao_user_id, created_at, updated_at)
    SELECT id, CASE WHEN oauth_provider = 'KAKAO' THEN id END,
           created_at AT TIME ZONE 'Asia/Seoul', updated_at AT TIME ZONE 'Asia/Seoul'
    FROM users
    WHERE id = p_user_id
    ON CONFLICT (owner_user_id) DO NOTHING;

    SELECT id INTO own_context_id FROM data_contexts WHERE owner_user_id = p_user_id;

    INSERT INTO user_data_contexts (user_id, context_id, created_at, updated_at)
    SELECT p_user_id, id, created_at, updated_at
    FROM data_contexts
    WHERE id = own_context_id
    ON CONFLICT (user_id, context_id) DO NOTHING;

    RETURN own_context_id;
END;
$$ LANGUAGE plpgsql;

CREATE FUNCTION bootstrap_inserted_user_data_context()
RETURNS TRIGGER AS $$
BEGIN
    PERFORM ensure_user_data_context(NEW.id);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE FUNCTION populate_legacy_api_key_context()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.context_id IS NULL THEN
        NEW.context_id := ensure_user_data_context(NEW.user_id);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_bootstrap_data_context
    AFTER INSERT ON users FOR EACH ROW EXECUTE FUNCTION bootstrap_inserted_user_data_context();
CREATE TRIGGER trg_user_api_keys_populate_legacy_context
    BEFORE INSERT ON user_api_keys FOR EACH ROW EXECUTE FUNCTION populate_legacy_api_key_context();
