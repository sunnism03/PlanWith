# Phase 7 — Selected Experiments

## Input files

- `performance/plans/chatbot-bottleneck-hypotheses.md`
- `performance/plans/group-event-crud-code-map.md`
- `performance/plans/group-event-concurrency-hypotheses.md`
- `performance/plans/observability-checklist.md`
- `performance/plans/jmeter-test-design.md`
- `performance/plans/test-harness-report.md`

## Selection gate

A candidate must have current code evidence, an executable harness scenario, baseline/after comparability, service impact and an independent change boundary. `SMOKE_ONLY` is excluded.

## Candidate scoring

| hypothesisId | Candidate | Role relevance | Depth | Quantifiable | Service impact | E2E | Differentiation | Feasible | Penalty | Score | Selected | Reason |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|---|
| CHAT-PERF-01 + CHAT-REL-01 | Blocking LLM latency and failure propagation | 20 | 18 | 18 | 14 | 10 | 9 | 5 | 0 | 94 | YES | C1 JMX and mock dry run are executable |
| CHAT-CON-01 + CHAT-COR-01 | Atomic token consumption and cleanup | 20 | 20 | 18 | 15 | 10 | 10 | 3 | -8 | 88 | NO | C3/C4/C6 design exists but executable JMX was not implemented/dry-run |
| CHAT-COR-02 | Multi-item all-or-none persistence | 18 | 19 | 15 | 15 | 10 | 9 | 2 | -12 | 76 | NO | safe deterministic mid-list failure fixture unavailable |
| CHAT-PERF-02 | Redis command amplification | 17 | 14 | 14 | 9 | 8 | 6 | 4 | -10 | 62 | NO | Pinpoint/Redis command timing unavailable; overlaps Chatbot path |
| GE-E1 | Group Event lost update | 19 | 20 | 16 | 15 | 10 | 10 | 2 | -10 | 82 | NO | code-grounded APPLICABLE, but no executable fixture/JMX dry run |
| GE-E2 | Group Event update/delete | 19 | 20 | 15 | 15 | 10 | 10 | 2 | -12 | 79 | NO | no executable Group Event fixture/JMX dry run |
| GE-E5 | Mapping/alarm atomicity | 18 | 20 | 14 | 15 | 10 | 9 | 1 | -15 | 72 | NO | deterministic safe failure injection unavailable |
| CHAT-OBS-01 | token/stdout logging | 13 | 10 | 12 | 12 | 8 | 6 | 5 | -12 | 54 | NO | important risk but primarily cleanup, not independent performance experiment |
| CHAT-COR-03 | greedy JSON compatibility | 17 | 15 | 14 | 13 | 9 | 8 | 3 | -8 | 71 | NO | mock profiles not yet implemented beyond valid response |

Only one experiment is selected. Selecting unsupported candidates to reach three would violate the workflow.

## EXP-01 — Blocking LLM Latency and Failure Propagation

- hypothesisId: `CHAT-PERF-01`, `CHAT-REL-01`
- scenarioId: `C1`
- Independent comparison: yes
- OpenAI mode: mock only
- Resume-strength focus: external AI dependency latency, thread occupancy and controlled failure behavior

### Baseline criteria

- same `chatbot-c1-dry-run.jmx` hash;
- fixed mock response and `mockOpenAiDelayMs`;
- same application commit, profile, H2/MySQL test-data shape, Redis and JMeter properties;
- JMeter request count, error rate, throughput, p50/p95/p99/max;
- mock delay profiles: normal and delayed/failure;
- `http.server.requests` and Tomcat metrics only when Actuator becomes available; otherwise `NOT_AVAILABLE`;
- Pinpoint external HTTP count/elapsed only with supplied evidence; otherwise `UNKNOWN`;
- DB invariant: C1 creates no Event/Todo row;
- Redis invariant: one payload and token mapping with positive TTL after message.

### After criteria

- identical JMX, mock response/delay, test data, threads, ramp-up and duration;
- intended differences limited to application commit and resulting image;
- compare error behavior and latency distribution;
- correctness assertions and Redis/DB invariants must not regress;
- any reduction in external call count requires runtime evidence, not inference.

### Target and verdict rule

- Target: reduce avoidable end-to-end latency or bound failure latency without changing API response correctness.
- `VERIFIED_IMPROVEMENT`: comparable run, lower p95/p99 or bounded timeout behavior, no invariant regression.
- `INCONCLUSIVE`: metrics/conditions differ or required evidence is unavailable.

### Observability

- Actuator: HTTP server timing, Tomcat busy threads, CPU; currently `NOT_AVAILABLE`.
- Pinpoint: two external HTTP spans and longest span; currently `UNKNOWN`.
- Harness evidence: JMeter JTL, mock mode, artifact manifest, DB/Redis invariant outputs.

### Draft resume bullet

> Mock 기반 LLM 의존성 실험을 설계해 직렬 외부 호출과 장애 전파를 재현하고, 동일 조건의 p95/p99·오류율·정합성 invariant로 개선 효과를 검증.

## Deferred candidates

`CHAT-CON-01`, `CHAT-COR-01`, `GE-E1`, `GE-E2`, `GE-E5` remain high-value follow-ups. They require an additional harness generation and dry run before selection; they are not silently treated as selected.

## Completion check

- Selected experiment count: 1 (≤3): **PASS**
- Real hypothesisId/scenarioId linkage: **PASS**
- `SMOKE_ONLY` excluded: **PASS**
- Independent baseline/after criteria: **PASS**
- Unsupported candidates not forced into selection: **PASS**

