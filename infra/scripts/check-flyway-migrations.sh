#!/usr/bin/env bash

set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 <base-commit> <head-commit>" >&2
  exit 2
fi

base_commit="$1"
head_commit="$2"
migration_path='backend/src/main/resources/db/migration/V*__*.sql'

git rev-parse --verify "${base_commit}^{commit}" >/dev/null
git rev-parse --verify "${head_commit}^{commit}" >/dev/null

changes="$(git diff --name-status --find-renames --diff-filter=MDR \
  "$base_commit" "$head_commit" -- "$migration_path")"

if [[ -n "$changes" ]]; then
  echo 'Applied Flyway migration files are immutable.' >&2
  echo 'Add a new versioned migration instead of modifying, deleting, or renaming an existing file.' >&2
  echo "$changes" >&2
  exit 1
fi

echo 'Flyway migration immutability check passed.'
