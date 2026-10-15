#!/usr/bin/env bash
# Regression tests use only the dedicated local PostgreSQL test container.
set -euo pipefail
set +x

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd "$script_dir/../.." && pwd)"
diagnose="$script_dir/login-sharing-diagnose.sh"
container=maple-utility-test-postgres-test-1
test_database="login_diagnose_test_$$"
temp_dir="$(mktemp -d)"
passed=0
database_created=false

cleanup() {
  if [[ "$database_created" == true ]]; then
    docker exec "$container" dropdb -U maple_test --if-exists "$test_database" >/dev/null 2>&1 || true
  fi
  rm -rf "$temp_dir"
}
trap cleanup EXIT

fail() { printf 'FAIL: %s\n' "$1" >&2; exit 1; }
pass() { passed=$((passed + 1)); printf 'PASS: %s\n' "$1"; }

command -v docker >/dev/null || fail 'dedicated PostgreSQL test container required'
[[ "$(docker inspect --format '{{.Config.Image}}' "$container")" == postgres:16.9-alpine ]] ||
  fail 'unexpected test container image'
docker exec "$container" createdb -U maple_test "$test_database"
database_created=true
export PGHOST=/var/run/postgresql PGPORT=5432 PGDATABASE="$test_database" PGUSER=maple_test
export PGPASSWORD='diagnostic-password-must-never-appear'

sql() {
  docker exec -i "$container" psql -X -q -U maple_test -d "$test_database" \
    --set=ON_ERROR_STOP=1 "$@"
}

# Snapshot every public table, including audit JSON and unrelated owners. Only hashes leave SQL.
snapshot() {
  sql -At <<'SQL'
BEGIN READ ONLY;
SELECT format('SELECT %L, count(*), md5(coalesce(string_agg(row_to_json(t)::text, '''' ORDER BY row_to_json(t)::text), '''')) FROM public.%I t;', table_name, table_name)
FROM information_schema.tables
WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
ORDER BY table_name
\gexec
ROLLBACK;
SQL
}

assert_redacted() {
  local output="$1" sentinel
  for sentinel in diagnostic-password-must-never-appear encrypted-key-must-never-appear \
    oauth-id-must-never-appear account-id-must-never-appear audit-payload-must-never-appear \
    ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff; do
    if [[ "$output" == *"$sentinel"* ]]; then fail 'sensitive value exposed'; fi
  done
}

expect_rejected() {
  local label="$1"
  shift
  if bash "$diagnose" "$@" >"$temp_dir/rejected.log" 2>&1; then fail "$label accepted"; fi
  assert_redacted "$(cat "$temp_dir/rejected.log")"
  pass "$label rejected without sensitive output"
}

result_row() {
  awk -v heading="$1" 'index($0, heading) { getline; getline; gsub(/[[:space:]]/, ""); print; exit }' \
    "$temp_dir/result.log"
}

expect_rejected 'missing arguments'
expect_rejected 'zero owner' 0 1
expect_rejected 'negative context' 1 -1
expect_rejected 'SQL-like owner' '1;SELECT 1' 1
expect_rejected 'oversized owner' 1234567890123456789 1
expect_rejected 'invalid docker container' 1 1 --docker 'invalid/container'

if (unset PGHOST; bash "$diagnose" 1 1 --docker "$container") >"$temp_dir/rejected.log" 2>&1; then
  fail 'missing database host accepted'
fi
assert_redacted "$(cat "$temp_dir/rejected.log")"
pass 'missing database configuration rejected'

if (unset PGPASSWORD PGPASSFILE; bash "$diagnose" 1 1 --docker "$container") >"$temp_dir/rejected.log" 2>&1; then
  fail 'missing authentication accepted'
fi
assert_redacted "$(cat "$temp_dir/rejected.log")"
pass 'missing authentication rejected'

# Apply the real migration chain rather than constructing a lookalike schema.
sql >"$temp_dir/setup.log" 2>&1 <<'SQL'
CREATE TABLE flyway_schema_history (
    installed_rank integer PRIMARY KEY, version varchar(50), description varchar(200), success boolean
);
SQL
for version in {1..18}; do
  migrations=("$repo_dir/backend/src/main/resources/db/migration/V${version}__"*.sql)
  [[ -f "${migrations[0]}" ]] || continue
  sql --single-transaction <"${migrations[0]}" >>"$temp_dir/setup.log" 2>&1 || fail "migration V$version failed"
  printf "INSERT INTO flyway_schema_history VALUES (%s,'%s','regression fixture',true);\n" "$version" "$version" |
    sql >>"$temp_dir/setup.log" 2>&1
done

