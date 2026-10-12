CREATE TABLE scheduler_week_finalizations (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    character_id BIGINT NOT NULL,
    week_start_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    last_attempt_at TIMESTAMP,
    finalized_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_scheduler_week_finalizations_user_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_scheduler_week_finalizations_character_id FOREIGN KEY (character_id) REFERENCES characters (id) ON DELETE CASCADE,
    CONSTRAINT uk_scheduler_week_finalizations_character_week UNIQUE (character_id, week_start_date),
    CONSTRAINT chk_scheduler_week_finalizations_status CHECK (status IN ('PENDING', 'FINALIZED', 'NO_DATA', 'RETRY', 'EXPIRED')),
    CONSTRAINT chk_scheduler_week_finalizations_attempts CHECK (attempts >= 0)
);

CREATE INDEX idx_scheduler_week_finalizations_user_status_week
    ON scheduler_week_finalizations (user_id, status, week_start_date);

CREATE TRIGGER trg_scheduler_week_finalizations_updated_at
    BEFORE UPDATE ON scheduler_week_finalizations
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();
