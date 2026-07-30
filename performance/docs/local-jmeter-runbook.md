# Local JMeter Runbook for PlanWith Chatbot

## Responsibility and safety boundary

The user runs JMeter, SSH tunnels, Actuator collection, Pinpoint inspection,
and every operation that can affect a deployed environment. Codex only
maintains the test definitions and analyzes evidence supplied by the user.

Do not run these scenarios against production data or real OpenAI. Use a
deployed performance environment, dedicated performance users, and a mock
OpenAI endpoint. The S7 failure profiles are mock-only.

Start with S1. Do not progress when any assertion, cleanup, observability, or
environment gate fails.

## Prerequisites on macOS

Confirm Java and JMeter without starting a test:

```bash
java -version
jmeter --version
```

The JMX files target JMeter 5.6.3. If Homebrew is used, installation is
typically:

```bash
brew install jmeter
```

Also confirm:

```bash
curl --version
jq --version
```

## SSH tunnel examples

The user creates and owns the tunnels. Replace placeholders locally; do not
store hostnames, keys, or credentials in the repository.

Application tunnel:

```bash
ssh -N \
  -L 18081:127.0.0.1:8080 \
  <ec2-user>@<planwith-ec2-host>
```

Actuator tunnel:

```bash
ssh -N \
  -L 19091:127.0.0.1:9091 \
  <ec2-user>@<planwith-ec2-host>
```

Keep port 9091 bound to EC2 loopback. Do not expose it through a public
security-group rule.

Read-only tunnel probes:

```bash
curl -fsS http://127.0.0.1:18081/actuator/health
curl -fsS http://127.0.0.1:19091/actuator/metrics
```

Use the application URL actually verified for the environment. The first
example may not be valid if Actuator is on a separate management port.

## Runtime users CSV

Create an ignored file such as:

```text
performance/harness/data/users.local.csv
```

The file has no header because each JMX supplies the column names:

```text
perf-user-01,Bearer <runtime-jwt>,<user-uuid>,<category-uuid>,,
```

Columns:

```text
userAlias,jwt,userId,categoryId,groupId,groupEventId
```

Use a distinct performance-test user for each concurrent thread. Keep the JWT
only in the ignored CSV, restrict file permissions, and remove it when no
longer needed:

```bash
chmod 600 performance/harness/data/users.local.csv
```

Never provide the CSV, JWT, Authorization header, API key, DB password, or
Redis password to Codex. Reports should contain only user aliases and counts.

## Common JMeter properties

| Property | Default | Meaning |
|---|---|---|
| `protocol` | `http` | verified API protocol |
| `host` | `127.0.0.1` | local tunnel or test host |
| `port` | `18081` | local tunnel or test port |
| `apiPath` | `/chatbot/messages` | actual Chatbot endpoint |
| `threads` | `1` | concurrent JMeter threads |
| `iterations` | `1` | requests per thread for non-duration plans |
| `rampUp` | `1` | ramp-up seconds |
| `durationSeconds` | `10` | S5 scheduler duration |
| `usersCsv` | none | ignored runtime CSV |
| `testRunId` | `manual` | unique correlation ID |
| `prompt` | `create one event` | prompt suffix |
| `intention` | empty/null | optional `EVENT`, `TODO`, or `PLAN_RECOMMENDATION` |
| `connectTimeout` | `5000` | JMeter connect timeout in milliseconds |
| `responseTimeout` | `15000` | JMeter response timeout in milliseconds |
| `expectedHttpStatus` | `200` | expected transport status |
| `expectedBodyCode` | `201` | expected service response code |
| `requireToken` | `true` | require extracted Chatbot token |
| `resultJtl` | manual result path | externally selected JTL path |
| `mockDelayMs` | none | run label only; does not configure the remote mock |
| `failureProfile` | none | run label only; does not configure the remote mock |

`mockDelayMs` and `failureProfile` document the intended server-side mock
condition. JMeter cannot alter the mock behind the deployed application. The
user must verify the mock configuration separately before each run.

## Result layout

Use a new runId and directory for every execution:

