WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY character_id,
                            week_start_date - ((EXTRACT(DOW FROM week_start_date)::int - 4 + 7) % 7),
                            content_name
               ORDER BY synced_at DESC NULLS LAST, updated_at DESC, id DESC
           ) AS row_number
    FROM scheduler_weekly_records
)
DELETE FROM scheduler_weekly_records record
USING ranked
WHERE record.id = ranked.id
  AND ranked.row_number > 1;

UPDATE scheduler_weekly_records
SET week_start_date = week_start_date - ((EXTRACT(DOW FROM week_start_date)::int - 4 + 7) % 7)
WHERE EXTRACT(DOW FROM week_start_date)::int <> 4;
