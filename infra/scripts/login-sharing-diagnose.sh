#!/usr/bin/env bash
# No writes, key decryption, fingerprint values, OAuth IDs, or raw audit payloads.
set -euo pipefail
set +x

usage() {
  printf '%s\n' 'Usage: login-sharing-diagnose.sh OWNER_ID CONTEXT_ID [--docker CONTAINER]' \
    'Required: PGHOST PGPORT PGDATABASE PGUSER and PGPASSWORD (or local PGPASSFILE).' \
    'Docker mode forwards environment names only; database must be explicitly selected.'
}

[[ $# == 2 || ( $# == 4 && $3 == --docker ) ]] || { usage >&2; exit 2; }
[[ $1 =~ ^[1-9][0-9]{0,17}$ && $2 =~ ^[1-9][0-9]{0,17}$ ]] || { usage >&2; exit 2; }
: "${PGHOST:?PGHOST required}"
: "${PGPORT:?PGPORT required}"
: "${PGDATABASE:?PGDATABASE required}"
: "${PGUSER:?PGUSER required}"
[[ -n ${PGPASSWORD:-} || ( $# == 2 && -n ${PGPASSFILE:-} ) ]] || {
  printf '%s\n' 'PGPASSWORD or local PGPASSFILE required' >&2; exit 2;
}
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
args=(-X --no-password --set=ON_ERROR_STOP=1 --set="owner_id=$1" --set="context_id=$2" --file=-)
if [[ $# == 4 ]]; then
  [[ $4 =~ ^[a-zA-Z0-9][a-zA-Z0-9_.-]*$ ]] || { usage >&2; exit 2; }
  exec docker exec -i -e PGHOST -e PGPORT -e PGDATABASE -e PGUSER -e PGPASSWORD \
    "$4" psql "${args[@]}" < "$script_dir/login-sharing-diagnose.sql"
fi
exec psql "${args[@]}" < "$script_dir/login-sharing-diagnose.sql"
