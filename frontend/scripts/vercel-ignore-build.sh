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
else
  # Vercel clone에는 origin 원격이 없을 수 있어 공개 저장소 URL로도 main 조회
  repo_url=""
  if [ -n "${VERCEL_GIT_REPO_OWNER:-}" ] && [ -n "${VERCEL_GIT_REPO_SLUG:-}" ]; then
    repo_url="https://github.com/${VERCEL_GIT_REPO_OWNER}/${VERCEL_GIT_REPO_SLUG}.git"
  fi
  fetched=""
  for remote in origin $repo_url; do
    if git fetch --no-tags --depth=200 "$remote" main >/dev/null 2>&1; then fetched="$remote"; break; fi
  done
  [ -n "$fetched" ] || build "비교 기준 커밋 확인 불가"
  base=$(git merge-base FETCH_HEAD HEAD 2>/dev/null) || build "main merge-base 확인 실패"
  label="main merge-base ${base:0:7}"
fi

git diff --quiet "$base" HEAD -- .
case $? in
  0) skip "$label 이후 frontend 변경 없음" ;;
  1) build "$label 이후 frontend 변경 있음" ;;
  *) build "git diff 실패" ;;
esac
