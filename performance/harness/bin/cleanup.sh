#!/usr/bin/env sh
set -eu

redis_host=${1:?redis host required}
redis_port=${2:?redis port required}
ledger=${3:?exact Redis key ledger required}
output=${4:?cleanup report output required}

script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
before_report=$(mktemp "${TMPDIR:-/tmp}/planwith-redis-before.XXXXXX")
trap 'rm -f "$before_report"' EXIT INT TERM

"$script_dir/verify-redis-key-ledger.sh" \
  "$redis_host" "$redis_port" "$ledger" before "$before_report"

jq -r '.entries[] | [.tokenMappingKey,.payloadKey] | @tsv' "$ledger" |
while IFS="$(printf '\t')" read -r token_key payload_key; do
  redis-cli --no-auth-warning -h "$redis_host" -p "$redis_port" --raw DEL \
    "$token_key" "$payload_key" >/dev/null
done

"$script_dir/verify-redis-key-ledger.sh" \
  "$redis_host" "$redis_port" "$ledger" after "$output"

rm -f "$before_report"
trap - EXIT INT TERM
