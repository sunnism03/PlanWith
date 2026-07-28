#!/usr/bin/env sh
set -eu
url=${1:?actuator base URL required}
output=${2:?output path required}
timestamp=$(date -u +%Y-%m-%dT%H:%M:%SZ)
if body=$(curl -fsS --max-time 2 "$url/actuator/metrics" 2>/dev/null); then
  jq -cn --arg ts "$timestamp" --argjson body "$body" '{timestamp:$ts,status:"AVAILABLE",body:$body}' > "$output"
else
  jq -cn --arg ts "$timestamp" '{timestamp:$ts,status:"NOT_AVAILABLE",reason:"Actuator endpoint unavailable"}' > "$output"
fi

