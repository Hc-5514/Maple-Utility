CREATE TABLE hunting_grounds (
    id BIGSERIAL PRIMARY KEY,
    region_name VARCHAR(100) NOT NULL,
    map_name VARCHAR(150) NOT NULL,
    max_monster_level INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_hunting_grounds_region_map UNIQUE (region_name, map_name),
    CONSTRAINT chk_hunting_grounds_max_monster_level CHECK (max_monster_level > 0)
);

CREATE INDEX idx_hunting_grounds_region_level_name
    ON hunting_grounds (region_name, max_monster_level DESC, map_name DESC);

CREATE TABLE user_hunting_ground_favorites (
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    hunting_ground_id BIGINT NOT NULL REFERENCES hunting_grounds (id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, hunting_ground_id)
);

ALTER TABLE hunting_records
    ADD COLUMN hunting_ground_id BIGINT REFERENCES hunting_grounds (id) ON DELETE SET NULL;

CREATE INDEX idx_hunting_records_character_date_ground
    ON hunting_records (character_id, record_date DESC, id DESC)
    WHERE hunting_ground_id IS NOT NULL;

CREATE TRIGGER trg_hunting_grounds_updated_at
    BEFORE UPDATE ON hunting_grounds
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();
