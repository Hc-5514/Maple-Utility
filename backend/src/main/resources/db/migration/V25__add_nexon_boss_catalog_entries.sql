-- 넥슨 스케줄러 응답에는 있으나 카탈로그에 없던 보스·난이도 추가
-- 시즌 보스 메이린은 결정 가격 미확인(0), 사용자가 드랍 모달에서 가격을 입력
INSERT INTO boss_master (boss_name, difficulty, reset_period, crystal_price, boss_image, sort_order, max_party_size)
VALUES
    ('림보', 'EXTREME', 'WEEKLY', 7500000000, '/assets/boss/limbo.png', 300, 3),
    ('발드릭스', 'EXTREME', 'WEEKLY', 9600000000, '/assets/boss/baldrix.png', 310, 3),
    ('시즌 보스 메이린', 'NORMAL', 'WEEKLY', 0, NULL, 330, 6),
    ('시즌 보스 메이린', 'HARD', 'WEEKLY', 0, NULL, 330, 6)
ON CONFLICT (boss_name, difficulty) DO NOTHING;

INSERT INTO boss_drop_items (
    boss_id, item_name, item_image, item_description, drop_rate_tier,
    item_kind, default_quantity, is_active
)
SELECT id, '강렬한 힘의 결정', NULL, '보스 처치 후 판매 가능한 결정석', 'HIGH',
       'CRYSTAL', 1, TRUE
FROM boss_master
WHERE (boss_name, difficulty) IN (
    ('림보', 'EXTREME'),
    ('발드릭스', 'EXTREME'),
    ('시즌 보스 메이린', 'NORMAL'),
    ('시즌 보스 메이린', 'HARD')
)
ON CONFLICT (boss_id, item_name) DO NOTHING;
