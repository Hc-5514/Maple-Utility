#!/usr/bin/env sh
set -eu

: "${DB_PASSWORD:?DB_PASSWORD is required}"
: "${BACKUP_DIR:?BACKUP_DIR is required}"

mkdir -p "$BACKUP_DIR"

timestamp="$(date +%Y%m%d-%H%M%S)"
backup_file="$BACKUP_DIR/maple-weekly-$timestamp.sql.gz"

PGPASSWORD="$DB_PASSWORD" pg_dump \
  --host "${DB_HOST:-postgres}" \
  --port "${DB_PORT:-5432}" \
  --username "${DB_USER:-maple_user}" \
  --dbname "${DB_NAME:-maple}" \
  --format plain \
  | gzip > "$backup_file"

find "$BACKUP_DIR" -name 'maple-weekly-*.sql.gz' -mtime +84 -delete
