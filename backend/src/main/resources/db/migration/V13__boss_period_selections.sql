CREATE TABLE boss_period_selections (
    id BIGSERIAL PRIMARY KEY,
    character_id BIGINT NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    boss_id BIGINT NOT NULL REFERENCES boss_master (id) ON DELETE RESTRICT,
    reset_period VARCHAR(10) NOT NULL,
    period_start DATE NOT NULL,
    is_visible BOOLEAN NOT NULL,
    is_completed BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_boss_period_selections_character_boss_period UNIQUE (character_id, boss_id, period_start),
    CONSTRAINT chk_boss_period_selections_reset_period CHECK (reset_period IN ('WEEKLY', 'MONTHLY'))
);

CREATE INDEX idx_boss_period_selections_character_period
    ON boss_period_selections (character_id, period_start, reset_period);

CREATE TRIGGER trg_boss_period_selections_updated_at
    BEFORE UPDATE ON boss_period_selections
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();