```text
performance/results/EXP-01/baseline/raw/<runId>/
  jmeter/
    result.jtl
    jmeter.log
  actuator/
    metrics.jsonl
  run-notes.md
```

The `raw/` hierarchy is ignored. Never overwrite or reuse a failed runId.

Record timestamps before and after each command:

```bash
date -u '+UTC=%Y-%m-%dT%H:%M:%SZ'
TZ=Asia/Seoul date '+KST=%Y-%m-%dT%H:%M:%S%z'
```

## Common command shape

Create the result directories yourself before running JMeter.

```bash
jmeter -n \
  -t performance/harness/jmeter/<scenario>.jmx \
  -Jprotocol=http \
  -Jhost=127.0.0.1 \
  -Jport=18081 \
  -Jthreads=1 \
  -Jiterations=1 \
  -JrampUp=1 \
  -JusersCsv=performance/harness/data/users.local.csv \
  -JtestRunId=<unique-run-id> \
  -JconnectTimeout=5000 \
  -JresponseTimeout=15000 \
  -JresultJtl=performance/results/EXP-01/baseline/raw/<run-id>/jmeter/result.jtl \
  -j performance/results/EXP-01/baseline/raw/<run-id>/jmeter/jmeter.log
```

The JMX writer uses XML JTL, preserves response data on errors, and does not
store request or response headers. This avoids writing the Authorization
header while retaining failure bodies needed for diagnosis.

## S1 — Single-request smoke

Purpose: validate request structure, authentication, response code, token
extraction, and safe result writing.

```bash
jmeter -n \
  -t performance/harness/jmeter/chatbot-s1-smoke.jmx \
  -Jhost=127.0.0.1 -Jport=18081 \
  -Jthreads=1 -Jiterations=1 -JrampUp=1 \
  -JusersCsv=performance/harness/data/users.local.csv \
  -JtestRunId=<s1-run-id> \
  -JresultJtl=performance/results/EXP-01/baseline/raw/<s1-run-id>/jmeter/result.jtl \
  -j performance/results/EXP-01/baseline/raw/<s1-run-id>/jmeter/jmeter.log
```

Stop if the sample, HTTP status, body code, or token assertion fails.

## S2 — Mock OpenAI delay sensitivity

Purpose: test `CHAT-PERF-01` using separate mock delay runs. Verify the
server-side mock delay before every command.

Run independent IDs for:

```text
0ms, 300ms, 1000ms, 3000ms
```

Command shape:

```bash
jmeter -n \
  -t performance/harness/jmeter/chatbot-s2-openai-delay.jmx \
  -Jhost=127.0.0.1 -Jport=18081 \
  -Jthreads=1 -Jiterations=1 -JrampUp=1 \
  -JmockDelayMs=<0|300|1000|3000> \
  -JusersCsv=performance/harness/data/users.local.csv \
  -JtestRunId=<s2-delay-run-id> \
  -JresultJtl=performance/results/EXP-01/baseline/raw/<run-id>/jmeter/result.jtl \
  -j performance/results/EXP-01/baseline/raw/<run-id>/jmeter/jmeter.log
```

Do not mix delay profiles in one JTL. Stop if the mock configuration cannot be
proven or any request reaches real OpenAI.

## S3 — Concurrency steps

Purpose: locate p95/p99, Tomcat, Hikari, and external-wait inflection points.

Run separate executions in this order:

```text
1, 5, 10, 20, 30 users
```

```bash
jmeter -n \
  -t performance/harness/jmeter/chatbot-s3-concurrency.jmx \
  -Jhost=127.0.0.1 -Jport=18081 \
  -Jthreads=<1|5|10|20|30> -Jiterations=1 -JrampUp=5 \
  -JusersCsv=performance/harness/data/users.local.csv \
  -JtestRunId=<s3-run-id> \
  -JresultJtl=performance/results/EXP-01/baseline/raw/<run-id>/jmeter/result.jtl \
  -j performance/results/EXP-01/baseline/raw/<run-id>/jmeter/jmeter.log
```

Do not start the next step until the preceding run is reviewed and cleaned up.

## S4 — Short synchronized burst

Purpose: observe short-arrival pressure on request threads and external I/O.

The file defaults to one user. Twenty users is a later manual candidate only:

```bash
jmeter -n \
  -t performance/harness/jmeter/chatbot-s4-burst.jmx \
  -Jhost=127.0.0.1 -Jport=18081 \
  -Jthreads=<approved-users> -Jiterations=1 -JrampUp=1 \
  -JsyncUsers=<approved-users> -JsyncTimeoutMs=10000 \
  -JusersCsv=performance/harness/data/users.local.csv \
  -JtestRunId=<s4-run-id> \
  -JresultJtl=performance/results/EXP-01/baseline/raw/<run-id>/jmeter/result.jtl \
  -j performance/results/EXP-01/baseline/raw/<run-id>/jmeter/jmeter.log
```

Stop on unexpected user-data changes, container restart, observability loss, or
the first unsafe error trend.

## S5 — Sustained low load

Purpose: observe stable throughput, memory, GC, and pool trends. The default is
one thread for 10 seconds.

```bash
jmeter -n \
  -t performance/harness/jmeter/chatbot-s5-sustained.jmx \
  -Jhost=127.0.0.1 -Jport=18081 \
  -Jthreads=1 -JrampUp=1 -JdurationSeconds=10 \
  -JrecycleUsers=true \
  -JusersCsv=performance/harness/data/users.local.csv \
  -JtestRunId=<s5-run-id> \
  -JresultJtl=performance/results/EXP-01/baseline/raw/<run-id>/jmeter/result.jtl \
  -j performance/results/EXP-01/baseline/raw/<run-id>/jmeter/jmeter.log
```

`recycleUsers=true` reuses the same dedicated user; it does not create users.
Extend duration only after a short run is safe and cleanup capacity is known.

## S6 — Intention comparison

Purpose: compare the same prompt with no intention and with `EVENT`.

Run A:

```bash
jmeter -n \
  -t performance/harness/jmeter/chatbot-s6-intention-comparison.jmx \
  -Jhost=127.0.0.1 -Jport=18081 \
  -Jthreads=1 -Jiterations=1 -Jintention= \
  -Jprompt='create one event' \
  -JusersCsv=performance/harness/data/users.local.csv \
  -JtestRunId=<s6-null-run-id> \
  -JresultJtl=performance/results/EXP-01/baseline/raw/<run-id>/jmeter/result.jtl \
  -j performance/results/EXP-01/baseline/raw/<run-id>/jmeter/jmeter.log
```

Run B uses the same settings except:

```bash
-Jintention=EVENT -JtestRunId=<s6-event-run-id>
```

Compare Pinpoint external HTTP count first; latency alone cannot prove the call
was or was not skipped.

## S7 — Mock-only failure resilience

Purpose: observe timeout, 4xx, 5xx, and malformed response behavior. This must
never target real OpenAI.

Configure one server-side mock profile at a time:

```text
timeout, http-4xx, http-5xx, malformed-json
```

The repository mock accepts `MOCK_PROFILE` with `success`, `timeout`,
`http-4xx`, `http-5xx`, or `malformed-json`. `MOCK_DELAY_MS` controls normal
delay and `MOCK_TIMEOUT_DELAY_MS` controls the timeout profile. The user
configures/restarts that mock environment; JMeter does not do so.

Because the current error contract is not confirmed, provide expected values
explicitly after a one-request observation:

```bash
jmeter -n \
  -t performance/harness/jmeter/chatbot-s7-failure-resilience.jmx \
  -Jhost=127.0.0.1 -Jport=18081 \
  -Jthreads=1 -Jiterations=1 -JrampUp=1 \
  -JfailureProfile=<profile> \
  -JexpectedHttpStatus=<observed-contract-status> \
  -JexpectedBodyCode=<expected-service-code-or-empty> \
  -JrequireToken=false \
  -JusersCsv=performance/harness/data/users.local.csv \
  -JtestRunId=<s7-run-id> \
  -JresponseTimeout=15000 \
  -JresultJtl=performance/results/EXP-01/baseline/raw/<run-id>/jmeter/result.jtl \
  -j performance/results/EXP-01/baseline/raw/<run-id>/jmeter/jmeter.log
```

