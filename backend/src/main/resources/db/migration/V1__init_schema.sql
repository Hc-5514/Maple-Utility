CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    oauth_provider VARCHAR(20) NOT NULL,
    oauth_id VARCHAR(100) NOT NULL,
    email VARCHAR(255),
    nickname VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_users_oauth_provider_oauth_id UNIQUE (oauth_provider, oauth_id),
    CONSTRAINT chk_users_oauth_provider CHECK (oauth_provider IN ('KAKAO', 'NEXON'))
);

CREATE TABLE user_api_keys (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    encrypted_key TEXT NOT NULL,
    key_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_verified_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_api_keys_user_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_user_api_keys_user_id UNIQUE (user_id),
    CONSTRAINT chk_user_api_keys_key_status CHECK (key_status IN ('ACTIVE', 'INVALID', 'EXPIRED'))
);

CREATE TABLE characters (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    ocid VARCHAR(100) NOT NULL,
    character_name VARCHAR(50) NOT NULL,
    world_name VARCHAR(30),
    character_class VARCHAR(50),
    character_level INTEGER,
    character_image TEXT,
    guild_name VARCHAR(50),
    is_favorite BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_characters_user_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_characters_user_id_ocid UNIQUE (user_id, ocid)
);

CREATE INDEX idx_characters_user_id_is_favorite ON characters (user_id, is_favorite);

CREATE TABLE boss_master (
    id BIGSERIAL PRIMARY KEY,
    boss_name VARCHAR(50) NOT NULL,
    difficulty VARCHAR(20) NOT NULL,
    reset_period VARCHAR(10) NOT NULL,
    crystal_price BIGINT NOT NULL DEFAULT 0,
    boss_image VARCHAR(255),
    sort_order INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_boss_master_boss_name_difficulty UNIQUE (boss_name, difficulty),
    CONSTRAINT chk_boss_master_difficulty CHECK (difficulty IN ('EASY', 'NORMAL', 'HARD', 'CHAOS', 'EXTREME')),
    CONSTRAINT chk_boss_master_reset_period CHECK (reset_period IN ('WEEKLY', 'MONTHLY')),
    CONSTRAINT chk_boss_master_crystal_price CHECK (crystal_price >= 0)
);

CREATE INDEX idx_boss_master_reset_period_sort_order ON boss_master (reset_period, sort_order);

CREATE TABLE scheduler_daily_records (
    id BIGSERIAL PRIMARY KEY,
    character_id BIGINT NOT NULL,
    record_date DATE NOT NULL,
    content_name VARCHAR(100) NOT NULL,
    completed_count INTEGER NOT NULL DEFAULT 0,
    total_count INTEGER NOT NULL DEFAULT 1,
    synced_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_scheduler_daily_records_character_id FOREIGN KEY (character_id) REFERENCES characters (id) ON DELETE CASCADE,
    CONSTRAINT uk_scheduler_daily_records_character_date_content UNIQUE (character_id, record_date, content_name),
    CONSTRAINT chk_scheduler_daily_records_completed_count CHECK (completed_count >= 0),
    CONSTRAINT chk_scheduler_daily_records_total_count CHECK (total_count >= 0)
);

CREATE INDEX idx_scheduler_daily_records_character_id_record_date ON scheduler_daily_records (character_id, record_date);
CREATE INDEX idx_scheduler_daily_records_record_date ON scheduler_daily_records (record_date);

CREATE TABLE scheduler_weekly_records (
    id BIGSERIAL PRIMARY KEY,
    character_id BIGINT NOT NULL,
    week_start_date DATE NOT NULL,
    content_name VARCHAR(100) NOT NULL,
    is_completed BOOLEAN NOT NULL DEFAULT FALSE,
    score INTEGER,
    synced_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_scheduler_weekly_records_character_id FOREIGN KEY (character_id) REFERENCES characters (id) ON DELETE CASCADE,
    CONSTRAINT uk_scheduler_weekly_records_character_week_content UNIQUE (character_id, week_start_date, content_name),
    CONSTRAINT chk_scheduler_weekly_records_score CHECK (score IS NULL OR score >= 0)
);

CREATE INDEX idx_scheduler_weekly_records_character_id_week_start_date ON scheduler_weekly_records (character_id, week_start_date);

CREATE TABLE scheduler_boss_records (
    id BIGSERIAL PRIMARY KEY,
    character_id BIGINT NOT NULL,
    boss_id BIGINT NOT NULL,
    record_date DATE NOT NULL,
    reset_period VARCHAR(10) NOT NULL,
    is_completed BOOLEAN NOT NULL DEFAULT FALSE,
    synced_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_scheduler_boss_records_character_id FOREIGN KEY (character_id) REFERENCES characters (id) ON DELETE CASCADE,
    CONSTRAINT fk_scheduler_boss_records_boss_id FOREIGN KEY (boss_id) REFERENCES boss_master (id) ON DELETE RESTRICT,
    CONSTRAINT uk_scheduler_boss_records_character_boss_date UNIQUE (character_id, boss_id, record_date),
    CONSTRAINT chk_scheduler_boss_records_reset_period CHECK (reset_period IN ('WEEKLY', 'MONTHLY'))
);

