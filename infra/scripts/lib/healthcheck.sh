#!/usr/bin/env bash
# source 전용. healthchecks.io 방식 dead-man ping.
# 사용: hc_ping <ping URL> [/fail]  (URL 미설정 시 경고 후 건너뜀, ping 실패는 백업 결과에 영향 없음)
# ping URL은 시크릿으로 취급하므로 curl 설정을 stdin(-K -)으로 전달해 명령행(ps)에 싣지 않음.
hc_ping() {
  local url=${1:-} suffix=${2:-}
  if [[ -z "$url" ]]; then
    echo "경고: HEALTHCHECK URL 미설정, dead-man ping 건너뜀" >&2
    return 0
  fi
  if ! printf 'url = "%s%s"\n' "$url" "$suffix" \
    | curl -fsS -m 10 --retry 3 -o /dev/null -K - 2>/dev/null; then
    echo "경고: dead-man ping 전송 실패${suffix:+ ($suffix)}" >&2
  fi
  return 0
}