This property labels the profile; it does not configure the mock. Stop after
one request if the response is unbounded, the mock cannot be proven, or a
request might reach real OpenAI.

## S8 — Cache comparison

`NOT_APPLICABLE`. The current code has no response cache. Redis holds
per-request staged state under random keys; repeating a prompt is not a cache
hit. Do not interpret same-prompt latency changes as cache behavior.

## S9 — Authentication DB and Hikari observation

Purpose: observe the User SELECT performed by JWT authentication and associated
Hikari usage without directly querying or mutating the DB from JMeter.

```bash
jmeter -n \
  -t performance/harness/jmeter/chatbot-s9-db-pool.jmx \
  -Jhost=127.0.0.1 -Jport=18081 \
  -Jthreads=<1|5|10|20> -Jiterations=1 -JrampUp=5 \
  -JusersCsv=performance/harness/data/users.local.csv \
  -JtestRunId=<s9-run-id> \
  -JresultJtl=performance/results/EXP-01/baseline/raw/<run-id>/jmeter/result.jtl \
  -j performance/results/EXP-01/baseline/raw/<run-id>/jmeter/jmeter.log
```

JMeter alone cannot prove Hikari contention. Actuator and Pinpoint evidence are
required.

## S10 — Draft baseline candidate

This file is not an approved measured baseline. Final threads, iterations,
ramp-up, mock delay, and duration must be chosen only after reviewing S1–S9.

Safe structural check:

```bash
jmeter -n \
  -t performance/harness/jmeter/chatbot-s10-baseline-candidate.jmx \
  -Jhost=127.0.0.1 -Jport=18081 \
  -Jthreads=1 -Jiterations=1 -JrampUp=1 \
  -JusersCsv=performance/harness/data/users.local.csv \
  -JtestRunId=<s10-draft-run-id> \
  -JresultJtl=performance/results/EXP-01/baseline/raw/<run-id>/jmeter/result.jtl \
  -j performance/results/EXP-01/baseline/raw/<run-id>/jmeter/jmeter.log
```

Do not call this a baseline until environment, workload, mock, cleanup, and
observability conditions are frozen.

## Actuator evidence

The user may collect the loopback-bound endpoint through a tunnel:

```bash
performance/harness/bin/collect-actuator.sh \
  http://127.0.0.1:19091 \
  performance/results/EXP-01/baseline/raw/<run-id>/actuator/metrics.jsonl \
  <duration-seconds> \
  5
```

Align collection start/end with JMeter timestamps. Record unavailable metrics
as `NOT_AVAILABLE`; never estimate them.

## Immediate stop conditions

Stop the current run and do not increase load when any of these occurs:

- a request may be reaching real OpenAI;
- the environment or commit differs from the recorded test target;
- error rate exceeds the predeclared safety threshold;
- application container restarts or becomes unhealthy;
- Actuator becomes unavailable;
- DB or Redis connectivity fails;
- a non-test user or unexpected Event/Todo is changed;
- exact-key cleanup cannot be completed;
- JTL or log output cannot be written;
- JWT, Authorization, API key, or password appears in an artifact;
- response assertions fail unexpectedly.

Do not delete evidence from a failed run. Preserve it under its unique ignored
run directory.

## Pinpoint capture window

Record exact UTC and KST start/end timestamps and inspect:

- application `PLANWITH-API-PERF`;
- agent `planwith-api-perf-01`;
- Transaction List for the exact run interval;
- representative and slowest Call Trees;
- two external HTTP spans;
- authentication User SELECT;
- Redis GET/SET spans;
- exception or timeout spans.

Provide screenshots or a text summary, but not secrets or raw request headers.

## Evidence to return to Codex

Use `performance/docs/performance-result-input-template.md` and provide:

- sanitized run metadata and exact commit/image/profile;
- JMeter aggregate metrics and error-body categories;
- Actuator summary for the same interval;
- Pinpoint screenshots or text values for the same interval;
- whether warm-up was separate;
- container restart count before/after;
- mock mode and verified delay/profile;
- DB/Redis invariant and cleanup status.

Do not send users CSV, JWT, API keys, passwords, Authorization headers, raw
environment dumps, or unredacted application logs.
