#!/usr/bin/env sh
set -eu
target=${1:?path required}
if grep -R -I -E '(Bearer[[:space:]]+[A-Za-z0-9._-]{20,}|OPENAI_API_KEY=.+|JWT_SECRET=.+|password[\"=:][[:space:]]*[^<${][^, ]+)' "$target" >/dev/null 2>&1; then
  echo "secret scan failed" >&2
  exit 1
fi
echo "secret scan passed"

