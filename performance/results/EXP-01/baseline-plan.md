# EXP-01 — Baseline Run Plan

## Status

- Readiness verdict: `READY_FOR_PHASE_8B`
- Phase: `8A`
- Measured baseline executed: **NO**
- Baseline commit frozen: **YES — `97432dc98f2ae8632151e4f301acb1831e79cf6d`**
- Input experiment: `EXP-01`
- hypothesisId: `CHAT-PERF-01`, `CHAT-REL-01`
- scenarioId: `C1`

The observability blocker is resolved, the execution-affecting harness changes are fixed by `HARNESS_COMMIT`, and the Phase 8A source snapshot is frozen as `BASELINE_COMMIT`. No measured command was executed in Phase 8A; execution is allowed only after the Phase 8B preflight revalidates this snapshot.

## Input files

- `AGENTS.md`
- `docs/codex/planwith-performance-workflow.md`
- `performance/state.md`
- `performance/plans/selected-experiments.md`
- `performance/plans/test-harness-report.md`
- `performance/plans/jmeter-test-design.md`
- `performance/plans/observability-checklist.md`
- `performance/harness/harness-manifest.json`
- `performance/harness/**`
- `build.gradle`
- `src/main/resources/application.yaml`
- `src/main/resources/application-dev.yaml`
- `src/main/resources/application-dev-perf.yaml`

## Evidence classification

### FACT

- `HARNESS_COMMIT` is `a01ac11c7bc194e80d96eec96e56a284e3cbc8ef`; it contains the user dependency, test-only profile, isolated multi-user fixture and validated harness content.
- `BASELINE_COMMIT` is `97432dc98f2ae8632151e4f301acb1831e79cf6d`; it was frozen before any measured baseline.
- The final dry run `dry-20260729T022205Z-64356` passed with one user, mock OpenAI, isolated H2, isolated Redis, HTTP/body assertions, JSON extraction, DB/Redis invariants, cleanup, artifact validation and secret scan.
- the user-added `build.gradle` dependency supplies `spring-boot-starter-actuator` without a separate version override.
- `dev-perf` imports the existing dev configuration and binds the management server to `127.0.0.1:9091`.
- exposed management endpoints are limited to `health`, `info` and `metrics`.
- the test-only profile enables the Tomcat MBean registry required for Tomcat thread metrics.
- `performance/harness/bin/collect-actuator.sh` writes JSONL samples at 5-second intervals and collects HTTP, process, JVM, GC, Tomcat and Hikari metric responses.
- a `dev-perf` runtime check returned HTTP 200 for `/actuator/health`, `/actuator/metrics` and every required metric endpoint.
- after DB connection and application API requests, all six required metrics and ten Hikari metrics were present.
- collector checks produced available samples at elapsed seconds 0 and 5.
- the management listener was exactly `127.0.0.1:9091`; Docker and Compose exposure were not added.
- `chatbot-c1-dry-run.jmx` uses `POST /chatbot/messages`, per-user CSV input, mock-compatible application routing, assertions and JSON extraction.
- the JMX is a one-shot workload: it accepts `threads` and `rampUp`, uses one loop, and does not use a duration scheduler.
- no measured baseline result exists.

### HYPOTHESIS

- serial external LLM calls increase end-to-end latency and missing timeout configuration can leave request threads occupied during delayed or failed mock responses.
- a comparable measured run can evaluate this only after required observability and artifact gates are executable.

### UNKNOWN

- Pinpoint trace values and span counts; no Pinpoint evidence is available.
- Docker image ID and digest because this blocked plan has not built or selected a measured-baseline image.
- live MySQL constraints and behavior; the validated local test path uses isolated H2.

## Readiness gates

