INSERT INTO boss_master (boss_name, difficulty, reset_period, crystal_price, boss_image, sort_order)
VALUES
    ('스우', 'NORMAL', 'WEEKLY', 16700000, '/assets/boss/suu.png', 10),
    ('스우', 'HARD', 'WEEKLY', 51500000, '/assets/boss/suu.png', 11),
    ('스우', 'EXTREME', 'WEEKLY', 574000000, '/assets/boss/suu.png', 12),
    ('데미안', 'NORMAL', 'WEEKLY', 17500000, '/assets/boss/damien.png', 20),
    ('데미안', 'HARD', 'WEEKLY', 48900000, '/assets/boss/damien.png', 21),
    ('가엔슬', 'NORMAL', 'WEEKLY', 25500000, '/assets/boss/guardian-angel-slime.png', 30),
    ('가엔슬', 'CHAOS', 'WEEKLY', 75100000, '/assets/boss/guardian-angel-slime.png', 31),
    ('루시드', 'EASY', 'WEEKLY', 29800000, '/assets/boss/lucid.png', 40),
    ('루시드', 'NORMAL', 'WEEKLY', 35600000, '/assets/boss/lucid.png', 41),
    ('루시드', 'HARD', 'WEEKLY', 62900000, '/assets/boss/lucid.png', 42),
    ('윌', 'EASY', 'WEEKLY', 32300000, '/assets/boss/will.png', 50),
    ('윌', 'NORMAL', 'WEEKLY', 41100000, '/assets/boss/will.png', 51),
    ('윌', 'HARD', 'WEEKLY', 77100000, '/assets/boss/will.png', 52),
    ('더스크', 'NORMAL', 'WEEKLY', 44000000, '/assets/boss/gloom.png', 60),
    ('더스크', 'CHAOS', 'WEEKLY', 69800000, '/assets/boss/gloom.png', 61),
    ('진 힐라', 'NORMAL', 'WEEKLY', 71200000, '/assets/boss/verus-hilla.png', 70),
    ('진 힐라', 'HARD', 'WEEKLY', 106000000, '/assets/boss/verus-hilla.png', 71),
    ('듄켈', 'NORMAL', 'WEEKLY', 47500000, '/assets/boss/dunkel.png', 80),
    ('듄켈', 'HARD', 'WEEKLY', 94400000, '/assets/boss/dunkel.png', 81),
    ('검은 마법사', 'HARD', 'MONTHLY', 665000000, '/assets/boss/black-mage.png', 90),
    ('검은 마법사', 'EXTREME', 'MONTHLY', 8740000000, '/assets/boss/black-mage.png', 91),
    ('선택받은 세렌', 'NORMAL', 'WEEKLY', 239000000, '/assets/boss/seren.png', 100),
    ('선택받은 세렌', 'HARD', 'WEEKLY', 356000000, '/assets/boss/seren.png', 101),
    ('선택받은 세렌', 'EXTREME', 'WEEKLY', 2835000000, '/assets/boss/seren.png', 102),
    ('감시자 칼로스', 'EASY', 'WEEKLY', 280000000, '/assets/boss/kalos.png', 110),
    ('감시자 칼로스', 'NORMAL', 'WEEKLY', 505000000, '/assets/boss/kalos.png', 111),
    ('감시자 칼로스', 'CHAOS', 'WEEKLY', 1273000000, '/assets/boss/kalos.png', 112),
    ('감시자 칼로스', 'EXTREME', 'WEEKLY', 4104000000, '/assets/boss/kalos.png', 113),
    ('카링', 'EASY', 'WEEKLY', 377000000, '/assets/boss/kaling.png', 120),
    ('카링', 'NORMAL', 'WEEKLY', 678000000, '/assets/boss/kaling.png', 121),
    ('카링', 'CHAOS', 'WEEKLY', 1739000000, '/assets/boss/kaling.png', 122),
    ('카링', 'EXTREME', 'WEEKLY', 5387000000, '/assets/boss/kaling.png', 123),
    ('림보', 'NORMAL', 'WEEKLY', 1026000000, '/assets/boss/limbo.png', 130),
    ('림보', 'HARD', 'WEEKLY', 2385000000, '/assets/boss/limbo.png', 131),
    ('발드릭스', 'NORMAL', 'WEEKLY', 1368000000, '/assets/boss/baldrix.png', 140),
    ('발드릭스', 'HARD', 'WEEKLY', 3078000000, '/assets/boss/baldrix.png', 141),
    ('최초의 대적자', 'EASY', 'WEEKLY', 308000000, '/assets/boss/adversary.png', 150),
    ('최초의 대적자', 'NORMAL', 'WEEKLY', 560000000, '/assets/boss/adversary.png', 151),
    ('최초의 대적자', 'HARD', 'WEEKLY', 1435000000, '/assets/boss/adversary.png', 152),
    ('최초의 대적자', 'EXTREME', 'WEEKLY', 4712000000, '/assets/boss/adversary.png', 153),
    ('찬란한 흉성', 'NORMAL', 'WEEKLY', 625000000, '/assets/boss/shining-star.png', 160),
    ('찬란한 흉성', 'HARD', 'WEEKLY', 2678000000, '/assets/boss/shining-star.png', 161),
    ('유피테르', 'NORMAL', 'WEEKLY', 1615000000, '/assets/boss/jupiter.png', 170),
    ('유피테르', 'HARD', 'WEEKLY', 4845000000, '/assets/boss/jupiter.png', 171),
    ('벨로나', 'NORMAL', 'WEEKLY', 0, '/assets/boss/bellona.png', 180),
    ('벨로나', 'HARD', 'WEEKLY', 0, '/assets/boss/bellona.png', 181),
    ('벨로나', 'CHAOS', 'WEEKLY', 0, '/assets/boss/bellona.png', 182);

INSERT INTO boss_drop_items (boss_id, item_name, item_image, item_description, drop_rate_tier)
SELECT id, item_name, item_image, item_description, drop_rate_tier
FROM boss_master
CROSS JOIN (
    VALUES
        ('솔 에르다 조각', '/assets/items/sol-erda-fragment.png', '보스 보상 공통 드랍 아이템', 'NORMAL'),
        ('강렬한 힘의 결정', '/assets/items/intense-power-crystal.png', '보스 처치 후 판매 가능한 결정석', 'HIGH')
) AS item_seed(item_name, item_image, item_description, drop_rate_tier);
