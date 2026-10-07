-- The source price table labels some CHAOS bosses as HARD; retain their in-game difficulty.
UPDATE boss_master
SET difficulty = 'HARD'
WHERE boss_name = '카링' AND difficulty = 'CHAOS';

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM scheduler_boss_records r
        JOIN boss_master b ON b.id = r.boss_id
        WHERE b.boss_name = '벨로나' AND b.difficulty = 'CHAOS'
    ) OR EXISTS (
        SELECT 1 FROM boss_item_acquisitions a
        JOIN boss_drop_items d ON d.id = a.boss_drop_item_id
        JOIN boss_master b ON b.id = d.boss_id
        WHERE b.boss_name = '벨로나' AND b.difficulty = 'CHAOS'
    ) THEN
        RAISE EXCEPTION 'Bellona CHAOS has records; manual migration required';
    END IF;
END $$;

DELETE FROM boss_master WHERE boss_name = '벨로나' AND difficulty = 'CHAOS';

INSERT INTO boss_master (boss_name, difficulty, reset_period, crystal_price, boss_image, sort_order)
VALUES ('벨로나', 'EASY', 'WEEKLY', 396000000, NULL, 179)
ON CONFLICT (boss_name, difficulty) DO NOTHING;

UPDATE boss_master AS boss
SET crystal_price = price.crystal_price
FROM (VALUES
    ('스우', 'NORMAL', 8350000::bigint),
    ('스우', 'HARD', 48900000::bigint),
    ('스우', 'EXTREME', 545000000::bigint),
    ('데미안', 'NORMAL', 8750000::bigint),
    ('데미안', 'HARD', 46400000::bigint),
    ('가엔슬', 'NORMAL', 12700000::bigint),
    ('가엔슬', 'CHAOS', 71300000::bigint),
    ('루시드', 'EASY', 14900000::bigint),
    ('루시드', 'NORMAL', 17800000::bigint),
    ('루시드', 'HARD', 59700000::bigint),
    ('윌', 'EASY', 16100000::bigint),
    ('윌', 'NORMAL', 20500000::bigint),
    ('윌', 'HARD', 73200000::bigint),
    ('더스크', 'NORMAL', 22000000::bigint),
    ('더스크', 'CHAOS', 66300000::bigint),
    ('진 힐라', 'NORMAL', 67600000::bigint),
    ('진 힐라', 'HARD', 100000000::bigint),
    ('듄켈', 'NORMAL', 23700000::bigint),
    ('듄켈', 'HARD', 89600000::bigint),
    ('검은 마법사', 'HARD', 465000000::bigint),
    ('검은 마법사', 'EXTREME', 5680000000::bigint),
    ('선택받은 세렌', 'NORMAL', 167000000::bigint),
    ('선택받은 세렌', 'HARD', 302000000::bigint),
    ('선택받은 세렌', 'EXTREME', 1840000000::bigint),
    ('감시자 칼로스', 'EASY', 238000000::bigint),
    ('감시자 칼로스', 'NORMAL', 479000000::bigint),
    ('감시자 칼로스', 'CHAOS', 1230000000::bigint),
    ('감시자 칼로스', 'EXTREME', 4104000000::bigint),
    ('카링', 'EASY', 320000000::bigint),
    ('카링', 'NORMAL', 593000000::bigint),
    ('카링', 'HARD', 1560000000::bigint),
    ('카링', 'EXTREME', 5387000000::bigint),
    ('림보', 'NORMAL', 995000000::bigint),
    ('림보', 'HARD', 2385000000::bigint),
    ('발드릭스', 'NORMAL', 1320000000::bigint),
    ('발드릭스', 'HARD', 3078000000::bigint),
    ('최초의 대적자', 'EASY', 261000000::bigint),
    ('최초의 대적자', 'NORMAL', 532000000::bigint),
    ('최초의 대적자', 'HARD', 1390000000::bigint),
    ('최초의 대적자', 'EXTREME', 4712000000::bigint),
    ('찬란한 흉성', 'NORMAL', 576000000::bigint),
    ('찬란한 흉성', 'HARD', 2678000000::bigint),
    ('유피테르', 'NORMAL', 1560000000::bigint),
    ('유피테르', 'HARD', 4845000000::bigint),
    ('벨로나', 'EASY', 396000000::bigint),
    ('벨로나', 'NORMAL', 824000000::bigint),
    ('벨로나', 'HARD', 2950000000::bigint)
) AS price(boss_name, difficulty, crystal_price)
WHERE boss.boss_name = price.boss_name AND boss.difficulty = price.difficulty;
