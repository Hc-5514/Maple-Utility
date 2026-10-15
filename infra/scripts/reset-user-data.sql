-- Invoked only by reset-user-data.sh inside its transaction.
-- No CASCADE and no legacy migration replay.
SET LOCAL search_path = public, pg_temp;
CREATE TEMP TABLE reset_expected_tables(name text PRIMARY KEY) ON COMMIT DROP;
INSERT INTO reset_expected_tables VALUES
('boss_drop_items'),
('boss_item_acquisitions'),
('boss_master'),
('boss_period_entries'),
('boss_period_selections'),
('characters'),
('data_context_accounts'),
('data_context_merge_audits'),
('data_contexts'),
('data_merge_provenance'),
('data_preference_sources'),
('data_sync_logs'),
('flyway_schema_history'),
('hunting_grounds'),
('hunting_records'),
('scheduler_boss_records'),
('scheduler_daily_records'),
('scheduler_monthly_boss_snapshots'),
('scheduler_monthly_finalizations'),
('scheduler_week_finalizations'),
('scheduler_weekly_records'),
('sync_jobs'),
('user_api_keys'),
('user_data_contexts'),
('user_hunting_ground_favorites'),
('user_selected_worlds'),
('users');
DO $$
DECLARE actual text[]; expected text[];
BEGIN
 SELECT array_agg(c.relname::text ORDER BY c.relname) INTO actual FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind IN ('r','p','f');
 SELECT array_agg(name ORDER BY name) INTO expected FROM reset_expected_tables;
 IF actual IS DISTINCT FROM expected THEN RAISE EXCEPTION 'Public table inventory mismatch; manual review required'; END IF;
 IF (SELECT jsonb_agg(jsonb_build_array(version,type,script,checksum,success) ORDER BY version COLLATE "C", type COLLATE "C", script COLLATE "C") FROM public.flyway_schema_history) IS DISTINCT FROM (SELECT payload->'migrations' FROM reset_catalog) THEN
  RAISE EXCEPTION 'Flyway history differs from canonical database';
 END IF;
 IF EXISTS (SELECT 1 FROM public.flyway_schema_history WHERE success IS DISTINCT FROM true OR version IS NULL OR type <> 'SQL' OR checksum IS NULL) OR
    EXISTS (SELECT version FROM public.flyway_schema_history GROUP BY version HAVING count(*) <> 1) THEN
  RAISE EXCEPTION 'Invalid or duplicate migration history'; END IF;
 IF EXISTS (SELECT 1 FROM reset_catalog c CROSS JOIN LATERAL jsonb_array_elements(c.payload->'migrations') m
            WHERE jsonb_array_length(m) <> 5 OR m->>0 IS NULL OR m->>1 IS DISTINCT FROM 'SQL' OR m->>2 IS NULL OR m->>3 IS NULL OR m->4 IS DISTINCT FROM 'true'::jsonb) OR
    EXISTS (SELECT m->>0 FROM reset_catalog c CROSS JOIN LATERAL jsonb_array_elements(c.payload->'migrations') m GROUP BY m->>0 HAVING count(*) <> 1) THEN
  RAISE EXCEPTION 'Invalid or duplicate canonical migration history'; END IF;
END $$;
LOCK TABLE public.hunting_grounds, public.flyway_schema_history IN ACCESS EXCLUSIVE MODE;
CREATE TEMP TABLE reset_preserved_grounds ON COMMIT DROP AS TABLE public.hunting_grounds;
CREATE TEMP TABLE reset_preserved_history ON COMMIT DROP AS TABLE public.flyway_schema_history;
CREATE TEMP TABLE reset_preserved_sequence(value jsonb) ON COMMIT DROP;
DO $$ DECLARE s text; v jsonb;
BEGIN
 s := pg_get_serial_sequence('public.hunting_grounds','id');
 IF s IS NULL THEN RAISE EXCEPTION 'Missing hunting ground sequence'; END IF;
 EXECUTE format('SELECT jsonb_build_array(last_value,is_called) FROM %s',s) INTO v;
 INSERT INTO reset_preserved_sequence VALUES(v);
END $$;
CREATE TEMP TABLE reset_bosses ON COMMIT DROP AS
SELECT b.* FROM reset_catalog c CROSS JOIN LATERAL jsonb_populate_recordset(NULL::public.boss_master,c.payload->'bosses') b;
CREATE TEMP TABLE reset_drops ON COMMIT DROP AS
SELECT d.*, j->>'boss_name' AS boss_name, j->>'difficulty' AS difficulty
FROM reset_catalog c CROSS JOIN LATERAL jsonb_array_elements(c.payload->'drops') j
CROSS JOIN LATERAL jsonb_populate_record(NULL::public.boss_drop_items,j) d;
DO $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM reset_bosses) OR NOT EXISTS(SELECT 1 FROM reset_drops) THEN RAISE EXCEPTION 'Empty catalog'; END IF;
 IF EXISTS(SELECT 1 FROM reset_drops WHERE is_active IS DISTINCT FROM true) THEN RAISE EXCEPTION 'Inactive drop in canonical catalog'; END IF;
 IF EXISTS(SELECT 1 FROM reset_drops d LEFT JOIN reset_bosses b USING(boss_name,difficulty) WHERE b.boss_name IS NULL) THEN RAISE EXCEPTION 'Unmatched canonical drop'; END IF;