sql >>"$temp_dir/setup.log" 2>&1 <<'SQL'
INSERT INTO users(id,oauth_provider,oauth_id,nickname) VALUES
    (901,'KAKAO','oauth-id-must-never-appear','same nickname'),
    (902,'NEXON_APIKEY','another-oauth','same nickname'),
    (903,'NEXON_APIKEY','merged-oauth','same nickname');
UPDATE data_contexts SET canonical_context_id=(SELECT id FROM data_contexts WHERE owner_user_id=901)
WHERE owner_user_id=903;
INSERT INTO data_context_accounts(account_id,context_id)
SELECT 'account-id-must-never-appear',id FROM data_contexts WHERE owner_user_id=901;
INSERT INTO user_api_keys(user_id,encrypted_key,context_id,key_fingerprint)
SELECT 901,'encrypted-key-must-never-appear',id,repeat('f',64)
FROM data_contexts WHERE owner_user_id=901;
INSERT INTO user_api_keys(user_id,encrypted_key,context_id)
SELECT 902,'encrypted-key-must-never-appear',id FROM data_contexts WHERE owner_user_id=901;
INSERT INTO characters(id,user_id,ocid,character_name,is_favorite) VALUES
    (901,901,'target-ocid','target character',true),
    (902,902,'other-ocid','unrelated character',false);
INSERT INTO hunting_records(character_id,record_date,meso_earned) VALUES
    (901,'2026-10-15',1),(902,'2026-10-15',2);
INSERT INTO data_merge_provenance(table_name,record_id,source_user_id) VALUES ('characters',901,902);
INSERT INTO scheduler_monthly_finalizations(id,user_id,character_id,month_start_date,status) VALUES
    (901,901,901,'2026-10-01','FINALIZED'),(902,902,902,'2026-10-01','FINALIZED');
INSERT INTO scheduler_monthly_boss_snapshots(finalization_id,boss_id,is_completed)
SELECT f.id,b.id,true FROM scheduler_monthly_finalizations f
CROSS JOIN (SELECT min(id) AS id FROM boss_master) b;
SQL
context_id="$(sql -Atc 'SELECT id FROM data_contexts WHERE owner_user_id=901')"
merged_context="$(sql -Atc 'SELECT id FROM data_contexts WHERE owner_user_id=903')"

for version in 18 19; do
  if [[ $version == 19 ]]; then
    sql --single-transaction <"$repo_dir/backend/src/main/resources/db/migration/V19__preference_sources_and_merge_audits.sql" \
      >>"$temp_dir/setup.log" 2>&1 || fail 'migration V19 failed'
    sql >>"$temp_dir/setup.log" 2>&1 <<'SQL'
INSERT INTO flyway_schema_history VALUES (19,'19','regression fixture',true);
INSERT INTO data_context_merge_audits(target_user_id,source_user_id,before_state,after_state)
VALUES (901,902,'{"secret":"audit-payload-must-never-appear"}','{"favorites":[901]}');
SQL
  fi
  before="$(snapshot)"
  for run in 1 2; do
    if ! bash "$diagnose" 901 "$context_id" --docker "$container" >"$temp_dir/result.log" 2>&1; then
      fail "V$version diagnosis run $run failed"
    fi
    output="$(cat "$temp_dir/result.log")"
    assert_redacted "$output"
    [[ "$output" == *"$test_database"* && "$output" == *'ROLLBACK'* ]] || fail 'incomplete diagnosis'
    [[ "$output" =~ \|[[:space:]]*on[[:space:]]*\| ]] || fail 'transaction not read only'
    [[ "$(result_row monthly_snapshot_rows)" == 1 ]] || fail 'snapshot count includes unrelated owner'
    [[ "$(result_row 'characters | favorites')" == '1|1' ]] || fail 'character count includes unrelated owner'
    [[ "$(result_row 'key_rows | known_distinct_keys')" == '2|1|1' ]] || fail 'legacy duplicate key evidence absent'
    if [[ $version == 18 ]]; then
      [[ "$output" == *'data_preference_sources absent'* && "$output" == *'data_context_merge_audits absent'* ]] ||
        fail 'pre-migration diagnostics absent'
    else
      [[ "$output" == *'before_state_bytes'* && "$output" == *'FAVORITE'* ]] || fail 'new schema diagnostics absent'
    fi
    [[ "$(snapshot)" == "$before" ]] || fail "V$version diagnosis modified database"
    pass "V$version run $run read only, redacted and repeat safe"
  done
  expect_rejected "V$version wrong owner" 902 "$context_id" --docker "$container"
  expect_rejected "V$version missing context" 901 999999 --docker "$container"
  expect_rejected "V$version merged context" 903 "$merged_context" --docker "$container"
  [[ "$(snapshot)" == "$before" ]] || fail "V$version rejection modified database"
done
printf 'Diagnosis tests: %s passed, 0 failed, 0 skipped.\n' "$passed"
