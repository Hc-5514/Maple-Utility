-- 카카오 로그인 시 마지막으로 사용한 넥슨 계정(데이터셋)을 기본 세션으로 선택하기 위한 마지막 사용 시각.
-- NULL은 사용 이력 없음이며 기존 등록순 동작을 유지한다.
ALTER TABLE user_data_contexts ADD COLUMN last_used_at TIMESTAMPTZ;

CREATE INDEX idx_user_data_contexts_user_last_used ON user_data_contexts (user_id, last_used_at DESC);
