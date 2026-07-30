#!/usr/bin/env sh
set -eu

redis_host=${1:?redis host required}
redis_port=${2:?redis port required}
ledger=${3:?exact Redis key ledger required}
output=${4:?output path required}

script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
"$script_dir/verify-redis-key-ledger.sh" \
  "$redis_host" "$redis_port" "$ledger" before "$output"
