#!/usr/bin/env bash
# Vercel Ignored Build Step: exit 0 = 빌드 생략, exit 1 = 빌드 진행
# rootDirectory(frontend) 기준 변경이 없을 때만 생략하고, 판단 불가 시 항상 빌드
set -u

build() { echo "▶ Vercel 빌드 진행: $1"; exit 1; }
skip() { echo "⏭ Vercel 빌드 생략: $1"; exit 0; }

base=""
if [ -n "${VERCEL_GIT_PREVIOUS_SHA:-}" ] && git cat-file -e "${VERCEL_GIT_PREVIOUS_SHA}^{commit}" 2>/dev/null; then
  base="$VERCEL_GIT_PREVIOUS_SHA"
  label="직전 성공 배포 ${base:0:7}"
elif git fetch --no-tags --depth=200 origin main >/dev/null 2>&1; then
  base=$(git merge-base FETCH_HEAD HEAD 2>/dev/null) || build "main merge-base 확인 실패"
  label="main merge-base ${base:0:7}"
else
  build "비교 기준 커밋 확인 불가"
fi

git diff --quiet "$base" HEAD -- .
case $? in
  0) skip "$label 이후 frontend 변경 없음" ;;
  1) build "$label 이후 frontend 변경 있음" ;;
  *) build "git diff 실패" ;;
esac
