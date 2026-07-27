#!/usr/bin/env bash
set -euo pipefail

: "${DB_PASSWORD:?DB_PASSWORD is required}"
: "${BACKUP_DIR:?BACKUP_DIR is required}"
: "${R2_ACCESS_KEY_ID:?R2_ACCESS_KEY_ID is required}"
: "${R2_SECRET_ACCESS_KEY:?R2_SECRET_ACCESS_KEY is required}"
: "${R2_BUCKET:?R2_BUCKET is required}"
: "${R2_ENDPOINT:?R2_ENDPOINT is required}"

mkdir -p "$BACKUP_DIR"

timestamp="$(date +%Y%m%d-%H%M%S)"
backup_file="$BACKUP_DIR/maple-daily-$timestamp.sql.gz"

docker exec maple-postgres sh -c \
  "PGPASSWORD=\"$DB_PASSWORD\" pg_dump \
    -U \"${DB_USER:-maple_user}\" \
    \"${DB_NAME:-maple}\"" \
  | gzip > "$backup_file"

rclone copyto "$backup_file" \
  ":s3,provider=Cloudflare,access_key_id=${R2_ACCESS_KEY_ID},secret_access_key=${R2_SECRET_ACCESS_KEY},endpoint=${R2_ENDPOINT}:${R2_BUCKET}/daily/$(basename "$backup_file")"

find "$BACKUP_DIR" -name 'maple-daily-*.sql.gz' -mtime +30 -delete
