#!/usr/bin/env sh
set -eu

: "${DB_PASSWORD:?DB_PASSWORD is required}"
: "${BACKUP_DIR:?BACKUP_DIR is required}"

mkdir -p "$BACKUP_DIR"

timestamp="$(date +%Y%m%d-%H%M%S)"
backup_file="$BACKUP_DIR/maple-daily-$timestamp.sql.gz"

PGPASSWORD="$DB_PASSWORD" pg_dump \
  --host "${DB_HOST:-postgres}" \
  --port "${DB_PORT:-5432}" \
  --username "${DB_USER:-maple_user}" \
  --dbname "${DB_NAME:-maple}" \
  | gzip > "$backup_file"

find "$BACKUP_DIR" -name 'maple-daily-*.sql.gz' -mtime +30 -delete
