#!/usr/bin/env sh
set -eu
url=${1:?actuator base URL required}
output=${2:?output path required}
duration_seconds=${3:-5}
interval_seconds=${4:-5}

case "$duration_seconds:$interval_seconds" in
  *[!0-9:]*|:*|*:) echo "duration and interval must be non-negative integers" >&2; exit 1 ;;
esac
test "$interval_seconds" -gt 0

metrics='http.server.requests process.cpu.usage jvm.memory.used jvm.gc.pause tomcat.threads.busy tomcat.threads.current hikaricp.connections.active hikaricp.connections.pending hikaricp.connections.timeout'
started=$(date +%s)
: > "$output"

while :; do
  now=$(date +%s)
  elapsed=$((now - started))
  test "$elapsed" -le "$duration_seconds" || break

  timestamp=$(date -u +%Y-%m-%dT%H:%M:%SZ)
  if index=$(curl -fsS --max-time 2 "$url/actuator/metrics" 2>/dev/null); then
    values='{}'
    for metric in $metrics; do
      if metric_body=$(curl -fsS --max-time 2 "$url/actuator/metrics/$metric" 2>/dev/null); then
        values=$(printf '%s' "$values" |
          jq -c --arg metric "$metric" --argjson body "$metric_body" '. + {($metric):$body}')
      fi
    done
    jq -cn --arg ts "$timestamp" --argjson elapsed "$elapsed" \
      --argjson index "$index" --argjson metrics "$values" \
      '{timestamp:$ts,elapsedSeconds:$elapsed,status:"AVAILABLE",index:$index,metrics:$metrics}' >> "$output"
  else
    jq -cn --arg ts "$timestamp" --argjson elapsed "$elapsed" \
      '{timestamp:$ts,elapsedSeconds:$elapsed,status:"NOT_AVAILABLE",reason:"Actuator endpoint unavailable"}' >> "$output"
  fi

  remaining=$((duration_seconds - elapsed))
  test "$remaining" -ge "$interval_seconds" || break
  sleep "$interval_seconds"
done
