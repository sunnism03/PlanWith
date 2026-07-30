#!/usr/bin/env sh
set -eu

redis_host=${1:?redis host required}
redis_port=${2:?redis port required}
ledger=${3:?exact Redis key ledger required}
phase=${4:?verification phase required: before or after}
output=${5:?verification report output required}

case "$phase" in
  before|after) ;;
  *) echo "phase must be before or after" >&2; exit 1 ;;
esac

jq -e '
  .schemaVersion == 1 and
  (.runId | type == "string" and length > 0) and
  (.entries | length > 0) and
  all(.entries[];
    (.tokenMappingKey | test("^chatbot_token:[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}$")) and
    (.payloadKey | test("^chatbot:[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}$"))
  ) and
  ([.entries[].tokenMappingKey] | length == (unique | length)) and
  ([.entries[].payloadKey] | length == (unique | length))
' "$ledger" >/dev/null

checked=0
forbidden=0
jq -r '.entries[] | [.tokenMappingKey,.payloadKey] | @tsv' "$ledger" |
while IFS="$(printf '\t')" read -r token_key payload_key; do
  token_exists=$(redis-cli --no-auth-warning -h "$redis_host" -p "$redis_port" --raw EXISTS "$token_key")
  payload_exists=$(redis-cli --no-auth-warning -h "$redis_host" -p "$redis_port" --raw EXISTS "$payload_key")
  token_ttl=$(redis-cli --no-auth-warning -h "$redis_host" -p "$redis_port" --raw TTL "$token_key")
  payload_ttl=$(redis-cli --no-auth-warning -h "$redis_host" -p "$redis_port" --raw TTL "$payload_key")

  case "$phase" in
    before)
      test "$token_exists" -eq 1
      test "$payload_exists" -eq 1
      test "$token_ttl" -gt 0
      test "$payload_ttl" -gt 0
      ;;
    after)
      test "$token_exists" -eq 0
      test "$payload_exists" -eq 0
      test "$token_ttl" -eq -2
      test "$payload_ttl" -eq -2
      ;;
  esac
  checked=$((checked + 2))
done

entry_count=$(jq '.entries | length' "$ledger")
verified_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)
jq -cn \
  --arg phase "$phase" \
  --arg verifiedAt "$verified_at" \
  --argjson entries "$entry_count" \
  '{status:"PASS",phase:$phase,entryCount:$entries,
    exactKeyCount:($entries * 2),verifiedAt:$verifiedAt}' > "$output"
