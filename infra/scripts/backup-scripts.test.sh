#!/usr/bin/env bash
# 백업·복구 스크립트 회귀 검증: docker·rclone 스텁으로 인자, 환경, 실패 전파를 확인. 외부 접속 없음.
set -euo pipefail
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
temp_dir=$(mktemp -d "${TMPDIR:-/tmp}/maple-backup-test.XXXXXX")
trap 'rm -rf -- "$temp_dir"' EXIT
passed=0
ok() { passed=$((passed+1)); printf 'PASS %s\n' "$1"; }
fail() { printf 'FAIL %s\n' "$1" >&2; exit 1; }

secret_id=test-access-key-id-not-real
secret_key=test-secret-access-key-not-real
stub_dir="$temp_dir/bin"
mkdir -p "$stub_dir"

cat > "$stub_dir/docker" <<'STUB'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$LOG_DIR/docker.args"
if [[ "${1:-}" == exec && "${2:-}" == -i ]]; then
  cat > "$LOG_DIR/restore.stdin"
  [[ "${FAKE_RESTORE_FAIL:-}" == 1 ]] && exit 3
  exit 0
fi
[[ "${FAKE_DUMP_FAIL:-}" == 1 ]] && exit 1
echo '-- fake dump'
STUB
cat > "$stub_dir/rclone" <<'STUB'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$LOG_DIR/rclone.args"
env | grep '^RCLONE_CONFIG_R2_' | sed 's/=.*//' | sort > "$LOG_DIR/rclone.env.names"
env | grep '^RCLONE_CONFIG_R2_NO_CHECK_BUCKET=' >> "$LOG_DIR/rclone.env.flags" || true
case "$1" in
  ls) echo '   12 maple-daily-20260101-030000.sql.gz'; echo '   12 maple-daily-20260102-030000.sql.gz' ;;
  copyto) if [[ "$2" == r2:* ]]; then echo 'SELECT 1;' | gzip > "$3"; fi ;;
esac
STUB
chmod +x "$stub_dir/docker" "$stub_dir/rclone"

# 케이스별 격리된 실행 환경. 성공 시 0, 실패 시 종료 코드를 stdout 끝에 기록
run_case() {
  local name=$1; shift
  case_dir="$temp_dir/$name"
  mkdir -p "$case_dir/log" "$case_dir/backups"
  LOG_DIR="$case_dir/log" PATH="$stub_dir:$PATH" BACKUP_DIR="$case_dir/backups" \
    R2_ACCESS_KEY_ID="$secret_id" R2_SECRET_ACCESS_KEY="$secret_key" \
    R2_BUCKET=test-bucket R2_ENDPOINT=https://example.invalid \
    "$@" > "$case_dir/out.log" 2>&1 && rc=0 || rc=$?
}

for kind in daily weekly; do
  run_case "$kind" bash "$script_dir/backup-$kind.sh"
  [[ $rc == 0 ]] || { cat "$case_dir/out.log" >&2; fail "$kind 백업 성공 경로"; }
  grep -qE "^copyto .*/maple-$kind-.*\.sql\.gz r2:test-bucket/$kind/maple-$kind-.*\.sql\.gz$" "$case_dir/log/rclone.args" \
    || fail "$kind R2 경로"
  ! grep -q -e "$secret_id" -e "$secret_key" "$case_dir/log/rclone.args" "$case_dir/log/docker.args" \
    || fail "$kind 시크릿 argv 노출"
  grep -q 'RCLONE_CONFIG_R2_ACCESS_KEY_ID' "$case_dir/log/rclone.env.names" || fail "$kind rclone 환경변수 리모트"
  grep -q '^RCLONE_CONFIG_R2_NO_CHECK_BUCKET=true$' "$case_dir/log/rclone.env.flags" || fail "$kind no_check_bucket"
  grep -q -e '--clean --if-exists' "$case_dir/log/docker.args" || fail "$kind 덤프 옵션"
  [[ -z "$(find "$case_dir/backups" -name '*.partial')" ]] || fail "$kind partial 잔존"
  [[ -n "$(find "$case_dir/backups" -name "maple-$kind-*.sql.gz")" ]] || fail "$kind 로컬 덤프"
  ok "$kind 백업 업로드·시크릿 미노출·덤프 옵션"

  run_case "$kind-dumpfail" env FAKE_DUMP_FAIL=1 bash "$script_dir/backup-$kind.sh"
  [[ $rc != 0 ]] || fail "$kind 덤프 실패 전파"
  [[ ! -e "$case_dir/log/rclone.args" ]] || fail "$kind 덤프 실패 시 업로드 금지"
  [[ -z "$(find "$case_dir/backups" -type f)" ]] || fail "$kind 덤프 실패 시 파일 잔존"
  ok "$kind 덤프 실패 시 중단·업로드 없음·파일 정리"

  run_case "$kind-nobucket" env -u R2_BUCKET bash "$script_dir/backup-$kind.sh"
  [[ $rc != 0 && ! -e "$case_dir/log/docker.args" ]] || fail "$kind 환경변수 누락 시 즉시 실패"
  ok "$kind 환경변수 누락 시 덤프 전 실패"
done

# restore: 최신 파일 선택, 오류 전파, 시크릿 미노출
run_case restore bash -c "printf 'yes\n' | bash '$script_dir/restore.sh' daily"
[[ $rc == 0 ]] || { cat "$case_dir/out.log" >&2; fail "restore 성공 경로"; }
grep -q 'maple-daily-20260102-030000.sql.gz' "$case_dir/out.log" || fail "restore 최신 선택"
grep -q -e 'ON_ERROR_STOP=1' -e '--single-transaction' "$case_dir/log/docker.args" || fail "restore psql 옵션"
grep -q '복구 완료' "$case_dir/out.log" || fail "restore 완료 메시지"
! grep -q -e "$secret_id" -e "$secret_key" "$case_dir/log/rclone.args" "$case_dir/log/docker.args" || fail "restore 시크릿 노출"
ok "restore 최신 선택·psql 안전 옵션·시크릿 미노출"

run_case restorefail env FAKE_RESTORE_FAIL=1 bash -c "printf 'yes\n' | bash '$script_dir/restore.sh' daily"
[[ $rc != 0 ]] || fail "restore 오류 전파"
! grep -q '복구 완료' "$case_dir/out.log" || fail "restore 실패 시 완료 메시지 금지"
ok "restore 실패 시 비정상 종료·완료 메시지 없음"

# setup-cron: 로그 파일 사전 생성과 logrotate 설치 (root 필요 동작이라 정적 검증)
grep -q 'touch "\$LOG_FILE"' "$script_dir/setup-cron.sh" || fail "setup-cron 로그 생성"
grep -q 'chown "\$DEPLOY_USER:\$DEPLOY_USER" "\$LOG_FILE"' "$script_dir/setup-cron.sh" || fail "setup-cron 로그 소유자"
grep -q '/etc/logrotate.d/maple-backup' "$script_dir/setup-cron.sh" || fail "setup-cron logrotate"
grep -q '>> /var/log/maple-backup.log' "$script_dir/../cron/maple-backup" || fail "cron 템플릿 로그 경로 일치"
ok "setup-cron 로그 파일·logrotate 정의와 cron 경로 일치"

printf 'ALL PASS (%d)\n' "$passed"
