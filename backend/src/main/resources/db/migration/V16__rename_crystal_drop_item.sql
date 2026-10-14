-- 결정 아이템 이름을 '강력한 힘의 결정'으로 통일
UPDATE boss_drop_items
SET item_name = '강력한 힘의 결정'
WHERE item_name = '강렬한 힘의 결정'
  AND item_kind = 'CRYSTAL';
