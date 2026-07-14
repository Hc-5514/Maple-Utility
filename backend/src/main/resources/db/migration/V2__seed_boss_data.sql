INSERT INTO boss_master (boss_name, difficulty, reset_period, crystal_price, boss_image, sort_order)
VALUES
    ('스우', 'NORMAL', 'WEEKLY', 16700000, '/assets/boss/suu.png', 10),
    ('스우', 'HARD', 'WEEKLY', 51500000, '/assets/boss/suu.png', 11),
    ('스우', 'EXTREME', 'WEEKLY', 574000000, '/assets/boss/suu.png', 12),
    ('윌', 'NORMAL', 'WEEKLY', 41100000, '/assets/boss/will.png', 20),
    ('윌', 'HARD', 'WEEKLY', 77100000, '/assets/boss/will.png', 21),
    ('더스크', 'NORMAL', 'WEEKLY', 44000000, '/assets/boss/gloom.png', 30),
    ('더스크', 'CHAOS', 'WEEKLY', 69800000, '/assets/boss/gloom.png', 31),
    ('진 힐라', 'NORMAL', 'WEEKLY', 71200000, '/assets/boss/verus-hilla.png', 40),
    ('진 힐라', 'HARD', 'WEEKLY', 106000000, '/assets/boss/verus-hilla.png', 41),
    ('루엘', 'NORMAL', 'WEEKLY', 0, '/assets/boss/luel.png', 50),
    ('루엘', 'HARD', 'WEEKLY', 0, '/assets/boss/luel.png', 51),
    ('선택받은 세렌', 'NORMAL', 'WEEKLY', 239000000, '/assets/boss/seren.png', 60),
    ('선택받은 세렌', 'HARD', 'WEEKLY', 356000000, '/assets/boss/seren.png', 61),
    ('선택받은 세렌', 'EXTREME', 'WEEKLY', 2835000000, '/assets/boss/seren.png', 62),
    ('감시자 칼로스', 'NORMAL', 'WEEKLY', 505000000, '/assets/boss/kalos.png', 70),
    ('감시자 칼로스', 'CHAOS', 'WEEKLY', 1273000000, '/assets/boss/kalos.png', 71),
    ('카링', 'NORMAL', 'WEEKLY', 678000000, '/assets/boss/kaling.png', 80),
    ('림보', 'NORMAL', 'WEEKLY', 1026000000, '/assets/boss/limbo.png', 90),
    ('발드릭스', 'NORMAL', 'WEEKLY', 1368000000, '/assets/boss/baldrix.png', 100),
    ('최초의 대적자', 'NORMAL', 'WEEKLY', 560000000, '/assets/boss/adversary.png', 110),
    ('최초의 대적자', 'HARD', 'WEEKLY', 1435000000, '/assets/boss/adversary.png', 111),
    ('찬란한 흉성', 'NORMAL', 'WEEKLY', 625000000, '/assets/boss/shining-star.png', 120),
    ('검은 마법사', 'HARD', 'MONTHLY', 665000000, '/assets/boss/black-mage.png', 130),
    ('검은 마법사', 'EXTREME', 'MONTHLY', 8740000000, '/assets/boss/black-mage.png', 131);

INSERT INTO boss_drop_items (boss_id, item_name, item_image, item_description, drop_rate_tier)
SELECT id, item_name, item_image, item_description, drop_rate_tier
FROM boss_master
CROSS JOIN (
    VALUES
        ('솔 에르다 조각', '/assets/items/sol-erda-fragment.png', '보스 보상 공통 드랍 아이템', 'NORMAL'),
        ('강렬한 힘의 결정', '/assets/items/intense-power-crystal.png', '보스 처치 후 판매 가능한 결정석', 'HIGH')
) AS item_seed(item_name, item_image, item_description, drop_rate_tier);
