#!/usr/bin/env bash
set -euo pipefail

: "${BACKUP_DIR:?BACKUP_DIR is required}"

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$script_dir/lib/rclone-env.sh"
source "$script_dir/lib/healthcheck.sh"

healthcheck_url="${HEALTHCHECK_DAILY_URL:-}"

mkdir -p "$BACKUP_DIR"

timestamp="$(date +%Y%m%d-%H%M%S)"
backup_file="$BACKUP_DIR/maple-daily-$timestamp.sql.gz"
partial_file="$backup_file.partial"
finish() {
  local rc=$?
  rm -f -- "$partial_file"
  if [[ $rc -eq 0 ]]; then
    hc_ping "$healthcheck_url"
  else
    hc_ping "$healthcheck_url" /fail
  fi
}
trap finish EXIT

docker exec maple-postgres sh -eu -c '
  export PGPASSWORD="$POSTGRES_PASSWORD"
  exec pg_dump -w -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists
' | gzip > "$partial_file"
gzip -t "$partial_file"
mv "$partial_file" "$backup_file"

rclone copyto "$backup_file" "r2:${R2_BUCKET}/daily/$(basename "$backup_file")"

find "$BACKUP_DIR" -name 'maple-daily-*.sql.gz' -mtime +30 -delete
