CREATE TABLE scheduler_monthly_finalizations (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    character_id BIGINT NOT NULL REFERENCES characters(id) ON DELETE CASCADE,
    month_start_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    last_error TEXT,
    last_attempt_at TIMESTAMP,
    finalized_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (character_id, month_start_date),
    CHECK (EXTRACT(DAY FROM month_start_date) = 1),
    CHECK (status IN ('PENDING', 'RETRY', 'NO_DATA', 'FINALIZED', 'EXPIRED'))
);
CREATE INDEX idx_scheduler_monthly_finalizations_user_status_month
    ON scheduler_monthly_finalizations(user_id, status, month_start_date);
CREATE TRIGGER trg_scheduler_monthly_finalizations_updated_at
    BEFORE UPDATE ON scheduler_monthly_finalizations FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TABLE scheduler_monthly_boss_snapshots (
    finalization_id BIGINT NOT NULL REFERENCES scheduler_monthly_finalizations(id) ON DELETE CASCADE,
    boss_id BIGINT NOT NULL REFERENCES boss_master(id) ON DELETE RESTRICT,
    is_completed BOOLEAN NOT NULL,
    PRIMARY KEY (finalization_id, boss_id)
);