| Gate | Status | Evidence |
|---|---|---|
| Selected experiment | PASS | state and selected-experiments contain only `EXP-01` |
| Harness commit | PASS | `a01ac11c7bc194e80d96eec96e56a284e3cbc8ef` |
| Harness dry run | PASS | `dry-20260729T022205Z-64356`, dev-perf and 5-second Actuator collection |
| Test environment isolation | PASS | runtime-local H2 and dedicated local Redis port |
| Real OpenAI exclusion | PASS | local compatible mock; real API is forbidden |
| JMeter endpoint and DTO | PASS | `POST /chatbot/messages`, current request body and assertions |
| Per-user JWT input | PASS | runtime-only `users.csv` and authorization file |
| DB invariant | PASS | C1 must persist no personal Event/Todo row |
| Redis invariant | PASS | staged payload and token mapping must exist after C1 |
| Cleanup | PASS | `chatbot:*` and `chatbot_token:*` are removed |
| Secret handling | PASS | runtime paths ignored and secret scan passed |
| Actuator dependency and endpoint | PASS | `dev-perf`, localhost port 9091, health/metrics HTTP 200 |
| Required Actuator metrics | PASS | HTTP, CPU, JVM, GC, Tomcat and Hikari runtime probes succeeded |
| Actuator 5-second collection | PASS | available JSONL samples at elapsed seconds 0 and 5 |
| Pinpoint | UNKNOWN | no access or supplied trace evidence |
| Tracked working tree at freeze | PASS | Phase 8A content snapshot was committed before recording self-referential metadata |
| Baseline source manifest | PASS | execution-affecting hashes match `BASELINE_COMMIT`; metadata records the frozen SHA |

## Planned test environment

- Environment class: isolated local test environment only
- Application: local Spring Boot jar built from the eventual `BASELINE_COMMIT`
- Active profile: `dev-perf`, with test-only environment-variable overrides
- DB: isolated H2 file under the run directory
- Redis: isolated local Redis instance on a dedicated port
- OpenAI mode: `MOCK`
- Mock server: `performance/harness/mock/openai_mock.py`
- Real OpenAI: forbidden
- Test users: generated per run; JWT and credentials remain in ignored runtime files
- Production data and endpoints: forbidden

No environment value containing a password, token, JWT or API key may be written to tracked output or raw result metadata.

## Planned JMeter scenario

- JMX: `performance/harness/jmeter/chatbot-c1-dry-run.jmx`
- scenarioId: `C1`
- method and endpoint: `POST /chatbot/messages`
- workload form: one request per thread
- `threads`: `25`
- `rampUp`: `5`
- `duration`: `0` for the current one-shot JMX
- measured repetitions: `10`
- primary sample population: `250` successful samples per mock latency profile
- `connectTimeout`: `5000`
- `responseTimeout`: `15000` until the experiment plan intentionally varies the failure profile
- `testRunId`: new value for every run
- `usersCsv`: ignored runtime path with a distinct JWT per row
- GUI listeners: forbidden
- warm-up: separate runId and separate result directory
- measured run: new runId and empty result directory

Warm-up uses 5 users once and is excluded from statistics. Each measured repetition uses 25 distinct CSV rows and a new result filename. The primary normal and delayed profiles each aggregate exactly 250 samples. A timeout diagnostic uses 3 distinct users once and is excluded from p95/p99 because it evaluates bounded failure behavior rather than steady-state latency.

## Planned mock profiles

1. normal fixed response with `MOCK_DELAY_MS=25`: 10 × 25 samples;
2. delayed fixed response with `MOCK_DELAY_MS=500`: 10 × 25 samples;
3. timeout diagnostic with `MOCK_DELAY_MS=16000`: 1 × 3 samples, excluded from percentile comparison.

Each profile must use the same JMX, fixture shape and invariant rules for baseline and after. `useRealOpenAI` must be `false`.

## Observability verification

1. Actuator dependency: **PASS**
2. test-only management endpoint on `127.0.0.1:9091`: **PASS**
3. exposure limited to `health`, `info`, `metrics`: **PASS**
4. `/actuator/health` and `/actuator/metrics`: **HTTP 200**
5. `http.server.requests`, `process.cpu.usage`, `jvm.memory.used`, `jvm.gc.pause`, `tomcat.threads.busy`, `tomcat.threads.current`: **AVAILABLE**
6. Hikari metrics after DB connection: **AVAILABLE**
7. application API request represented in `http.server.requests`: **PASS**
8. 5-second collector JSONL: **PASS**
9. management listener limited to loopback: **PASS**
10. changed-file secret scan: **PASS**

