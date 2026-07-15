#!/usr/bin/env bash
set -euo pipefail

DEPLOY_USER="${SUDO_USER:-ubuntu}"
DEPLOY_DIR="${DEPLOY_DIR:-/opt/maple-utility}"

if [[ "$(id -u)" -ne 0 ]]; then
  echo "sudo 권한으로 실행 필요" >&2
  exit 1
fi

apt-get update
apt-get install -y ca-certificates curl gnupg ufw

install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  | gpg --dearmor --yes -o /etc/apt/keyrings/docker.gpg
chmod a+r /etc/apt/keyrings/docker.gpg

. /etc/os-release
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu ${VERSION_CODENAME} stable" \
  > /etc/apt/sources.list.d/docker.list

apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

systemctl enable --now docker
usermod -aG docker "${DEPLOY_USER}"

install -d -m 0755 -o "${DEPLOY_USER}" -g "${DEPLOY_USER}" "${DEPLOY_DIR}"

ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable

echo "Oracle Cloud VM 초기 설정 완료"
echo "배포 사용자: ${DEPLOY_USER}"
echo "배포 경로: ${DEPLOY_DIR}"
echo "docker 그룹 적용을 위해 ${DEPLOY_USER} 재로그인 필요"
