-- 보스·기간 기록별 결정 가격 수정값 (NULL이면 boss_master 기본 가격 사용)
ALTER TABLE boss_period_entries
    ADD COLUMN crystal_price BIGINT;

ALTER TABLE boss_period_entries
    ADD CONSTRAINT chk_boss_period_entries_crystal_price CHECK (crystal_price IS NULL OR crystal_price >= 0);
