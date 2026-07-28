#!/usr/bin/env bash
set -euo pipefail

if [[ "$(id -u)" -ne 0 ]]; then
  echo "sudo 권한으로 실행 필요" >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_USER="${SUDO_USER:-ubuntu}"
DEPLOY_DIR="${DEPLOY_DIR:-/opt/maple-utility}"
BACKUP_DIR="${BACKUP_DIR:-/backups}"

if ! command -v rclone &>/dev/null; then
  echo "rclone 설치 중..."
  apt-get update -qq
  apt-get install -y rclone
fi

install -d -m 0750 -o "$DEPLOY_USER" -g "$DEPLOY_USER" "$BACKUP_DIR"

sed \
  -e "s|DEPLOY_USER|${DEPLOY_USER}|g" \
  -e "s|DEPLOY_DIR|${DEPLOY_DIR}|g" \
  "${SCRIPT_DIR}/../cron/maple-backup" \
  > /etc/cron.d/maple-backup

chmod 0644 /etc/cron.d/maple-backup

systemctl restart cron 2>/dev/null || service cron restart 2>/dev/null || true

echo "cron 설치 완료"
echo "  백업 사용자:  ${DEPLOY_USER}"
echo "  배포 경로:    ${DEPLOY_DIR}"
echo "  백업 경로:    ${BACKUP_DIR}"
echo "  cron 파일:    /etc/cron.d/maple-backup"
echo ""
echo "수동 테스트:"
echo "  sudo -u ${DEPLOY_USER} bash -c 'set -a; source ${DEPLOY_DIR}/.env; set +a; ${DEPLOY_DIR}/infra/scripts/backup-daily.sh'"
