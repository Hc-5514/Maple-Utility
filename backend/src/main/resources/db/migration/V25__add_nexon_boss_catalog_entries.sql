-- 넥슨 스케줄러 응답에는 있으나 카탈로그에 없던 보스·난이도 추가
-- 시즌 보스 메이린은 결정 가격 0, 최대 파티 1인, 드랍 아이템 없음
INSERT INTO boss_master (boss_name, difficulty, reset_period, crystal_price, boss_image, sort_order, max_party_size)
VALUES
    ('림보', 'EXTREME', 'WEEKLY', 7500000000, '/assets/boss/limbo.png', 300, 3),
    ('발드릭스', 'EXTREME', 'WEEKLY', 9600000000, '/assets/boss/baldrix.png', 310, 3),
    ('시즌 보스 메이린', 'NORMAL', 'WEEKLY', 0, NULL, 330, 1),
    ('시즌 보스 메이린', 'HARD', 'WEEKLY', 0, NULL, 330, 1)
ON CONFLICT (boss_name, difficulty) DO NOTHING;

-- 림보·발드릭스 익스트림 드랍 아이템: 하드와 동일(비활성 항목 제외)하되
-- 익스트림에서는 림보의 왜곡된 욕망의 결정, 발드릭스의 영원한 충성의 흔적이 드랍되지 않음
INSERT INTO boss_drop_items (
    boss_id, item_name, item_image, item_description, drop_rate_tier,
    item_kind, default_quantity, is_active
)
SELECT extreme.id, hard_item.item_name, hard_item.item_image, hard_item.item_description,
       hard_item.drop_rate_tier, hard_item.item_kind, hard_item.default_quantity, TRUE
FROM boss_master extreme
JOIN boss_master hard ON hard.boss_name = extreme.boss_name AND hard.difficulty = 'HARD'
JOIN boss_drop_items hard_item ON hard_item.boss_id = hard.id AND hard_item.is_active
WHERE extreme.difficulty = 'EXTREME' AND extreme.boss_name IN ('림보', '발드릭스')
  AND NOT (extreme.boss_name = '림보' AND hard_item.item_name = '왜곡된 욕망의 결정')
  AND NOT (extreme.boss_name = '발드릭스' AND hard_item.item_name = '영원한 충성의 흔적')
ORDER BY hard_item.id
ON CONFLICT (boss_id, item_name) DO NOTHING;

-- 익스트림에서 추가로 드랍되는 아이템
INSERT INTO boss_drop_items (
    boss_id, item_name, item_image, item_description, drop_rate_tier,
    item_kind, default_quantity, is_active
)
SELECT boss.id, seed.item_name, NULL, NULL, NULL, seed.item_kind, seed.default_quantity, TRUE
FROM (VALUES
    ('림보', 'EXTREME', '익셉셔널 해머(포켓)', 'RANDOM', 1),
    ('림보', 'EXTREME', '영롱한 달빛 포션', 'RANDOM', 1),
    ('발드릭스', 'EXTREME', '익셉셔널 해머(펜던트)', 'RANDOM', 1),
    ('발드릭스', 'EXTREME', '영롱한 달빛 포션', 'RANDOM', 1)
) AS seed(boss_name, difficulty, item_name, item_kind, default_quantity)
JOIN boss_master boss ON boss.boss_name = seed.boss_name AND boss.difficulty = seed.difficulty
ON CONFLICT (boss_id, item_name) DO NOTHING;
