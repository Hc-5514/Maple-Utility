#!/usr/bin/env bash
# list-backups.sh 회귀 검증: 합성 덤프 디렉터리만 사용 (실제 덤프·DB 접근 없음)
set -euo pipefail
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
temp_dir=$(mktemp -d "${TMPDIR:-/tmp}/maple-list-test.XXXXXX")
trap 'rm -rf -- "$temp_dir"' EXIT
passed=0
ok() { passed=$((passed+1)); printf 'PASS %s\n' "$1"; }
fail() { printf 'FAIL %s\n' "$1" >&2; exit 1; }
sum() { if command -v sha256sum >/dev/null; then sha256sum "$@"; else shasum -a 256 "$@"; fi; }

make_dump() { # 이름 [manifest 포함 여부]
  local d="$temp_dir/root/$1"
  mkdir -p "$d"; printf 'synthetic-dump-%s' "$1" > "$d/maple.dump"
  (cd "$d" && sum maple.dump > SHA256SUMS)
  if [[ ${2:-yes} == yes ]]; then
    printf 'observed_at=2026-01-02 03:04:05.123+00\ncharacters=7\nusers=2\n' > "$d/manifest.txt"
  fi
}

make_dump good
out=$(bash "$script_dir/list-backups.sh" "$temp_dir/root") || fail "정상 디렉터리 종료 코드"
grep -Eq '^good +2026-01-02 03:04:05 +[^ ]+ +[0-9a-f]{12} +2 +7 +OK$' <<<"$out" || fail "정상 출력"
grep -q 'synthetic-dump' <<<"$out" && fail "덤프 내용 출력 금지"
ok "정상 덤프 목록 표시, 덤프 내용 미출력"

make_dump nomanifest no
bash "$script_dir/list-backups.sh" "$temp_dir/root" > "$temp_dir/o.log" 2> "$temp_dir/e.log" && fail "manifest 누락 경고 시 비정상 종료 필요"
grep -q 'manifest없음' "$temp_dir/o.log" || fail "manifest 누락 표시"
ok "manifest 누락 경고·비정상 종료"
rm -rf "$temp_dir/root/nomanifest"

make_dump tampered
printf 'x' >> "$temp_dir/root/tampered/maple.dump"
bash "$script_dir/list-backups.sh" "$temp_dir/root" > "$temp_dir/o.log" 2>&1 && fail "체크섬 불일치 시 비정상 종료 필요"
grep -q 'tampered.*체크섬불일치' "$temp_dir/o.log" || fail "체크섬 불일치 표시"
ok "체크섬 불일치 경고·비정상 종료"
rm -rf "$temp_dir/root/tampered"

make_dump partial
: > "$temp_dir/root/partial/maple.dump.partial"
bash "$script_dir/list-backups.sh" "$temp_dir/root" > "$temp_dir/o.log" 2>&1 && fail "partial 존재 시 비정상 종료 필요"
grep -q 'partial.*partial존재' "$temp_dir/o.log" || fail "partial 표시"
ok ".partial 존재 경고·비정상 종료"

bash "$script_dir/list-backups.sh" "$temp_dir/none" > "$temp_dir/o.log" 2>&1 && fail "없는 경로 비정상 종료 필요"
bash "$script_dir/list-backups.sh" >/dev/null 2>&1 && fail "인자 없음 비정상 종료 필요"
ok "없는 경로·인자 누락 처리"

printf 'ALL PASS (%d)\n' "$passed"
