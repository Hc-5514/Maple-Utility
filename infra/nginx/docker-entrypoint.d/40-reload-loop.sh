#!/bin/sh
set -eu

(
  while :; do
    sleep 6h
    nginx -s reload || true
  done
) &
