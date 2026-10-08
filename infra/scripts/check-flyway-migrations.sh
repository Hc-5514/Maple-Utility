#!/usr/bin/env bash

set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 <base-commit> <head-commit>" >&2
  exit 2
fi

base_commit="$1"
head_commit="$2"
migration_path='backend/src/main/resources/db/migration/V*__*.sql'
migration_dir='backend/src/main/resources/db/migration'

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

highest_version=0
while IFS= read -r path; do
  filename="${path##*/}"
  if [[ "$filename" =~ ^V([0-9]+)__.*\.sql$ ]]; then
    version=$((10#${BASH_REMATCH[1]}))
    if (( version > highest_version )); then
      highest_version=$version
    fi
  fi
done < <(git ls-tree -r --name-only "$base_commit" -- "$migration_dir")

added_migrations="$(git diff --name-only --diff-filter=A "$base_commit" "$head_commit" -- "$migration_dir")"
while IFS= read -r path; do
  [[ -z "$path" ]] && continue
  filename="${path##*/}"
  if [[ ! "$filename" =~ ^V([0-9]+)__.*\.sql$ ]]; then
    echo "Invalid Flyway migration name: $path" >&2
    exit 1
  fi
  version=$((10#${BASH_REMATCH[1]}))
  if (( version <= highest_version )); then
    echo "New Flyway migration version $version must exceed base version $highest_version: $path" >&2
    exit 1
  fi
done <<< "$added_migrations"

echo 'Flyway migration immutability check passed.'
