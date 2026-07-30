#!/usr/bin/env sh
set -eu

root_dir=$(CDPATH= cd -- "$(dirname "$0")/../../.." && pwd)
runtime_root="$root_dir/performance/harness/runtime"
run_id="dry-$(date -u +%Y%m%dT%H%M%SZ)-$$"
run_dir="$runtime_root/$run_id"
redis_token_file="$run_dir/redis-tokens.runtime"
redis_key_ledger="$run_dir/redis-keys.generated.json"
redis_port=16379
mock_port=18080
app_port=18081
started_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)

mkdir -p "$run_dir"
: > "$redis_token_file"

redis_pid=
mock_pid=
app_pid=
collector_pid=
cleanup_processes() {
  test -z "$collector_pid" || kill "$collector_pid" 2>/dev/null || true
  test -z "$app_pid" || kill "$app_pid" 2>/dev/null || true
  test -z "$mock_pid" || kill "$mock_pid" 2>/dev/null || true
  test -z "$redis_pid" || kill "$redis_pid" 2>/dev/null || true
  test -z "$collector_pid" || wait "$collector_pid" 2>/dev/null || true
  test -z "$app_pid" || wait "$app_pid" 2>/dev/null || true
  test -z "$mock_pid" || wait "$mock_pid" 2>/dev/null || true
  test -z "$redis_pid" || wait "$redis_pid" 2>/dev/null || true
  app_pid=
  collector_pid=
  mock_pid=
  redis_pid=
}
trap cleanup_processes EXIT INT TERM

test -f "$root_dir/build/libs/ToDoWithMe-0.0.1-SNAPSHOT.jar"

redis-server --port "$redis_port" --save '' --appendonly no --dir "$run_dir" \
  >"$run_dir/redis.raw.log" 2>&1 &
redis_pid=$!

MOCK_PORT="$mock_port" MOCK_DELAY_MS=25 \
  python3 "$root_dir/performance/harness/mock/openai_mock.py" \
  >"$run_dir/mock.raw.log" 2>&1 &
mock_pid=$!

jwt_secret=$(LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c 64)
openai_key=$(LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c 32)
db_path="$run_dir/planwith"

SPRING_PROFILES_ACTIVE=dev-perf \
SPRING_DATASOURCE_URL="jdbc:h2:file:$db_path;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1" \
SPRING_DATASOURCE_USERNAME=sa \
SPRING_DATASOURCE_PASSWORD= \
SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.h2.Driver \
SPRING_DATA_REDIS_HOST=127.0.0.1 \
SPRING_DATA_REDIS_PORT="$redis_port" \
SPRING_JPA_HIBERNATE_DDL_AUTO=create-drop \
SPRING_JPA_SHOW_SQL=false \
SPRING_JPA_DATABASE_PLATFORM=org.hibernate.dialect.H2Dialect \
JWT_SECRET="$jwt_secret" \
OPENAI_API_KEY="$openai_key" \
OPENAI_API_URL="http://127.0.0.1:$mock_port/v1/chat/completions" \
SERVER_PORT="$app_port" \
java -jar "$root_dir/build/libs/ToDoWithMe-0.0.1-SNAPSHOT.jar" \
  >"$run_dir/application.raw.log" 2>&1 &
app_pid=$!

ready=false
for _ in $(seq 1 60); do
  if curl -fsS "http://127.0.0.1:$app_port/swagger-ui/index.html" >/dev/null 2>&1; then
    ready=true
    break
  fi
  if ! kill -0 "$app_pid" 2>/dev/null; then
    break
  fi
  sleep 1
done
test "$ready" = true

"$root_dir/performance/harness/bin/bootstrap-user.sh" \
  "http://127.0.0.1:$app_port" "$run_id" "$run_dir"

"$root_dir/performance/harness/bin/verify-user-data-postcondition.sh" \
  "http://127.0.0.1:$app_port" "$run_dir/users.csv" 2026 8 before \
  "$run_dir/db-precondition.json"

"$root_dir/performance/harness/bin/collect-actuator.sh" \
  "http://127.0.0.1:9091" "$run_dir/actuator.jsonl" 15 5 &
collector_pid=$!

jmeter -n \
  -t "$root_dir/performance/harness/jmeter/chatbot-c1-dry-run.jmx" \
  -Jhost=127.0.0.1 -Jport="$app_port" \
  -Jthreads=1 -JrampUp=1 -JtestRunId="$run_id" \
  -JusersCsv="$run_dir/users.csv" \
  -JredisTokenFile="$redis_token_file" \
  -JconnectTimeout=5000 -JresponseTimeout=15000 \
  -l "$run_dir/jmeter.raw.jtl" -j "$run_dir/jmeter.raw.log"

test "$(awk -F, 'NR > 1 && $8 != "true" {n++} END {print n+0}' "$run_dir/jmeter.raw.jtl")" -eq 0
wait "$collector_pid"
collector_pid=
jq -s -e 'length >= 2 and all(.[]; .status == "AVAILABLE")' "$run_dir/actuator.jsonl" >/dev/null

"$root_dir/performance/harness/bin/verify-user-data-postcondition.sh" \
  "http://127.0.0.1:$app_port" "$run_dir/users.csv" 2026 8 after \
  "$run_dir/db-postcondition.json"
"$root_dir/performance/harness/bin/record-redis-keys.sh" \
  127.0.0.1 "$redis_port" "$run_id" "$redis_token_file" "$redis_key_ledger"
"$root_dir/performance/harness/bin/verify-redis-invariant.sh" \
  127.0.0.1 "$redis_port" "$redis_key_ledger" "$run_dir/redis-precondition.json"
"$root_dir/performance/harness/bin/cleanup.sh" \
  127.0.0.1 "$redis_port" "$redis_key_ledger" "$run_dir/cleanup.json"

jq -e '.status == "PASS"' "$run_dir/db-precondition.json" >/dev/null
jq -e '.status == "PASS"' "$run_dir/db-postcondition.json" >/dev/null
jq -e '.status == "PASS"' "$run_dir/redis-precondition.json" >/dev/null
jq -e '.status == "PASS"' "$run_dir/cleanup.json" >/dev/null

ended_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)
"$root_dir/performance/harness/bin/collect-run-metadata.sh" \
  "$run_dir/run-metadata.json" "$run_id" "$started_at" "$ended_at"

cat > "$run_dir/summary.md" <<EOF
# Dry Run Summary

- runId: $run_id
- scenarioId: C1
- users: 1
- OpenAI mode: MOCK
- JMeter assertions: PASSED
- JSON Extractor assertion: PASSED
- Actuator collection file: CREATED
- DB precondition and postcondition: PASSED
- Redis exact-key precondition: PASSED
- Redis exact-key cleanup and postcondition: PASSED
EOF

rm -f "$run_dir/users.csv" "$run_dir/authorization.runtime" "$redis_token_file"
cleanup_processes
"$root_dir/performance/harness/bin/create-artifact-manifest.sh" "$run_dir" "$run_id"
"$root_dir/performance/harness/bin/validate-run-artifacts.sh" "$run_dir"
"$root_dir/performance/harness/bin/scan-secrets.sh" "$run_dir"

printf '%s\n' "$run_dir"
