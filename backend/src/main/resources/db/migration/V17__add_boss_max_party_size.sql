-- 보스·난이도별 최대 파티 인원 (기본 6인)
ALTER TABLE boss_master
    ADD COLUMN max_party_size SMALLINT NOT NULL DEFAULT 6;

ALTER TABLE boss_master
    ADD CONSTRAINT ck_boss_master_max_party_size CHECK (max_party_size BETWEEN 1 AND 6);

UPDATE boss_master
SET max_party_size = 3
WHERE boss_name IN ('최초의 대적자', '카링', '림보', '발드릭스', '유피테르', '벨로나');

UPDATE boss_master
SET max_party_size = 2
WHERE boss_name = '스우' AND difficulty = 'EXTREME';

-- 최대 인원을 초과해 저장된 기간 기록의 결정 메소를 최대 인원 기준으로 재계산한 뒤 인원 보정
UPDATE boss_item_acquisitions a
SET meso_amount = m.crystal_price / m.max_party_size
FROM boss_period_entries e, boss_master m, boss_drop_items d
WHERE a.boss_period_entry_id = e.id
  AND m.id = e.boss_id
  AND d.id = a.boss_drop_item_id
  AND e.party_size > m.max_party_size
  AND d.item_kind = 'CRYSTAL'
  AND m.crystal_price > 0;

UPDATE boss_period_entries e
SET party_size = m.max_party_size
FROM boss_master m
WHERE e.boss_id = m.id
  AND e.party_size > m.max_party_size;
