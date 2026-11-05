#!/usr/bin/env bash
# source 전용. R2 리모트 `r2:`를 환경변수로 정의해 시크릿이 rclone 명령행(ps)에 노출되지 않도록 함.
: "${R2_ACCESS_KEY_ID:?R2_ACCESS_KEY_ID is required}"
: "${R2_SECRET_ACCESS_KEY:?R2_SECRET_ACCESS_KEY is required}"
: "${R2_BUCKET:?R2_BUCKET is required}"
: "${R2_ENDPOINT:?R2_ENDPOINT is required}"

export RCLONE_CONFIG_R2_TYPE=s3
export RCLONE_CONFIG_R2_PROVIDER=Cloudflare
export RCLONE_CONFIG_R2_ACCESS_KEY_ID="$R2_ACCESS_KEY_ID"
export RCLONE_CONFIG_R2_SECRET_ACCESS_KEY="$R2_SECRET_ACCESS_KEY"
export RCLONE_CONFIG_R2_ENDPOINT="$R2_ENDPOINT"
# 버킷 한정 토큰은 버킷 확인 요청에서 403이 날 수 있음
export RCLONE_CONFIG_R2_NO_CHECK_BUCKET=true
