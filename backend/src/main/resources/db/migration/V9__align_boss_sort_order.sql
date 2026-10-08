-- boss_drop_items.md document order for active boss cards and manual selection candidates.
UPDATE boss_master
SET sort_order = CASE boss_name
    WHEN '자쿰' THEN 100
    WHEN '매그너스' THEN 110
    WHEN '반반' THEN 120
    WHEN '피에르' THEN 130
    WHEN '블러디 퀸' THEN 140
    WHEN '벨룸' THEN 150
    WHEN '파풀라투스' THEN 160
    WHEN '스우' THEN 170
    WHEN '데미안' THEN 180
    WHEN '가엔슬' THEN 190
    WHEN '루시드' THEN 200
    WHEN '윌' THEN 210
    WHEN '더스크' THEN 220
    WHEN '듄켈' THEN 230
    WHEN '진 힐라' THEN 240
    WHEN '선택받은 세렌' THEN 250
    WHEN '감시자 칼로스' THEN 260
    WHEN '최초의 대적자' THEN 270
    WHEN '카링' THEN 280
    WHEN '벨로나' THEN 290
    WHEN '림보' THEN 300
    WHEN '발드릭스' THEN 310
    WHEN '유피테르' THEN 320
    WHEN '검은 마법사' THEN 330
    ELSE sort_order
END;
