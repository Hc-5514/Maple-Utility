#!/usr/bin/env bash
# Local-only PostgreSQL custom backup; no cloud upload or deletion.
set -euo pipefail
umask 077
[[ $# == 2 ]] || { echo 'Usage: manual-backup.sh CONTAINER BACKUP_ROOT' >&2; exit 2; }
container=$1
root=$2
mkdir -p "$root"
chmod 700 "$root"
out="$root/$(date -u +%Y%m%dT%H%M%SZ)-$$"
mkdir -m 700 "$out"
docker exec "$container" sh -eu -c '
 export PGPASSWORD="$POSTGRES_PASSWORD"
 exec pg_dump -w -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc --lock-wait-timeout=10s
' > "$out/maple.dump.partial" 2> "$out/pg_dump.log"
test -s "$out/maple.dump.partial"
docker exec -i "$container" pg_restore --list < "$out/maple.dump.partial" > "$out/archive-list.txt"
test -s "$out/archive-list.txt"
mv "$out/maple.dump.partial" "$out/maple.dump"
# Counts are a separate observation, not the pg_dump snapshot; stop writes for exact comparison.
docker exec -i "$container" sh -eu -c '
 export PGPASSWORD="$POSTGRES_PASSWORD"
 exec psql -X -w -qAt -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"
' > "$out/manifest.txt" <<'SQL'
SELECT 'observed_at=' || CURRENT_TIMESTAMP;
SELECT 'database=' || current_database();
SELECT 'server_version=' || current_setting('server_version');
SELECT 'database_bytes=' || pg_database_size(current_database());
SELECT format('SELECT %L || count(*) FROM %I.%I;', tablename || '=', schemaname, tablename)
FROM pg_tables WHERE schemaname='public' ORDER BY tablename \gexec
SQL
(
 cd "$out"
 if command -v sha256sum >/dev/null; then sha256sum maple.dump > SHA256SUMS; sha256sum -c SHA256SUMS;
 else shasum -a 256 maple.dump > SHA256SUMS; shasum -a 256 -c SHA256SUMS; fi
)
printf 'Backup directory: %s\n' "$out"
printf 'Review pg_dump.log; restore into an isolated database before marking verified.\n'