CREATE INDEX idx_scheduler_boss_records_character_date_reset ON scheduler_boss_records (character_id, record_date, reset_period);

CREATE TABLE boss_drop_items (
    id BIGSERIAL PRIMARY KEY,
    boss_id BIGINT NOT NULL,
    item_name VARCHAR(100) NOT NULL,
    item_image VARCHAR(255),
    item_description TEXT,
    drop_rate_tier VARCHAR(20),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_boss_drop_items_boss_id FOREIGN KEY (boss_id) REFERENCES boss_master (id) ON DELETE CASCADE,
    CONSTRAINT chk_boss_drop_items_drop_rate_tier CHECK (drop_rate_tier IS NULL OR drop_rate_tier IN ('HIGH', 'NORMAL', 'LOW'))
);

CREATE INDEX idx_boss_drop_items_boss_id ON boss_drop_items (boss_id);
CREATE UNIQUE INDEX uk_boss_drop_items_boss_id_item_name ON boss_drop_items (boss_id, item_name);

CREATE TABLE boss_item_acquisitions (
    id BIGSERIAL PRIMARY KEY,
    character_id BIGINT NOT NULL,
    boss_drop_item_id BIGINT NOT NULL,
    acquired_date DATE NOT NULL,
    memo VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_boss_item_acquisitions_character_id FOREIGN KEY (character_id) REFERENCES characters (id) ON DELETE CASCADE,
    CONSTRAINT fk_boss_item_acquisitions_boss_drop_item_id FOREIGN KEY (boss_drop_item_id) REFERENCES boss_drop_items (id) ON DELETE RESTRICT
);

CREATE INDEX idx_boss_item_acquisitions_character_id_acquired_date ON boss_item_acquisitions (character_id, acquired_date);

CREATE TABLE hunting_records (
    id BIGSERIAL PRIMARY KEY,
    character_id BIGINT NOT NULL,
    record_date DATE NOT NULL,
    meso_earned BIGINT NOT NULL DEFAULT 0,
    sol_erda_earned INTEGER NOT NULL DEFAULT 0,
    play_duration_min INTEGER,
    hunting_ground VARCHAR(100),
    memo TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_hunting_records_character_id FOREIGN KEY (character_id) REFERENCES characters (id) ON DELETE CASCADE,
    CONSTRAINT uk_hunting_records_character_id_record_date UNIQUE (character_id, record_date),
    CONSTRAINT chk_hunting_records_meso_earned CHECK (meso_earned >= 0),
    CONSTRAINT chk_hunting_records_sol_erda_earned CHECK (sol_erda_earned >= 0),
    CONSTRAINT chk_hunting_records_play_duration_min CHECK (play_duration_min IS NULL OR play_duration_min >= 0)
);

CREATE TABLE data_sync_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    sync_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    api_calls_used INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_data_sync_logs_user_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT chk_data_sync_logs_sync_type CHECK (sync_type IN ('SCHEDULER_BATCH', 'SCHEDULER_REALTIME', 'CHARACTER_SYNC')),
    CONSTRAINT chk_data_sync_logs_status CHECK (status IN ('STARTED', 'COMPLETED', 'FAILED')),
    CONSTRAINT chk_data_sync_logs_api_calls_used CHECK (api_calls_used >= 0)
);

CREATE INDEX idx_data_sync_logs_user_id_started_at ON data_sync_logs (user_id, started_at);

CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION update_updated_at();
CREATE TRIGGER trg_user_api_keys_updated_at BEFORE UPDATE ON user_api_keys FOR EACH ROW EXECUTE FUNCTION update_updated_at();
CREATE TRIGGER trg_characters_updated_at BEFORE UPDATE ON characters FOR EACH ROW EXECUTE FUNCTION update_updated_at();
CREATE TRIGGER trg_scheduler_daily_records_updated_at BEFORE UPDATE ON scheduler_daily_records FOR EACH ROW EXECUTE FUNCTION update_updated_at();
CREATE TRIGGER trg_scheduler_weekly_records_updated_at BEFORE UPDATE ON scheduler_weekly_records FOR EACH ROW EXECUTE FUNCTION update_updated_at();
CREATE TRIGGER trg_scheduler_boss_records_updated_at BEFORE UPDATE ON scheduler_boss_records FOR EACH ROW EXECUTE FUNCTION update_updated_at();
CREATE TRIGGER trg_boss_master_updated_at BEFORE UPDATE ON boss_master FOR EACH ROW EXECUTE FUNCTION update_updated_at();
CREATE TRIGGER trg_boss_drop_items_updated_at BEFORE UPDATE ON boss_drop_items FOR EACH ROW EXECUTE FUNCTION update_updated_at();
CREATE TRIGGER trg_boss_item_acquisitions_updated_at BEFORE UPDATE ON boss_item_acquisitions FOR EACH ROW EXECUTE FUNCTION update_updated_at();
CREATE TRIGGER trg_hunting_records_updated_at BEFORE UPDATE ON hunting_records FOR EACH ROW EXECUTE FUNCTION update_updated_at();
CREATE TRIGGER trg_data_sync_logs_updated_at BEFORE UPDATE ON data_sync_logs FOR EACH ROW EXECUTE FUNCTION update_updated_at();