END $$;
TRUNCATE TABLE
 public.boss_drop_items,
 public.boss_item_acquisitions,
 public.boss_master,
 public.boss_period_entries,
 public.boss_period_selections,
 public.characters,
 public.data_context_accounts,
 public.data_context_merge_audits,
 public.data_contexts,
 public.data_merge_provenance,
 public.data_preference_sources,
 public.data_sync_logs,
 public.hunting_records,
 public.scheduler_boss_records,
 public.scheduler_daily_records,
 public.scheduler_monthly_boss_snapshots,
 public.scheduler_monthly_finalizations,
 public.scheduler_week_finalizations,
 public.scheduler_weekly_records,
 public.sync_jobs,
 public.user_api_keys,
 public.user_data_contexts,
 public.user_hunting_ground_favorites,
 public.user_selected_worlds,
 public.users
 RESTART IDENTITY RESTRICT;
INSERT INTO public.boss_master
 (boss_name,difficulty,reset_period,crystal_price,boss_image,sort_order,is_active,max_party_size)
SELECT boss_name,difficulty,reset_period,crystal_price,boss_image,sort_order,is_active,max_party_size
FROM reset_bosses
ORDER BY sort_order, array_position(ARRAY['EASY','NORMAL','HARD','CHAOS','EXTREME'], difficulty::text), boss_name COLLATE "C";
INSERT INTO public.boss_drop_items
 (boss_id,item_name,item_image,item_description,drop_rate_tier,item_kind,default_quantity,is_active)
SELECT b.id,d.item_name,d.item_image,d.item_description,d.drop_rate_tier,d.item_kind,d.default_quantity,d.is_active
FROM reset_drops d JOIN public.boss_master b USING(boss_name,difficulty)
ORDER BY b.id,d.item_name COLLATE "C";
DO $$
DECLARE t text; n bigint; s text; v jsonb;
BEGIN
 IF EXISTS((TABLE reset_preserved_grounds EXCEPT ALL TABLE public.hunting_grounds) UNION ALL (TABLE public.hunting_grounds EXCEPT ALL TABLE reset_preserved_grounds)) OR
    EXISTS((TABLE reset_preserved_history EXCEPT ALL TABLE public.flyway_schema_history) UNION ALL (TABLE public.flyway_schema_history EXCEPT ALL TABLE reset_preserved_history)) THEN
  RAISE EXCEPTION 'Preserved table changed'; END IF;
 s := pg_get_serial_sequence('public.hunting_grounds','id');
 EXECUTE format('SELECT jsonb_build_array(last_value,is_called) FROM %s',s) INTO v;
 IF v IS DISTINCT FROM (SELECT value FROM reset_preserved_sequence) THEN RAISE EXCEPTION 'Preserved sequence changed'; END IF;
 FOR t IN SELECT name FROM reset_expected_tables WHERE name NOT IN ('hunting_grounds','flyway_schema_history','boss_master','boss_drop_items') LOOP
  EXECUTE format('SELECT count(*) FROM public.%I',t) INTO n;
  IF n <> 0 THEN RAISE EXCEPTION 'Residual rows in %',t; END IF;
  s := NULL;
  IF EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name=t AND column_name='id') THEN
   s := pg_get_serial_sequence(format('public.%I',t),'id');
  END IF;
  IF s IS NOT NULL THEN
   EXECUTE format('SELECT jsonb_build_array(last_value,is_called) FROM %s',s) INTO v;
   IF v IS DISTINCT FROM '[1,false]'::jsonb THEN RAISE EXCEPTION 'Sequence not reset: %',s; END IF;
  END IF;
 END LOOP;
 IF (SELECT count(*) FROM public.boss_master) <> (SELECT count(*) FROM reset_bosses) OR
    (SELECT count(*) FROM public.boss_drop_items) <> (SELECT count(*) FROM reset_drops) THEN RAISE EXCEPTION 'Catalog row count mismatch'; END IF;
 IF EXISTS(SELECT 1 FROM (SELECT id,row_number() OVER(ORDER BY sort_order,array_position(ARRAY['EASY','NORMAL','HARD','CHAOS','EXTREME'],difficulty::text),boss_name COLLATE "C") wanted FROM public.boss_master) x WHERE id<>wanted) THEN RAISE EXCEPTION 'Boss ID order mismatch'; END IF;
 IF EXISTS(SELECT 1 FROM (SELECT id,row_number() OVER(ORDER BY boss_id,item_name COLLATE "C") wanted FROM public.boss_drop_items) x WHERE id<>wanted) THEN RAISE EXCEPTION 'Drop ID order mismatch'; END IF;
 FOR t IN SELECT unnest(ARRAY['boss_master','boss_drop_items']) LOOP
  s := pg_get_serial_sequence(format('public.%I',t),'id');
  EXECUTE format('SELECT count(*) FROM public.%I',t) INTO n;
  EXECUTE format('SELECT jsonb_build_array(last_value,is_called) FROM %s',s) INTO v;
  IF v IS DISTINCT FROM jsonb_build_array(n,true) THEN RAISE EXCEPTION 'Catalog sequence mismatch: %',s; END IF;
 END LOOP;
 IF EXISTS(SELECT 1 FROM public.boss_drop_items d LEFT JOIN public.boss_master b ON b.id=d.boss_id WHERE b.id IS NULL OR NOT d.is_active) THEN RAISE EXCEPTION 'Drop integrity failure'; END IF;
END $$;
SELECT 'reset_verified' AS status,
 (SELECT count(*) FROM public.boss_master) AS bosses,
 (SELECT count(*) FROM public.boss_drop_items) AS active_drops,
 (SELECT count(*) FROM public.hunting_grounds) AS preserved_grounds;
