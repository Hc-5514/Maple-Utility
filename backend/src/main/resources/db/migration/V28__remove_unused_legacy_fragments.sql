-- V2 공통 임시 드랍 중 V7 비활성 처리된 미참조 항목만 정리.
-- 기존 획득 기록 참조 항목과 활성 항목 보존.
DELETE FROM boss_drop_items d
WHERE d.item_name='솔 에르다 조각' AND NOT d.is_active
  AND NOT EXISTS (SELECT 1 FROM boss_item_acquisitions a WHERE a.boss_drop_item_id=d.id);
