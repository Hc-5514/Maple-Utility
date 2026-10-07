CREATE TABLE boss_period_entries (
    id BIGSERIAL PRIMARY KEY,
    character_id BIGINT NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    boss_id BIGINT NOT NULL REFERENCES boss_master (id) ON DELETE RESTRICT,
    reset_period VARCHAR(10) NOT NULL,
    period_start DATE NOT NULL,
    party_size SMALLINT NOT NULL DEFAULT 1,
    saved_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_boss_period_entries_character_boss_period UNIQUE (character_id, boss_id, period_start),
    CONSTRAINT chk_boss_period_entries_reset_period CHECK (reset_period IN ('WEEKLY', 'MONTHLY')),
    CONSTRAINT chk_boss_period_entries_party_size CHECK (party_size BETWEEN 1 AND 6)
);

CREATE INDEX idx_boss_period_entries_character_period
    ON boss_period_entries (character_id, period_start DESC);

CREATE TRIGGER trg_boss_period_entries_updated_at
    BEFORE UPDATE ON boss_period_entries
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

ALTER TABLE boss_item_acquisitions
    ADD COLUMN boss_period_entry_id BIGINT REFERENCES boss_period_entries (id) ON DELETE CASCADE,
    ADD COLUMN quantity INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN meso_amount BIGINT;

ALTER TABLE boss_item_acquisitions
    ADD CONSTRAINT chk_boss_item_acquisitions_quantity CHECK (quantity > 0),
    ADD CONSTRAINT chk_boss_item_acquisitions_meso_amount CHECK (meso_amount IS NULL OR meso_amount >= 0);

-- Keep old acquisition IDs and dates intact; only attach their reset-period owner.
WITH historic AS (
    SELECT a.character_id, d.boss_id, b.reset_period,
           CASE WHEN b.reset_period = 'MONTHLY'
                THEN date_trunc('month', a.acquired_date)::date
                ELSE a.acquired_date - ((EXTRACT(DOW FROM a.acquired_date)::integer + 3) % 7)
           END AS period_start,
           MIN(a.created_at) AS first_saved_at
    FROM boss_item_acquisitions a
    JOIN boss_drop_items d ON d.id = a.boss_drop_item_id
    JOIN boss_master b ON b.id = d.boss_id
    GROUP BY a.character_id, d.boss_id, b.reset_period,
             CASE WHEN b.reset_period = 'MONTHLY'
                  THEN date_trunc('month', a.acquired_date)::date
                  ELSE a.acquired_date - ((EXTRACT(DOW FROM a.acquired_date)::integer + 3) % 7)
             END
)
INSERT INTO boss_period_entries (character_id, boss_id, reset_period, period_start, saved_at)
SELECT character_id, boss_id, reset_period, period_start, first_saved_at FROM historic;

UPDATE boss_item_acquisitions a
SET boss_period_entry_id = p.id
FROM boss_drop_items d, boss_master b, boss_period_entries p
WHERE a.boss_drop_item_id = d.id
  AND d.boss_id = b.id
  AND p.character_id = a.character_id
  AND p.boss_id = b.id
  AND p.period_start = CASE WHEN b.reset_period = 'MONTHLY'
                            THEN date_trunc('month', a.acquired_date)::date
                            ELSE a.acquired_date - ((EXTRACT(DOW FROM a.acquired_date)::integer + 3) % 7)
                       END;

CREATE INDEX idx_boss_item_acquisitions_period_entry
    ON boss_item_acquisitions (boss_period_entry_id);
