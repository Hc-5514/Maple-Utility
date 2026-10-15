\set ON_ERROR_STOP on
\pset pager off
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL TIME ZONE 'Asia/Seoul';
SET LOCAL statement_timeout = '30s';
SET LOCAL lock_timeout = '3s';

SELECT current_database() AS database_name, current_schema() AS schema_name,
       current_setting('transaction_read_only') AS read_only,
       CURRENT_TIMESTAMP AS observed_at;
SELECT EXISTS (SELECT 1 FROM public.data_contexts
               WHERE id = :'context_id'::bigint AND owner_user_id = :'owner_id'::bigint
                 AND canonical_context_id IS NULL) AS target_valid \gset
\if :target_valid
\else
  \echo 'ERROR: target context/owner mismatch or merged context; diagnosis stopped'
  -- ON_ERROR_STOP makes this a nonzero exit on PostgreSQL 16+ psql as well.
  SELECT 1 / 0 AS invalid_target;
\endif

SELECT installed_rank, version, description, success FROM public.flyway_schema_history
ORDER BY installed_rank DESC LIMIT 5;
SELECT id, owner_user_id, kakao_user_id, canonical_context_id
FROM public.data_contexts WHERE id = :'context_id'::bigint;
SELECT user_id, context_id FROM public.user_data_contexts
WHERE context_id = :'context_id'::bigint ORDER BY user_id;
SELECT count(*) AS verified_account_count FROM public.data_context_accounts
WHERE context_id = :'context_id'::bigint;
SELECT id AS key_row_id, user_id, context_id, key_status,
       key_fingerprint IS NOT NULL AS fingerprint_present, last_verified_at
FROM public.user_api_keys WHERE context_id = :'context_id'::bigint ORDER BY id;
SELECT count(*) AS key_rows, count(DISTINCT key_fingerprint) AS known_distinct_keys,
       count(*) FILTER (WHERE key_fingerprint IS NULL) AS unverified_fingerprint_rows
FROM public.user_api_keys WHERE context_id = :'context_id'::bigint;

SELECT count(*) AS characters, count(*) FILTER (WHERE is_favorite) AS favorites
FROM public.characters WHERE user_id = :'owner_id'::bigint;
-- Record ownership is evidence of activity, not proof of a former favorite.
SELECT c.id AS character_id, c.is_favorite, c.sort_order,
       (SELECT count(*) FROM public.hunting_records h WHERE h.character_id = c.id) AS hunting_rows,
       (SELECT count(*) FROM public.scheduler_boss_records b WHERE b.character_id = c.id) AS boss_rows
FROM public.characters c WHERE c.user_id = :'owner_id'::bigint ORDER BY c.id;

-- Restrict all counts to the selected owner, including indirect snapshot rows.
-- Catalog identifiers are quoted; only row counts are selected, never row payloads.
SELECT format('SELECT %L AS relation, count(*) AS owner_rows FROM public.%I t WHERE %s;',
              t.table_name, t.table_name,
              CASE WHEN EXISTS (SELECT 1 FROM information_schema.columns c
                                WHERE c.table_schema = 'public' AND c.table_name = t.table_name
                                  AND c.column_name = 'user_id')
                   THEN format('t.user_id = %L::bigint', :'owner_id')
                   ELSE format('t.character_id IN (SELECT id FROM public.characters WHERE user_id = %L::bigint)', :'owner_id') END)
FROM information_schema.tables t
WHERE t.table_schema = 'public' AND t.table_type = 'BASE TABLE'
  AND EXISTS (SELECT 1 FROM information_schema.columns c
              WHERE c.table_schema = t.table_schema AND c.table_name = t.table_name
                AND c.column_name IN ('user_id', 'character_id'))
ORDER BY t.table_name
\gexec
SELECT count(*) AS monthly_snapshot_rows
FROM public.scheduler_monthly_boss_snapshots s
JOIN public.scheduler_monthly_finalizations f ON f.id = s.finalization_id
WHERE f.user_id = :'owner_id'::bigint;

SELECT p.table_name, p.source_user_id, count(*) AS character_markers
FROM public.data_merge_provenance p
JOIN public.characters c ON p.table_name = 'characters' AND p.record_id = c.id
WHERE c.user_id = :'owner_id'::bigint
GROUP BY p.table_name, p.source_user_id ORDER BY p.source_user_id;

-- Tables introduced by the fix are optional during pre-migration diagnosis.
SELECT to_regclass('public.data_preference_sources') IS NOT NULL AS has_preferences,
       to_regclass('public.data_context_merge_audits') IS NOT NULL AS has_audits \gset
\if :has_preferences
SELECT setting_type, source_user_id, state, count(*) AS markers, max(saved_at) AS last_saved_at
FROM public.data_preference_sources
WHERE (setting_type = 'FAVORITE' AND record_id IN
       (SELECT id FROM public.characters WHERE user_id = :'owner_id'::bigint))
   OR (setting_type IN ('SORT_ORDER', 'WORLD_SELECTION') AND record_id = :'owner_id'::bigint)
GROUP BY setting_type, source_user_id, state ORDER BY setting_type, source_user_id, state;
\else
\echo 'data_preference_sources absent: pre-migration diagnosis'
\endif
\if :has_audits
SELECT id, target_user_id, source_user_id, created_at,
       octet_length(before_state::text) AS before_state_bytes,
       octet_length(after_state::text) AS after_state_bytes
FROM public.data_context_merge_audits
WHERE target_user_id = :'owner_id'::bigint OR source_user_id = :'owner_id'::bigint
ORDER BY id;
\else
\echo 'data_context_merge_audits absent: pre-migration diagnosis'
\endif
ROLLBACK;