The collector, profile and isolated-user fixture are included in `HARNESS_COMMIT`. Their hashes must match the final source manifest before source freeze.

## Planned JMeter command

After Phase 8A records `BASELINE_COMMIT`, Phase 8B may use the following command shape for each measured repetition:

```bash
jmeter -n \
  -t performance/harness/jmeter/chatbot-c1-dry-run.jmx \
  -Jhost=127.0.0.1 \
  -Jport=18081 \
  -Jthreads=25 \
  -JrampUp=5 \
  -JtestRunId=<runId> \
  -JusersCsv=<ignored-runtime-users.csv> \
  -JconnectTimeout=5000 \
  -JresponseTimeout=15000 \
  -l <new-result-directory>/jmeter-<profile>-<repetition>.jtl \
  -j <new-result-directory>/jmeter-<profile>-<repetition>.log
```

The command is a plan only and is not executed in Phase 8A.

Pinpoint remains optional evidence. If access is unavailable, record `UNKNOWN` and do not estimate values.

## Invariants

### Before

- test user and category fixture belong only to the current `testRunId`;
- no previous `chatbot:*` or `chatbot_token:*` keys exist for the test run;
- no personal Event/Todo row for the C1 marker exists;
- result directory does not exist or is empty.

### After

- all JMeter assertions pass;
- C1 creates no personal Event/Todo row;
- one staged chatbot payload and one token mapping are observed before cleanup;
- no real OpenAI endpoint was contacted;
- every raw artifact is listed with SHA-256 and size in `artifact-manifest.json`;
- secret scan and artifact validation exit with code 0.

## Cleanup

1. remove only testRunId-scoped Redis keys and validated Chatbot key patterns;
2. stop the isolated application, mock and Redis processes;
3. preserve failed raw artifacts under `failed/<runId>`;
4. remove runtime JWT and credential files;
5. verify remaining Chatbot keys are zero;
6. do not run another attempt until cleanup is `COMPLETED`.

No `DROP`, `TRUNCATE`, broad `DELETE`, destructive Git reset or production cleanup is allowed.

## Abort conditions

- Actuator endpoint or required time-series collector becomes unavailable;
- `useRealOpenAI=true`;
- environment cannot be proven non-production;
- source manifest differs from the frozen commit;
- result path is not new and empty;
- invariant, cleanup, secret scan or artifact validation fails;
- tracked working tree is not clean at the freeze/run boundary;
- JMX, mock, fixture or invariant behavior changes after source freeze;
- harness commit or scenario hash differs from the source manifest.

## Planned execution sequence

During Phase 8B, after its preflight reconfirms the frozen source and empty/new result path:

1. validate the source manifest and frozen commit;
2. create a new runId and empty result directory;
3. run pre-invariants;
4. start 5-second Actuator collection;
5. execute a separate warm-up;
6. execute the measured JMeter CLI run with mock OpenAI;
7. stop collection;
8. record Pinpoint evidence only when supplied;
9. run post-invariants and cleanup;
10. create and validate the artifact manifest;
11. write summary and update state.

The command above is a documented Phase 8B command shape only. It was not executed during Phase 8A, and Phase 8B must stop if the frozen source, harness, environment, or result-path gates fail.

## Expected result files

- `performance/results/EXP-01/baseline/run-metadata.json`
- `performance/results/EXP-01/baseline/artifact-manifest.json`
- `performance/results/EXP-01/baseline/summary.md`
- ignored raw JTL and JMeter report
- ignored Actuator JSONL time series
- ignored application and mock logs
- DB and Redis invariant outputs referenced by the artifact manifest

None of these measured result files is created in Phase 8A.

## Resume action

Resume at Phase 8B. Revalidate `BASELINE_COMMIT`, the source manifest, `HARNESS_COMMIT`, the non-production mock environment, and the new empty result path before executing the plan. The pre-existing `ToDoWithMeApplicationTests` package/configuration failure remains a declared non-observability risk; bootJar, mock dry run and runtime gates are the Phase 8A execution evidence.
