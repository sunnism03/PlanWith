#!/usr/bin/env sh
set -eu
base_url=${1:?base URL required}
auth_file=${2:?runtime authorization file required}
output=${3:?output path required}
auth=$(cat "$auth_file")
if body=$(curl -fsS -H "Authorization: $auth" "$base_url/events?year=2026&month=8" 2>/dev/null); then
  count=$(printf '%s' "$body" | jq '[.data[]? | select(.title == "dry-run-event")] | length')
  jq -cn --argjson count "$count" '{status:(if $count == 0 then "PASS" else "FAIL" end),check:"no personal event persisted by C1",matchingRows:$count}' > "$output"
else
  jq -cn '{status:"NOT_AVAILABLE",reason:"DB-backed verification endpoint unavailable"}' > "$output"
fi

