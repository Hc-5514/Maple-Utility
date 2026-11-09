-- 운영 수동 보정 가격의 신규·기존 DB 정합화. 기존 개별 기록 보존.
CREATE TEMP TABLE crystal_price_patch (boss_name text, difficulty text, price bigint) ON COMMIT DROP;
INSERT INTO crystal_price_patch VALUES
('자쿰','CHAOS',4040000),('매그너스','HARD',4280000),
('반반','CHAOS',4070000),('피에르','CHAOS',4080000),
('블러디 퀸','CHAOS',4070000),('벨룸','CHAOS',4640000),('파풀라투스','CHAOS',6550000);
DO $$ BEGIN
    IF (SELECT count(*) FROM crystal_price_patch p JOIN boss_master b
        ON b.boss_name=p.boss_name AND b.difficulty=p.difficulty
        WHERE b.crystal_price IN (0,p.price) AND b.reset_period='WEEKLY' AND b.is_active) <> 7 THEN
        RAISE EXCEPTION '결정 가격 대상 누락 또는 예상 외 가격·상태';
    END IF;
END $$;
UPDATE boss_master b SET crystal_price=p.price FROM crystal_price_patch p
WHERE b.boss_name=p.boss_name AND b.difficulty=p.difficulty AND b.crystal_price=0;
