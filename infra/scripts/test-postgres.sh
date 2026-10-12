#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd "$script_dir/../.." && pwd)"
env_file="$repo_dir/.env.test.local"
expected_url='jdbc:postgresql://127.0.0.1:5433/maple_flyway_test'

fail() {
  printf '오류: %s\n' "$1" >&2
  exit 1
}

usage() {
  cat <<'HELP'
사용법: infra/scripts/test-postgres.sh {init|up|test|down}
  init  테스트 전용 연결 정보 및 임의 비밀번호 생성 (기존 파일 보존)
  up    독립 PostgreSQL 기동 및 준비 상태 대기
  test  PostgreSQL 기동 후 Java 21 Gradle 전체 테스트 재실행
  down  테스트 컨테이너 제거 및 임시 데이터 초기화
HELP
}

load_config() {
  [[ ! -L "$env_file" ]] || fail '.env.test.local 심볼릭 링크 사용 금지'
  [[ -f "$env_file" && -O "$env_file" ]] || fail '현재 사용자 소유 .env.test.local 필요 (init 명령으로 생성)'
  local mode line key value
  case "$(uname -s)" in
    Darwin) mode="$(stat -f '%Lp' "$env_file")" ;;
    *) mode="$(stat -c '%a' "$env_file")" ;;
  esac
  [[ "$mode" == 600 ]] || fail '.env.test.local 권한 600 필요'
  unset FLYWAY_TEST_URL FLYWAY_TEST_USER FLYWAY_TEST_PASSWORD
  while IFS= read -r line || [[ -n "$line" ]]; do
    [[ -z "$line" || "$line" == \#* ]] && continue
    [[ "$line" == *=* ]] || fail '잘못된 테스트 환경변수 형식'
    key="${line%%=*}"
    value="${line#*=}"
    case "$key" in
      FLYWAY_TEST_URL)
        [[ "${FLYWAY_TEST_URL+x}" != x ]] || fail '중복 테스트 환경변수'
        [[ "$value" == "$expected_url" ]] || fail '허용된 테스트 DB 주소와 불일치'
        export FLYWAY_TEST_URL="$value" ;;
      FLYWAY_TEST_USER)
        [[ "${FLYWAY_TEST_USER+x}" != x ]] || fail '중복 테스트 환경변수'
        [[ "$value" == maple_test ]] || fail '허용된 테스트 사용자와 불일치'
        export FLYWAY_TEST_USER="$value" ;;
      FLYWAY_TEST_PASSWORD)
        [[ "${FLYWAY_TEST_PASSWORD+x}" != x ]] || fail '중복 테스트 환경변수'
        [[ "$value" =~ ^[a-f0-9]{64}$ ]] || fail 'init으로 생성한 64자리 임의 비밀번호 필요'
        export FLYWAY_TEST_PASSWORD="$value" ;;
      *) fail '지원하지 않는 테스트 환경변수' ;;
    esac
  done < "$env_file"
  [[ -n "${FLYWAY_TEST_URL:-}" && -n "${FLYWAY_TEST_USER:-}" && -n "${FLYWAY_TEST_PASSWORD:-}" ]] || fail '테스트 연결 정보 3개 모두 필요'
}

compose() {
  docker compose --project-name maple-utility-test --env-file "$env_file" \
    -f "$repo_dir/docker-compose.test.yml" "$@"
}

start_postgres() {
  command -v docker >/dev/null || fail 'Docker 설치 필요'
  compose up -d --wait --wait-timeout 60 postgres-test
}

check_java() {
  local java_bin java_version
  java_bin=java
  if [[ -n "${JAVA_HOME:-}" ]]; then
    java_bin="$JAVA_HOME/bin/java"
  fi
  java_version="$("$java_bin" -version 2>&1)" || fail 'Java 21 설치 및 JAVA_HOME 설정 필요'
  [[ "$java_version" =~ version\ \"21[.\"] ]] || fail 'Java 21 필요 (JAVA_HOME 확인)'
}

[[ $# == 1 ]] || { usage; exit 1; }
case "$1" in
  init)
    [[ ! -L "$env_file" ]] || fail '.env.test.local 심볼릭 링크 사용 금지'
    if [[ -e "$env_file" ]]; then
      load_config
      printf '기존 테스트 연결 정보 보존\n'
      exit 0
    fi
    command -v openssl >/dev/null || fail 'openssl 설치 필요'
    password="$(openssl rand -hex 32)"
    (
      umask 077
      set -o noclobber
      printf 'FLYWAY_TEST_URL=%s\nFLYWAY_TEST_USER=maple_test\nFLYWAY_TEST_PASSWORD=%s\n' \
        "$expected_url" "$password" > "$env_file"
    ) || fail '테스트 연결 정보 생성 실패 (기존 파일 보존)'
    unset password
    printf '.env.test.local 생성 완료 (권한 600, Git 제외)\n'
    ;;
  up)
    load_config
    start_postgres
    ;;
  test)
    load_config
    check_java
    start_postgres
    cd "$repo_dir/backend"
    ./gradlew test --rerun-tasks
    ;;
  down)
    load_config
    command -v docker >/dev/null || fail 'Docker 설치 필요'
    compose down
    ;;
  -h|--help) usage ;;
  *) usage; exit 1 ;;
esac
