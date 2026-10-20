#!/usr/bin/env bash
# Explicit manual operation only. App/writers must be stopped before invocation.
set -euo pipefail
umask 077
[[ $# == 2 ]] || { echo 'Usage: reset-user-data.sh TARGET_CONTAINER CATALOG_JSON' >&2; exit 2; }
[[ ${RESET_CONFIRM:-} == RESET-ALL-USER-DATA && ${RESET_BACKUP_VERIFIED:-} == yes && ${RESET_WRITERS_STOPPED:-} == yes ]] || {
 echo 'Require RESET_CONFIRM=RESET-ALL-USER-DATA RESET_BACKUP_VERIFIED=yes RESET_WRITERS_STOPPED=yes' >&2; exit 2;
}
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
# Hex transports JSON as data, never as executable SQL or psql meta commands.
catalog_hex=$(python3 -c 'import json,sys; d=json.load(open(sys.argv[1])); assert all(d.get(k) for k in ("bosses","drops","migrations")); print(json.dumps(d,ensure_ascii=False).encode().hex())' "$2")
{
 printf '\\set ON_ERROR_STOP on\nBEGIN;\nSET LOCAL lock_timeout = '\''10s'\'';\nSET LOCAL statement_timeout = '\''120s'\'';\n'
 printf 'CREATE TEMP TABLE reset_catalog (payload jsonb) ON COMMIT DROP;\n'
 printf "INSERT INTO reset_catalog VALUES (convert_from(decode('%s','hex'),'UTF8')::jsonb);\n" "$catalog_hex"
 cat "$script_dir/reset-user-data.sql"
 printf '\nCOMMIT;\n'
} | docker exec -i "$1" sh -eu -c '
 export PGPASSWORD="$POSTGRES_PASSWORD"
 exec psql -X -w -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"
'
