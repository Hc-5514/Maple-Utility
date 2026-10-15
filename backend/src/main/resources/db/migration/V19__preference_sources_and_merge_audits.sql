SET LOCAL TIME ZONE 'Asia/Seoul';

-- FAVORITE records identify characters; SORT_ORDER and WORLD_SELECTION records
-- identify data-owner users. The application manages these polymorphic IDs.
CREATE TABLE data_preference_sources (
    setting_type VARCHAR(30) NOT NULL,
    record_id BIGINT NOT NULL,
    source_user_id BIGINT NOT NULL,
    state VARCHAR(20) NOT NULL,
    saved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (setting_type, record_id),
    CONSTRAINT fk_data_preference_sources_source_user_id FOREIGN KEY (source_user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_data_preference_sources_setting_type CHECK (setting_type IN ('FAVORITE', 'SORT_ORDER', 'WORLD_SELECTION')),
    CONSTRAINT chk_data_preference_sources_state CHECK (state IN ('UNKNOWN', 'LEGACY', 'EXPLICIT')),
    CONSTRAINT chk_data_preference_sources_explicit_saved_at CHECK (state <> 'EXPLICIT' OR saved_at IS NOT NULL)
);

CREATE INDEX idx_data_preference_sources_source_user_id ON data_preference_sources (source_user_id);

-- A legacy true value is evidence worth preserving, but false values cannot
-- distinguish an explicit removal from an untouched synchronization default.
-- General row timestamps are deliberately not treated as preference save times.
INSERT INTO data_preference_sources (setting_type, record_id, source_user_id, state)
SELECT 'FAVORITE', c.id, COALESCE(p.source_user_id, c.user_id),
       CASE WHEN c.is_favorite THEN 'LEGACY' ELSE 'UNKNOWN' END
FROM characters c
LEFT JOIN data_merge_provenance p ON p.table_name = 'characters' AND p.record_id = c.id;

INSERT INTO data_preference_sources (setting_type, record_id, source_user_id, state)
SELECT 'SORT_ORDER', id, id, 'UNKNOWN'
FROM users;

INSERT INTO data_preference_sources (setting_type, record_id, source_user_id, state)
SELECT 'WORLD_SELECTION', u.id, COALESCE(p.source_user_id, u.id),
       CASE WHEN EXISTS (SELECT 1 FROM user_selected_worlds w WHERE w.user_id = u.id)
            THEN 'LEGACY' ELSE 'UNKNOWN' END
FROM users u
LEFT JOIN data_merge_provenance p ON p.table_name = 'user_selected_world_sets' AND p.record_id = u.id;

-- Only preference values, provenance, merge decisions and record counts belong
-- in these snapshots. Credential material must never be supplied by callers.
CREATE TABLE data_context_merge_audits (
    id BIGSERIAL PRIMARY KEY,
    target_user_id BIGINT NOT NULL,
    source_user_id BIGINT NOT NULL,
    before_state JSONB NOT NULL,
    after_state JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_data_context_merge_audits_target_user_id FOREIGN KEY (target_user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_data_context_merge_audits_source_user_id FOREIGN KEY (source_user_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX idx_data_context_merge_audits_target_created ON data_context_merge_audits (target_user_id, created_at, id);
CREATE INDEX idx_data_context_merge_audits_source_created ON data_context_merge_audits (source_user_id, created_at, id);

CREATE TRIGGER trg_data_preference_sources_updated_at
    BEFORE UPDATE ON data_preference_sources FOR EACH ROW EXECUTE FUNCTION update_updated_at();
CREATE TRIGGER trg_data_context_merge_audits_updated_at
    BEFORE UPDATE ON data_context_merge_audits FOR EACH ROW EXECUTE FUNCTION update_updated_at();
