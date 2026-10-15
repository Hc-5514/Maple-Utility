#!/usr/bin/env bash
# Isolated integration regression: actual release SQL + synthetic history fixture.
# Synthetic checksums below are NOT Flyway validation or production catalog data.
set -euo pipefail
umask 077
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
repo_dir=$(cd "$script_dir/../.." && pwd)
temp_dir=$(mktemp -d "${TMPDIR:-/tmp}/maple-reset-test.XXXXXX")
container="maple-reset-test-$(date +%s)-$$"
passed=0
cleanup() {
  docker rm -vf "$container" >/dev/null 2>&1 || true
  rm -rf -- "$temp_dir"
}
trap cleanup EXIT
ok() { passed=$((passed+1)); printf 'PASS %s\n' "$1"; }
sql() {
  docker exec -i "$container" psql -X -w -qAt -v ON_ERROR_STOP=1 -U fixture -d "${test_db:-fixture}"
}
expect_failure() {
  local name=$1 expected=$2
  shift 2
  if "$@" >"$temp_dir/failure.log" 2>&1; then
    printf 'FAIL expected rejection: %s\n' "$name" >&2; exit 1
  fi
  if ! rg -q -- "$expected" "$temp_dir/failure.log"; then
    cat "$temp_dir/failure.log" >&2; exit 1
  fi
}
snapshot() {
  # All public rows and sequence states, independent of physical row order.
  sql <<'SQL'
SELECT format('SELECT %L || COALESCE(string_agg(v, chr(10) ORDER BY v), '''') FROM (SELECT row_to_json(t)::text AS v FROM public.%I t) s;', 'table:' || tablename || '=', tablename)
FROM pg_tables WHERE schemaname='public' ORDER BY tablename \gexec
SELECT format('SELECT %L || last_value || '':'' || is_called FROM public.%I;', 'seq:' || sequencename || '=', sequencename)
FROM pg_sequences WHERE schemaname='public' ORDER BY sequencename \gexec
SQL
}
reset() {
  RESET_CONFIRM=RESET-ALL-USER-DATA RESET_BACKUP_VERIFIED=yes RESET_WRITERS_STOPPED=yes \
    bash "$script_dir/reset-user-data.sh" "$container" "$1"
}

docker run -d --name "$container" --network none \
  -e POSTGRES_USER=fixture -e POSTGRES_DB=fixture -e POSTGRES_PASSWORD=fixture-test-only \
  -e POSTGRES_HOST_AUTH_METHOD=trust postgres:16.9-alpine >/dev/null
for attempt in {1..30}; do
  if docker exec "$container" pg_isready -U fixture -d fixture >/dev/null 2>&1; then break; fi
  sleep 2
done
docker exec "$container" pg_isready -U fixture -d fixture >/dev/null

# Transaction wrappers match Flyway's default migration transaction semantics.
python3 - "$repo_dir" > "$temp_dir/migrations.sql" <<'PY'
from pathlib import Path
import sys
paths = sorted((Path(sys.argv[1]) / 'backend/src/main/resources/db/migration').glob('V*__*.sql'),
               key=lambda p: int(p.name.split('__')[0][1:]))
print('CREATE TABLE flyway_schema_history (installed_rank integer PRIMARY KEY, version varchar(50), type varchar(20) NOT NULL, script varchar(1000) NOT NULL, checksum integer, success boolean NOT NULL);')
for rank, p in enumerate(paths, 1):
    version = p.name.split('__')[0][1:]
    print('BEGIN;')
    print(p.read_text())
    print(f"INSERT INTO flyway_schema_history VALUES ({rank}, '{version}', 'SQL', '{p.name}', {rank}, true);")
    print('COMMIT;')
PY
sql < "$temp_dir/migrations.sql" > "$temp_dir/migrations.log"
bash "$script_dir/export-reset-catalog.sh" "$container" "$temp_dir/catalog.json" >/dev/null
python3 - "$temp_dir/catalog.json" <<'PY'
import json, sys
c = json.load(open(sys.argv[1]))
assert len(c['bosses']) == 54, len(c['bosses'])
assert len(c['drops']) == 282, len(c['drops'])
assert len(c['migrations']) == 19, len(c['migrations'])
assert all(d['is_active'] for d in c['drops'])
assert all('id' not in b for b in c['bosses'])
assert all('boss_id' not in d for d in c['drops'])
PY
ok 'canonical export: final release catalog and active drops only'
expect_failure 'existing export file' 'Output already exists' \
  bash "$script_dir/export-reset-catalog.sh" "$container" "$temp_dir/catalog.json"
ok 'canonical export refuses to overwrite existing file'

# Each reset target receives nonempty synthetic data, including account links,
# audit/provenance, preferences and monthly/weekly scheduler history.
sql > "$temp_dir/fixture.log" <<'SQL'
INSERT INTO users(oauth_provider,oauth_id,nickname) VALUES ('KAKAO','fixture-only','fixture');
INSERT INTO user_api_keys(user_id,encrypted_key) VALUES (1,'NOT-A-REAL-KEY');
INSERT INTO data_context_accounts(account_id,context_id) VALUES ('fixture-account',1);
INSERT INTO characters(user_id,ocid,character_name,is_favorite) VALUES (1,'fixture-ocid','fixture-character',true);
INSERT INTO user_hunting_ground_favorites SELECT 1,min(id),CURRENT_TIMESTAMP FROM hunting_grounds;
INSERT INTO user_selected_worlds(user_id,world_name) VALUES (1,'fixture-world');
INSERT INTO hunting_records(character_id,record_date,meso_earned,hunting_ground_id) SELECT 1,'2026-10-01',1,min(id) FROM hunting_grounds;
INSERT INTO scheduler_daily_records(character_id,record_date,content_name) VALUES (1,'2026-10-01','fixture');
INSERT INTO scheduler_weekly_records(character_id,week_start_date,content_name) VALUES (1,'2026-10-01','fixture');
INSERT INTO scheduler_boss_records(character_id,boss_id,record_date,reset_period) SELECT 1,min(id),'2026-10-01','WEEKLY' FROM boss_master;
INSERT INTO boss_period_entries(character_id,boss_id,reset_period,period_start) SELECT 1,min(id),'WEEKLY','2026-10-01' FROM boss_master;
INSERT INTO boss_period_selections(character_id,boss_id,reset_period,period_start,is_visible) SELECT 1,min(id),'WEEKLY','2026-10-01',true FROM boss_master;
INSERT INTO boss_item_acquisitions(character_id,boss_drop_item_id,acquired_date,boss_period_entry_id) SELECT 1,min(id),'2026-10-01',1 FROM boss_drop_items;
INSERT INTO scheduler_week_finalizations(user_id,character_id,week_start_date) VALUES (1,1,'2026-10-01');
INSERT INTO scheduler_monthly_finalizations(user_id,character_id,month_start_date) VALUES (1,1,'2026-10-01');
INSERT INTO scheduler_monthly_boss_snapshots SELECT 1,min(id),true FROM boss_master;
INSERT INTO sync_jobs(user_id,job_type,status) VALUES (1,'CHARACTER','COMPLETED');
INSERT INTO data_sync_logs(user_id,sync_type,status,started_at) VALUES (1,'CHARACTER_SYNC','COMPLETED',CURRENT_TIMESTAMP);
INSERT INTO data_merge_provenance(table_name,record_id,source_user_id) VALUES ('characters',1,1);
INSERT INTO data_preference_sources(setting_type,record_id,source_user_id,state,saved_at) VALUES ('FAVORITE',1,1,'EXPLICIT',CURRENT_TIMESTAMP);
INSERT INTO data_context_merge_audits(target_user_id,source_user_id,before_state,after_state) VALUES (1,1,'{}','{}');
-- Out-of-order installed ranks are legitimate when V9 was installed after V12.
UPDATE flyway_schema_history SET installed_rank=-installed_rank;
UPDATE flyway_schema_history SET installed_rank=100+installed_rank;
SELECT setval(pg_get_serial_sequence('hunting_grounds','id'),1000,true);
DO $$ DECLARE t text; s text;
BEGIN
 FOR t IN SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename NOT IN ('hunting_grounds','flyway_schema_history') LOOP
  IF EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name=t AND column_name='id') THEN
   s:=pg_get_serial_sequence('public.'||t,'id');
   IF s IS NOT NULL THEN EXECUTE format('SELECT setval(%L,400,true)',s); END IF;
  END IF;
 END LOOP;
END $$;
SQL
snapshot > "$temp_dir/before.snapshot"

bash "$script_dir/manual-backup.sh" "$container" "$temp_dir/backups" > "$temp_dir/backup.log"
backup_dir=$(find "$temp_dir/backups" -mindepth 1 -maxdepth 1 -type d)
test -s "$backup_dir/maple.dump"
test ! -e "$backup_dir/maple.dump.partial"
python3 - "$backup_dir" <<'PY'
from pathlib import Path
import stat, sys
p = Path(sys.argv[1])
assert stat.S_IMODE(p.stat().st_mode) == 0o700
assert all(stat.S_IMODE(f.stat().st_mode) == 0o600 for f in p.iterdir())
PY
(cd "$backup_dir"; shasum -a 256 -c SHA256SUMS) >/dev/null
rg -q '^users=1$' "$backup_dir/manifest.txt"
sql <<'SQL'
CREATE DATABASE restored;
SQL
docker exec -i "$container" pg_restore -U fixture -d restored --no-owner --no-privileges \
  --exit-on-error --single-transaction < "$backup_dir/maple.dump"
test_db=restored snapshot > "$temp_dir/restored.snapshot"
cmp "$temp_dir/before.snapshot" "$temp_dir/restored.snapshot"
ok 'manual backup: checksum, manifest, full isolated restore and sequence equivalence'
if bash "$script_dir/manual-backup.sh" "${container}-missing" "$temp_dir/failed-backups" > "$temp_dir/failed-backup.log" 2>&1; then
  printf 'FAIL missing container accepted as backup\n' >&2; exit 1
fi
if find "$temp_dir/failed-backups" -name maple.dump | rg -q .; then
  printf 'FAIL failed backup promoted to completed dump\n' >&2; exit 1
fi
ok 'backup failure never creates a completed dump'

expect_failure 'missing confirmation' 'Require RESET_CONFIRM' \
  env -u RESET_CONFIRM -u RESET_BACKUP_VERIFIED -u RESET_WRITERS_STOPPED \
  bash "$script_dir/reset-user-data.sh" "$container" "$temp_dir/catalog.json"
snapshot > "$temp_dir/after.snapshot"; cmp "$temp_dir/before.snapshot" "$temp_dir/after.snapshot"
ok 'missing confirmation refuses all mutation'

sql <<'SQL'
CREATE TABLE unexpected_fixture(id bigint PRIMARY KEY, user_id bigint REFERENCES users(id));
INSERT INTO unexpected_fixture VALUES(1,1);
SQL
snapshot > "$temp_dir/unknown.snapshot"
expect_failure 'unknown table' 'Public table inventory mismatch' reset "$temp_dir/catalog.json"
snapshot > "$temp_dir/after.snapshot"; cmp "$temp_dir/unknown.snapshot" "$temp_dir/after.snapshot"
sql <<'SQL'
DROP TABLE unexpected_fixture;
SQL
ok 'unknown dependent table prevents reset and preserves every row and sequence'

python3 - "$temp_dir" <<'PY'
import json, pathlib, sys
p = pathlib.Path(sys.argv[1]); original = json.loads((p/'catalog.json').read_text())
for name in ['checksum','inactive','unmatched','missing-field','empty','malformed']:
    c = json.loads(json.dumps(original))
    if name == 'checksum': c['migrations'][0][3] += 1
    elif name == 'inactive': c['drops'][0]['is_active'] = False
    elif name == 'unmatched': c['drops'][0]['boss_name'] = 'fixture-does-not-exist'
    elif name == 'missing-field': del c['bosses'][0]['crystal_price']
    elif name == 'empty': c['bosses'] = []
    (p/f'{name}.json').write_text('{' if name == 'malformed' else json.dumps(c))
PY
for scenario in checksum inactive unmatched missing-field empty malformed; do
  case "$scenario" in
    checksum) expected='Flyway history differs' ;;
    inactive) expected='Inactive drop' ;;
    unmatched) expected='Unmatched canonical drop' ;;
    missing-field) expected='null value in column "crystal_price"' ;;
    empty) expected='AssertionError' ;;
    malformed) expected='JSONDecodeError' ;;
  esac
  expect_failure "$scenario" "$expected" reset "$temp_dir/$scenario.json"
  snapshot > "$temp_dir/after.snapshot"; cmp "$temp_dir/before.snapshot" "$temp_dir/after.snapshot"
  ok "$scenario catalog rejection preserves all rows and sequences"
done

# Confirm the late failure above really occurs after TRUNCATE and sequence reuse.
reset "$temp_dir/catalog.json" > "$temp_dir/reset.log"
rg -q reset_verified "$temp_dir/reset.log"
sql > "$temp_dir/assertions.log" <<'SQL'
DO $$ DECLARE t text; s text; value bigint;
BEGIN
 FOR t IN SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename NOT IN ('boss_master','boss_drop_items','hunting_grounds','flyway_schema_history') LOOP
  EXECUTE format('SELECT count(*) FROM public.%I',t) INTO value;
  IF value<>0 THEN RAISE EXCEPTION 'Remaining fixture data: %',t; END IF;
  s:=NULL;
  IF EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name=t AND column_name='id') THEN
   s:=pg_get_serial_sequence('public.'||t,'id');
  END IF;
  IF s IS NOT NULL THEN
   EXECUTE format('SELECT nextval(%L)',s) INTO value;
   IF value<>1 THEN RAISE EXCEPTION 'Wrong next ID in %: %',t,value; END IF;
  END IF;
 END LOOP;
 IF (SELECT count(*) FROM boss_master)<>54 OR (SELECT count(*) FROM boss_drop_items)<>282 THEN RAISE EXCEPTION 'Wrong catalog count'; END IF;
 IF EXISTS(SELECT 1 FROM (SELECT id,row_number() OVER(ORDER BY sort_order,array_position(ARRAY['EASY','NORMAL','HARD','CHAOS','EXTREME'],difficulty::text),boss_name COLLATE "C") AS wanted FROM boss_master) x WHERE id<>wanted) THEN RAISE EXCEPTION 'Incorrect boss order'; END IF;
 IF EXISTS(SELECT 1 FROM boss_drop_items WHERE NOT is_active) THEN RAISE EXCEPTION 'Inactive drop retained'; END IF;
 IF nextval(pg_get_serial_sequence('boss_master','id'))<>55 OR nextval(pg_get_serial_sequence('boss_drop_items','id'))<>283 THEN RAISE EXCEPTION 'Catalog next ID mismatch'; END IF;
 IF nextval(pg_get_serial_sequence('hunting_grounds','id'))<>1001 THEN RAISE EXCEPTION 'Hunting sequence changed'; END IF;
END $$;
SQL
ok 'successful reset clears every user table and resets next IDs with deterministic catalog order'
bash "$script_dir/export-reset-catalog.sh" "$container" "$temp_dir/reset-catalog.json" >/dev/null
python3 - "$temp_dir/catalog.json" "$temp_dir/reset-catalog.json" <<'PY'
import json, sys
before, after = [json.load(open(p)) for p in sys.argv[1:]]
for key in ('bosses', 'drops'):
    normalize = lambda rows: sorted(json.dumps(r, sort_keys=True) for r in rows)
    assert normalize(before[key]) == normalize(after[key]), key
PY
ok 'canonical prices, party limits, boss fields and remapped drop associations match exactly'

sql <<'SQL' > "$temp_dir/preserved.snapshot"
SELECT row_to_json(t) FROM hunting_grounds t ORDER BY id;
SELECT row_to_json(t) FROM flyway_schema_history t ORDER BY installed_rank;
SQL
test_db=restored sql <<'SQL' > "$temp_dir/preserved-original.snapshot"
SELECT row_to_json(t) FROM hunting_grounds t ORDER BY id;
SELECT row_to_json(t) FROM flyway_schema_history t ORDER BY installed_rank;
SQL
cmp "$temp_dir/preserved-original.snapshot" "$temp_dir/preserved.snapshot"
ok 'hunting catalog and full Flyway rows preserved despite different migration installation order'
printf 'Results: %s passed, 0 failed, 0 skipped\n' "$passed"
