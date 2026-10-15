#!/usr/bin/env bash
# Run against a fresh LOCAL database migrated from the exact release migrations.
set -euo pipefail
umask 077
[[ $# == 2 ]] || { echo 'Usage: export-reset-catalog.sh SCRATCH_CONTAINER OUTPUT_JSON' >&2; exit 2; }
[[ ! -e $2 && ! -e $2.partial ]] || { echo 'Output already exists' >&2; exit 1; }
docker exec -i "$1" sh -eu -c '
 export PGPASSWORD="$POSTGRES_PASSWORD"
 exec psql -X -w -qAt -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"
' > "$2.partial" <<'SQL'
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
DO $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM public.flyway_schema_history) OR
    EXISTS(SELECT 1 FROM public.flyway_schema_history WHERE success IS DISTINCT FROM true OR version IS NULL OR type <> 'SQL' OR checksum IS NULL) OR
    EXISTS(SELECT version FROM public.flyway_schema_history GROUP BY version HAVING count(*) <> 1) THEN
  RAISE EXCEPTION 'Invalid or duplicate canonical migration history'; END IF;
END $$;
SELECT jsonb_build_object(
 'bosses', (SELECT jsonb_agg(to_jsonb(b) - 'id' - 'created_at' - 'updated_at' ORDER BY b.id) FROM public.boss_master b),
 'drops', (SELECT jsonb_agg((to_jsonb(d) - 'id' - 'boss_id' - 'created_at' - 'updated_at') || jsonb_build_object('boss_name', b.boss_name, 'difficulty', b.difficulty) ORDER BY d.id) FROM public.boss_drop_items d JOIN public.boss_master b ON b.id=d.boss_id WHERE d.is_active),
 'migrations', (SELECT jsonb_agg(jsonb_build_array(version, type, script, checksum, success) ORDER BY version COLLATE "C", type COLLATE "C", script COLLATE "C") FROM public.flyway_schema_history)
);
COMMIT;
SQL
python3 -c 'import json,sys; d=json.load(open(sys.argv[1])); assert all(d.get(k) for k in ("bosses","drops","migrations")), "Empty catalog/history"' "$2.partial"
mv "$2.partial" "$2"
printf 'Canonical catalog: %s\n' "$2"
