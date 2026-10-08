#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
guard_script="$script_dir/check-flyway-migrations.sh"
test_repo="$(mktemp -d)"
trap 'rm -rf "$test_repo"' EXIT

git -C "$test_repo" init -q
git -C "$test_repo" config user.name 'Flyway Guard Test'
git -C "$test_repo" config user.email 'flyway-guard@example.com'

migration_dir="$test_repo/backend/src/main/resources/db/migration"
mkdir -p "$migration_dir"
printf '%s\n' 'CREATE TABLE sample (id BIGINT);' > "$migration_dir/V1__init.sql"
git -C "$test_repo" add .
git -C "$test_repo" commit -q -m 'baseline'
baseline_commit="$(git -C "$test_repo" rev-parse HEAD)"

printf '%s\n' 'ALTER TABLE sample ADD COLUMN name VARCHAR(100);' > "$migration_dir/V2__add_name.sql"
git -C "$test_repo" add .
git -C "$test_repo" commit -q -m 'add migration'
addition_commit="$(git -C "$test_repo" rev-parse HEAD)"

(
  cd "$test_repo"
  "$guard_script" "$baseline_commit" "$addition_commit"
)

printf '%s\n' '-- changed' >> "$migration_dir/V1__init.sql"
git -C "$test_repo" add .
git -C "$test_repo" commit -q -m 'modify migration'
modification_commit="$(git -C "$test_repo" rev-parse HEAD)"

if (
  cd "$test_repo"
  "$guard_script" "$addition_commit" "$modification_commit"
); then
  echo 'Expected an existing migration modification to fail.' >&2
  exit 1
fi

git -C "$test_repo" mv \
  backend/src/main/resources/db/migration/V2__add_name.sql \
  backend/src/main/resources/db/migration/V3__rename_name.sql
git -C "$test_repo" commit -q -m 'rename migration'
rename_commit="$(git -C "$test_repo" rev-parse HEAD)"

if (
  cd "$test_repo"
  "$guard_script" "$modification_commit" "$rename_commit"
); then
  echo 'Expected an existing migration rename to fail.' >&2
  exit 1
fi

echo 'Flyway migration guard tests passed.'
