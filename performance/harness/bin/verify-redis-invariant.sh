#!/usr/bin/env sh
set -eu
port=${1:?redis port required}
output=${2:?output path required}
token_count=$(redis-cli -p "$port" --scan --pattern 'chatbot_token:*' | wc -l | tr -d ' ')
payload_count=$(redis-cli -p "$port" --scan --pattern 'chatbot:*' | wc -l | tr -d ' ')
jq -cn --argjson token "$token_count" --argjson payload "$payload_count" \
  '{status:(if $token >= 1 and $payload >= 1 then "PASS" else "FAIL" end),tokenKeys:$token,payloadKeys:$payload}' > "$output"

