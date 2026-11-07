#!/usr/bin/env bash
# 수동 덤프 디렉터리 목록 조회 (읽기 전용). 서버·PC 공용.
# 사실 값(시각·해시·행 수)은 각 디렉터리의 manifest.txt·SHA256SUMS를 정본으로 읽어 표시하며 덤프 내용은 열지 않음.
set -euo pipefail
[[ $# -ge 1 ]] || { echo 'Usage: list-backups.sh BACKUP_ROOT [BACKUP_ROOT...]' >&2; exit 2; }

if command -v sha256sum >/dev/null; then sha_check() { sha256sum -c SHA256SUMS >/dev/null 2>&1; }
else sha_check() { shasum -a 256 -c SHA256SUMS >/dev/null 2>&1; }; fi

manifest_value() { sed -n "s/^$1=//p" "$2" 2>/dev/null | head -1; }

warnings=0
printf '%-48s %-19s %6s %-12s %6s %10s  %s\n' 디렉터리 관측시각\(UTC\) 크기 SHA-256 users characters 상태
for root in "$@"; do
  [[ -d "$root" ]] || { echo "경고: 디렉터리 없음: $root" >&2; warnings=$((warnings+1)); continue; }
  while IFS= read -r sums; do
    dir=$(dirname "$sums")
    name=${dir#"$root"/}
    status=OK
    sha=$(awk 'NR==1{print substr($1,1,12)}' "$sums")
    if ! (cd "$dir" && sha_check); then status="체크섬불일치"; fi
    [[ -z "$(find "$dir" -maxdepth 1 -name '*.partial' -print -quit)" ]] || status="$status,partial존재"
    observed='-' ; users='-' ; chars='-'
    if [[ -f "$dir/manifest.txt" ]]; then
      observed=$(manifest_value observed_at "$dir/manifest.txt" | cut -c1-19)
      users=$(manifest_value users "$dir/manifest.txt")
      chars=$(manifest_value characters "$dir/manifest.txt")
    else
      status="$status,manifest없음"
    fi
    size=$(du -sh "$dir" | awk '{print $1}')
    [[ $status == OK ]] || warnings=$((warnings+1))
    printf '%-48s %-19s %6s %-12s %6s %10s  %s\n' "$name" "${observed:--}" "$size" "$sha" "${users:--}" "${chars:--}" "$status"
  done < <(find "$root" -name SHA256SUMS -type f | sort)
done
[[ $warnings -eq 0 ]] || { echo "경고 항목 ${warnings}건" >&2; exit 1; }
