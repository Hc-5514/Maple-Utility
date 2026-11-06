-- 물욕템 카탈로그 분류. 시드링 상자는 조회 시 상세 결과(리레4·컨티4) 추가 검증.
ALTER TABLE boss_drop_items DROP CONSTRAINT chk_boss_drop_items_kind;
ALTER TABLE boss_drop_items ADD CONSTRAINT chk_boss_drop_items_kind
    CHECK (item_kind IN ('CRYSTAL', 'FIXED', 'RANDOM', 'LUCKY'));

UPDATE boss_drop_items SET item_kind = 'LUCKY'
WHERE item_kind = 'RANDOM' AND item_name IN (
    '루즈 컨트롤 머신 마크', '마력이 깃든 안대', '몽환의 벨트', '저주받은 마도서 선택 상자',
    '거대한 공포', '커맨더 포스 이어링', '고통의 근원', '창세의 뱃지',
    '미트라의 분노 선택 상자', '컴플리트 언더컨트롤', '혼돈의 칠흑 장신구 상자',
    '불멸의 유산', '굶주리는 핏빛 원혼', '황홀한 악몽', '근원의 속삭임', '죽음의 맹세', '오만의 원죄',
    '의지의 에테르넬 방어구 상자', '고대의 에테르넬 방어구 상자', '흉수의 에테르넬 방어구 상자',
    '광기의 에테르넬 방어구 상자', '환상의 에테르넬 방어구 상자', '욕망의 에테르넬 방어구 상자',
    '맹세의 에테르넬 방어구 상자', '갈망의 에테르넬 방어구 상자',
    '생명의 연마석', '신념의 연마석', '영롱한 달빛 포션',
    '익셉셔널 해머(얼굴장식)', '익셉셔널 해머(눈장식)', '익셉셔널 해머(귀고리)',
    '익셉셔널 해머(벨트)', '익셉셔널 해머(훈장)', '익셉셔널 해머(펜던트)', '익셉셔널 해머(포켓)',
    '1단계 소울 에테르', '2단계 소울 에테르', '3단계 소울 에테르', '4단계 소울 에테르',
    '녹옥의 보스 반지 상자', '홍옥의 보스 반지 상자', '흑옥의 보스 반지 상자',
    '백옥의 보스 반지 상자', '생명의 보스 반지 상자'
);

ALTER TABLE boss_item_acquisitions
    ADD COLUMN period_start DATE,
    ADD COLUMN period_end DATE;

-- 원본 acquired_date·ID·상세 결과·수량·금액 보존. 연결된 기간 기록을 최우선 사용.
WITH periods AS (
    SELECT a.id, COALESCE(p.reset_period, b.reset_period) AS reset_period,
           COALESCE(p.period_start,
               CASE b.reset_period
                   WHEN 'MONTHLY' THEN date_trunc('month', a.acquired_date)::date
                   WHEN 'WEEKLY' THEN a.acquired_date - ((EXTRACT(DOW FROM a.acquired_date)::integer + 3) % 7)
                   ELSE a.acquired_date
               END) AS period_start
    FROM boss_item_acquisitions a
    JOIN boss_drop_items d ON d.id = a.boss_drop_item_id
    JOIN boss_master b ON b.id = d.boss_id
    LEFT JOIN boss_period_entries p ON p.id = a.boss_period_entry_id
)
UPDATE boss_item_acquisitions a
SET period_start = p.period_start,
    period_end = CASE p.reset_period
        WHEN 'MONTHLY' THEN (p.period_start + INTERVAL '1 month - 1 day')::date
        WHEN 'WEEKLY' THEN p.period_start + 6
        ELSE p.period_start
    END
FROM periods p WHERE p.id = a.id;

ALTER TABLE boss_item_acquisitions
    ALTER COLUMN period_start SET NOT NULL,
    ALTER COLUMN period_end SET NOT NULL,
    ADD CONSTRAINT chk_boss_item_acquisitions_period CHECK (period_start <= period_end);

-- 캐릭터별 기간 겹침 필터 및 기간 시작일·ID 내림차순 페이지 조회 지원.
CREATE INDEX idx_boss_item_acquisitions_character_period
    ON boss_item_acquisitions (character_id, period_start DESC, id DESC) INCLUDE (period_end);
