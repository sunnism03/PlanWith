#!/usr/bin/env sh
set -eu

container=${1:-${PLANWITH_CONTAINER_NAME:-planwith-api}}
collector_host=${2:-${PINPOINT_COLLECTOR_HOST:-}}

case "$container" in
  ''|*[!A-Za-z0-9_.-]*) echo "invalid container name" >&2; exit 1 ;;
esac
case "$collector_host" in
  *[!A-Za-z0-9_.:-]*) echo "invalid collector host" >&2; exit 1 ;;
esac

if ! docker inspect "$container" >/dev/null 2>&1; then
  echo "container_name=$container"
  echo "container_status=NOT_FOUND"
  exit 1
fi

image_id=$(docker inspect --format '{{.Image}}' "$container")
image_tag=$(docker inspect --format '{{.Config.Image}}' "$container")
revision=$(docker image inspect --format '{{index .Config.Labels "org.opencontainers.image.revision"}}' "$image_id" 2>/dev/null || true)
env_lines=$(docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "$container")
profile=$(printf '%s\n' "$env_lines" | sed -n 's/^SPRING_PROFILES_ACTIVE=//p' | tail -n 1)
openai_url=$(printf '%s\n' "$env_lines" | sed -n 's/^OPENAI_API_URL=//p' | tail -n 1)

test -n "$profile" || profile=UNSET
test -n "$revision" || revision=UNSET

openai_host=UNSET
openai_port=UNSET
if test -n "$openai_url"; then
  scheme=${openai_url%%://*}
  authority=${openai_url#*://}
  authority=${authority%%/*}
  authority=${authority%%\?*}
  authority=${authority##*@}
  case "$authority" in
    \[*\]:*)
      openai_host=${authority%%]*}
      openai_host="${openai_host#\[}"
      openai_port=${authority##*:}
      ;;
    *:*)
      openai_host=${authority%:*}
      openai_port=${authority##*:}
      ;;
    *)
      openai_host=$authority
      case "$scheme" in
        https) openai_port=443 ;;
        http) openai_port=80 ;;
        *) openai_port=UNKNOWN ;;
      esac
      ;;
  esac
fi

echo "container_name=$container"
echo "container_status=$(docker inspect --format '{{.State.Status}}' "$container")"
echo "image_id=$image_id"
echo "image_tag=$image_tag"
echo "oci_revision=$revision"
echo "spring_profiles_active=$profile"
echo "openai_api_host=$openai_host"
echo "openai_api_port=$openai_port"

health_code=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 3 \
  http://127.0.0.1:9091/actuator/health 2>/dev/null || true)
test -n "$health_code" || health_code=UNAVAILABLE
echo "actuator_health_http=$health_code"

for metric in \
  http.server.requests process.cpu.usage jvm.memory.used jvm.gc.pause \
  tomcat.threads.busy tomcat.threads.current hikaricp.connections.active \
  hikaricp.connections.pending hikaricp.connections.timeout
do
  if curl -fsS -o /dev/null --max-time 3 \
    "http://127.0.0.1:9091/actuator/metrics/$metric" 2>/dev/null; then
    status=PASS
  else
    status=FAIL
  fi
  safe_name=$(printf '%s' "$metric" | tr '.-' '__')
  echo "actuator_metric_${safe_name}=$status"
done

runtime_command=$(docker inspect --format '{{json .Config.Entrypoint}} {{json .Config.Cmd}}' "$container")
java_options=$(printf '%s\n' "$env_lines" |
  sed -n -e 's/^JAVA_TOOL_OPTIONS=//p' -e 's/^_JAVA_OPTIONS=//p')
if printf '%s\n%s\n' "$runtime_command" "$java_options" |
  grep -Eiq -- '(-javaagent[^ ]*pinpoint|pinpoint-bootstrap|pinpoint-agent)'; then
  echo "pinpoint_jvm_options=PRESENT"
else
  echo "pinpoint_jvm_options=NOT_FOUND"
fi

if logs=$(docker logs --tail 2000 "$container" 2>&1); then
  if printf '%s\n' "$logs" |
    grep -Eiq -- '(pinpoint.*(load|start|connect).*(fail|error|exception)|(fail|error|exception).*pinpoint)'; then
    echo "pinpoint_agent_load_error=PRESENT"
  else
    echo "pinpoint_agent_load_error=NONE_FOUND"
  fi
else
  echo "pinpoint_agent_load_error=UNKNOWN"
fi

for port in 9991 9992 9993; do
  if test -z "$collector_host"; then
    result=UNKNOWN
  else
    set +e
    docker exec "$container" sh -c '
      if command -v nc >/dev/null 2>&1; then
        nc -z -w 3 "$1" "$2"
      elif command -v bash >/dev/null 2>&1 && command -v timeout >/dev/null 2>&1; then
        timeout 4 bash -c '"'"'exec 3<>/dev/tcp/"$1"/"$2"'"'"' _ "$1" "$2"
      else
        exit 2
      fi
    ' sh "$collector_host" "$port" >/dev/null 2>&1
    rc=$?
    set -e
    case "$rc" in
      0) result=PASS ;;
      2) result=UNKNOWN ;;
      *) result=FAIL ;;
    esac
  fi
  echo "pinpoint_collector_${port}=$result"
done
