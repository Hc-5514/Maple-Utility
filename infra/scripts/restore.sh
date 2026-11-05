#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "사용법: $0 [daily|weekly] [파일명]"
  echo "  파일명 생략 시 R2의 최신 백업을 자동 선택"
  echo "  예) $0 daily"
  echo "  예) $0 weekly maple-weekly-20260101-040000.sql.gz"
  exit 1
}

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$script_dir/lib/rclone-env.sh"

DB_NAME="${DB_NAME:-maple}"

BACKUP_TYPE="${1:-}"
BACKUP_FILE="${2:-}"

if [[ -z "$BACKUP_TYPE" ]]; then
  usage
fi

if [[ "$BACKUP_TYPE" != "daily" && "$BACKUP_TYPE" != "weekly" ]]; then
  echo "오류: 백업 유형은 daily 또는 weekly 이어야 합니다." >&2
  usage
fi

R2_REMOTE="r2:${R2_BUCKET}"

if [[ -z "$BACKUP_FILE" ]]; then
  echo "R2에서 최신 ${BACKUP_TYPE} 백업 조회 중..."
  BACKUP_FILE="$(rclone ls "${R2_REMOTE}/${BACKUP_TYPE}/" \
    | sort -k2 \
    | awk '{print $2}' \
    | tail -1)"

  if [[ -z "$BACKUP_FILE" ]]; then
    echo "오류: R2 ${BACKUP_TYPE}/ 경로에 백업 파일이 없습니다." >&2
    exit 1
  fi
  echo "선택된 백업: ${BACKUP_FILE}"
fi

TMP_FILE="$(mktemp /tmp/maple-restore-XXXXXX.sql.gz)"
trap 'rm -f "$TMP_FILE"' EXIT

echo "R2에서 다운로드: ${BACKUP_TYPE}/${BACKUP_FILE}"
rclone copyto "${R2_REMOTE}/${BACKUP_TYPE}/${BACKUP_FILE}" "$TMP_FILE"

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  경고: '${DB_NAME}' 데이터베이스의 기존 데이터가 삭제됩니다."
echo "  복구 파일: ${BACKUP_FILE}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
read -r -p "계속하려면 'yes'를 입력하세요: " confirm
if [[ "$confirm" != "yes" ]]; then
  echo "복구 취소"
  exit 0
fi

echo "복구 진행 중..."
gunzip -c "$TMP_FILE" \
  | docker exec -i maple-postgres sh -eu -c '
    export PGPASSWORD="$POSTGRES_PASSWORD"
    exec psql -X -w -v ON_ERROR_STOP=1 --single-transaction -U "$POSTGRES_USER" -d "$POSTGRES_DB"
  '

echo "복구 완료: ${BACKUP_FILE}"
