#!/usr/bin/env sh
set -eu

redis_host=${1:?redis host required}
redis_port=${2:?redis port required}
run_id=${3:?run id required}
token_file=${4:?JMeter token file required}
output=${5:?Redis key ledger output required}

test -s "$token_file"
output_dir=$(dirname "$output")
test -d "$output_dir"
entries=$(mktemp "${TMPDIR:-/tmp}/planwith-redis-entries.XXXXXX")
tokens=$(mktemp "${TMPDIR:-/tmp}/planwith-redis-tokens.XXXXXX")
trap 'rm -f "$entries" "$tokens"' EXIT INT TERM
: > "$entries"

LC_ALL=C sort -u "$token_file" > "$tokens"
while IFS= read -r token; do
  printf '%s\n' "$token" |
    grep -Eq '^[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}$' ||
    { echo "invalid chatbot token format" >&2; exit 1; }

  token_key="chatbot_token:$token"
  mapping_exists=$(redis-cli --no-auth-warning -h "$redis_host" -p "$redis_port" --raw EXISTS "$token_key")
  test "$mapping_exists" -eq 1
  mapping_ttl=$(redis-cli --no-auth-warning -h "$redis_host" -p "$redis_port" --raw TTL "$token_key")
  test "$mapping_ttl" -gt 0

  payload_id=$(redis-cli --no-auth-warning -h "$redis_host" -p "$redis_port" --raw GET "$token_key")
  printf '%s\n' "$payload_id" |
    grep -Eq '^[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}$' ||
    { echo "invalid chatbot payload key format" >&2; exit 1; }

  payload_key="chatbot:$payload_id"
  payload_exists=$(redis-cli --no-auth-warning -h "$redis_host" -p "$redis_port" --raw EXISTS "$payload_key")
  test "$payload_exists" -eq 1
  payload_ttl=$(redis-cli --no-auth-warning -h "$redis_host" -p "$redis_port" --raw TTL "$payload_key")
  test "$payload_ttl" -gt 0

  jq -cn \
    --arg tokenKey "$token_key" \
    --arg payloadKey "$payload_key" \
    --argjson tokenTtl "$mapping_ttl" \
    --argjson payloadTtl "$payload_ttl" \
    '{tokenMappingKey:$tokenKey,payloadKey:$payloadKey,
      observedBeforeCleanup:{
        tokenMapping:{exists:true,ttlSeconds:$tokenTtl},
        payload:{exists:true,ttlSeconds:$payloadTtl}
      }}' >> "$entries"
done < "$tokens"

test -s "$entries"
created_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)
jq -s \
  --arg runId "$run_id" \
  --arg createdAt "$created_at" \
  '{schemaVersion:1,runId:$runId,createdAt:$createdAt,entries:.}' \
  "$entries" > "$output"

jq -e '
  .schemaVersion == 1 and
  (.runId | type == "string" and length > 0) and
  (.entries | length > 0) and
  ([.entries[].tokenMappingKey] | length == (unique | length)) and
  ([.entries[].payloadKey] | length == (unique | length))
' "$output" >/dev/null

rm -f "$entries" "$tokens"
trap - EXIT INT TERM
