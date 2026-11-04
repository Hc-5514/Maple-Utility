-- 동일 캐릭터의 같은 날짜 사냥 기록 복수 입력 허용
ALTER TABLE hunting_records
    DROP CONSTRAINT IF EXISTS uk_hunting_records_character_id_record_date;
