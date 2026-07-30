#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
redis_port=${REDIS_TEST_PORT:-16383}
tmp_dir=$(mktemp -d "${TMPDIR:-/tmp}/planwith-exact-cleanup.XXXXXX")
redis_pid=

cleanup_local_test() {
  test -z "$redis_pid" || kill "$redis_pid" 2>/dev/null || true
  test -z "$redis_pid" || wait "$redis_pid" 2>/dev/null || true
  rm -r "$tmp_dir"
}
trap cleanup_local_test EXIT INT TERM

redis-server \
  --bind 127.0.0.1 \
  --port "$redis_port" \
  --save '' \
  --appendonly no \
  --dir "$tmp_dir" \
  >"$tmp_dir/redis.log" 2>&1 &
redis_pid=$!

ready=false
for _ in $(seq 1 30); do
  if redis-cli -h 127.0.0.1 -p "$redis_port" PING >/dev/null 2>&1; then
    ready=true
    break
  fi
  sleep 1
done
test "$ready" = true

token=11111111-1111-1111-1111-111111111111
payload=22222222-2222-2222-2222-222222222222
unrelated=33333333-3333-3333-3333-333333333333

redis-cli -h 127.0.0.1 -p "$redis_port" \
  SETEX "chatbot_token:$token" 3600 "$payload" >/dev/null
redis-cli -h 127.0.0.1 -p "$redis_port" \
  SETEX "chatbot:$payload" 3600 '{}' >/dev/null
redis-cli -h 127.0.0.1 -p "$redis_port" \
  SETEX "chatbot:$unrelated" 3600 '{}' >/dev/null

printf '%s\n' "$token" > "$tmp_dir/tokens.runtime"
"$script_dir/record-redis-keys.sh" \
  127.0.0.1 "$redis_port" exact-cleanup-regression \
  "$tmp_dir/tokens.runtime" "$tmp_dir/ledger.json"
"$script_dir/cleanup.sh" \
  127.0.0.1 "$redis_port" "$tmp_dir/ledger.json" "$tmp_dir/cleanup.json"

test "$(redis-cli -h 127.0.0.1 -p "$redis_port" EXISTS "chatbot:$unrelated")" -eq 1
test "$(redis-cli -h 127.0.0.1 -p "$redis_port" TTL "chatbot:$unrelated")" -gt 0
test "$(redis-cli -h 127.0.0.1 -p "$redis_port" DBSIZE)" -eq 1
jq -e '.status == "PASS" and .phase == "after" and .exactKeyCount == 2' \
  "$tmp_dir/cleanup.json" >/dev/null

echo "exact-key cleanup regression: PASS"
