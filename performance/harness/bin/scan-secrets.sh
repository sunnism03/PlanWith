#!/usr/bin/env sh
set -eu
target=${1:?path required}
if grep -R -I --exclude-dir=runtime --exclude=scan-secrets.sh \
  -E '(Bearer[[:space:]]+eyJ[A-Za-z0-9._-]+|sk-[A-Za-z0-9_-]{20,}|AKIA[A-Z0-9]{16}|BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY)' \
  "$target" >/dev/null 2>&1; then
  echo "secret scan failed" >&2
  exit 1
fi
echo "secret scan passed"
