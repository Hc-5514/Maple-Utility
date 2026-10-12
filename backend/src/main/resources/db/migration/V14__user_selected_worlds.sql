CREATE TABLE user_selected_worlds (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    world_name VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_selected_worlds_user_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_user_selected_worlds_user_world UNIQUE (user_id, world_name)
);

CREATE INDEX idx_user_selected_worlds_user_id ON user_selected_worlds (user_id);

CREATE TRIGGER trg_user_selected_worlds_updated_at
    BEFORE UPDATE ON user_selected_worlds
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();
