# Phase 6 — JMeter Test Design

## Input files

- `performance/plans/context-verification.md`
- `performance/plans/chatbot-code-map.md`
- `performance/plans/chatbot-bottleneck-hypotheses.md`
- `performance/plans/group-event-crud-code-map.md`
- `performance/plans/group-event-concurrency-hypotheses.md`
- `performance/plans/observability-checklist.md`

## Global properties

| Property | Purpose |
|---|---|
| `baseUrl` | test application URL |
| `threads` | virtual users |
| `rampUp` | ramp-up seconds |
| `duration` | measured duration |
| `testRunId` | unique data namespace |
| `connectTimeout` | JMeter client connect timeout |
| `responseTimeout` | JMeter client response timeout |
| `usersCsv` | runtime-only user/JWT fixture |
| `useRealOpenAI` | must be false for measured/dry runs |
| `mockOpenAiDelayMs` | mock response delay |

`usersCsv` is never tracked. Expected columns are `userAlias,jwt,userId,categoryId,groupId,groupEventId`. Each thread selects an isolated row except scenarios intentionally sharing token/event.

## Common Test Plan Tree

```text
Test Plan
├── User Defined Variables
├── CSV Data Set Config (runtime-only usersCsv)
├── HTTP Request Defaults
├── HTTP Header Manager (Authorization: Bearer ${jwt})
├── setUp Thread Group
│   ├── testRunId validation
│   ├── fixture precondition
│   └── DB/Redis pre-invariant command
├── Scenario Thread Group
│   ├── optional Synchronizing Timer
│   ├── HTTP sampler
│   ├── JSON Extractor
│   └── assertions
└── tearDown Thread Group
    ├── DB/Redis post-invariant command
    └── cleanup command
```

Measured mode has no GUI listener. JTL is written by CLI. Warm-up and measurement use separate runIds.

## Scenario catalog

### C1 — Mocked Chatbot Message

- `scenarioId`: `C1`
- `hypothesisId`: `CHAT-PERF-01`, `CHAT-REL-01`, `CHAT-PERF-02`, `CHAT-OBS-01`, `CHAT-COR-03`
- Endpoint: `POST /chatbot/messages`
- Body:

```json
{
  "intention": null,
  "categoryId": "${categoryId}",
  "prompt": "testRunId=${testRunId}; create one event"
}
```

- OpenAI mode: `MOCK`
- Thread group: 1 user for dry run; configurable closed workload later.
- Extractor: `$.data.tokenReqDTO.token` → `chatbotToken`; `$.data.responses[0]`.
- Assertions: actual HTTP 200, body `$.code=201`, token non-empty, response list non-empty.
- DB invariant: no Event/Todo row is created by message generation.
- Redis invariant: token mapping and payload exist with positive TTL.
- Cleanup: delete only keys associated with extracted token/testRunId.
- Risk: mock endpoint mismatch; real OpenAI prohibited.

### C2 — Real OpenAI Compatibility Smoke

- `scenarioId`: `C2`
- `hypothesisId`: `CHAT-COR-03`
- Classification: `SMOKE_ONLY`
- Same message endpoint/body contract as C1.
- Users: 1–3 maximum; no barrier; no load or throughput interpretation.
- Excluded from Phase 7 selection, measured baseline and before/after metrics.
- Optional result path: `performance/results/smoke/openai-real/<runId>/**`.
- Failure is a compatibility risk and does not invalidate mock baseline.

### C3 — Single Accept

- `scenarioId`: `C3`
- `hypothesisId`: `CHAT-COR-01`
- Setup: run C1 and extract one token with a one-item mock response.
- Endpoint: `POST /chatbot/message?accept=true&isAlarmed=false`
- Body: `{"token":"${chatbotToken}"}`
- Assertions: HTTP 201 and `$.code=201`.
- DB invariant: exactly one expected Event/Todo row for this token.
- Redis invariant: payload and token mapping must be absent after success; current code is expected to violate this but result is measured.
- Cleanup: delete created DB row by testRunId and both Redis key families.
- OpenAI mode: mock only during setup.

### C4 — Duplicate Accept Concurrency

- `scenarioId`: `C4`
- `hypothesisId`: `CHAT-CON-01`
- Setup: one shared token from C1.
- Endpoint/body: same as C3.
- Thread group: N users with one shared token and Synchronizing Timer.
- Assertions: permitted statuses are recorded; 5xx is not silently accepted.
- DB invariant: total created rows must equal staged item count, not N × item count.
- Redis invariant: token is consumed at most once and absent after terminal success.
- Cleanup: testRunId-scoped DB rows and token keys.
- Risk: correctness test, not throughput test.

### C5 — Multi-item Accept

- `scenarioId`: `C5`
- `hypothesisId`: `CHAT-COR-02`
- Setup: mock returns at least two uniquely titled items.
- Endpoint/body: same as C3.
- Thread group: one user.
- Assertions: success path creates all items; failure profile reports non-success.
- DB invariant: all-or-none for the defined failure profile; if product contract is not all-or-none, record `UNKNOWN` rather than asserting.
- Redis invariant: terminal lifecycle recorded.
- Cleanup: delete all testRunId rows and keys.
- Risk: deterministic mid-list failure requires a safe test fixture; otherwise failure subcase is `NOT_APPLICABLE`.

### C6 — Reject and Cleanup

- `scenarioId`: `C6`
- `hypothesisId`: `CHAT-COR-01`
- Setup: C1 token.
- Endpoint: `POST /chatbot/message?accept=false&isAlarmed=false`
- Body: `{"token":"${chatbotToken}"}`
- Assertions: HTTP 201, no DB row.
- Redis invariant: both `chatbot:*` payload and `chatbot_token:*` mapping absent.
- Cleanup: defensive removal of both key families.

### GE-E1 — Concurrent Update Same Event

- `scenarioId`: `GE-E1`
- `hypothesisId`: `GE-E1`
- Endpoint: `PATCH /groupEvents/${groupId}/events/${groupEventId}`
- Body A/B:

```json
{
  "groupEventId": "${groupEventId}",
  "title": "testRunId=${testRunId};writer=${writer}",
  "assignedMemberList": ["${assignedUserId}"]
}
```

- Thread group: two authorized users, shared event, Synchronizing Timer.
- Assertions: record both HTTP statuses and response event ID.
- DB invariant: final event/mappings/alarms correspond to a defined winner or explicit conflict; no silent success-loss policy is assumed.
- Cleanup: restore/delete dedicated test event only.
- OpenAI: `NOT_APPLICABLE`.

### GE-E2 — Update Versus Delete

- `scenarioId`: `GE-E2`
- `hypothesisId`: `GE-E2`
- Branch A: PATCH as GE-E1.
- Branch B: `DELETE /groupEvents/${groupId}/events/${groupEventId}`.
- Thread group: two authorized users with barrier.
- Assertions: allowed statuses must be enumerated from observed contract; unexpected 5xx fails.
- DB invariant: no orphan mapping/alarm; response outcomes are consistent with final event existence.
- Cleanup: remove dedicated fixture and orphans by testRunId.

### GE-E3 — Double Delete

- `scenarioId`: `GE-E3`
- `hypothesisId`: `GE-E3`
- Endpoint: DELETE same event from two synchronized users.
- DB invariant: event/mappings/alarms absent exactly once; no unrelated rows changed.
- Selection: fallback when GE-E5 deterministic injection is unavailable.

### GE-E5 — Mapping/Alarm Atomicity

- `scenarioId`: `GE-E5`
- `hypothesisId`: `GE-E5`
- Endpoint: PATCH changing multiple assignees/alarm state.
- Barrier: not required for deterministic failure subcase; concurrency variant may pair two updates.
- Invariants: no unrelated mapping deletion, no orphan alarm, final assignees equal response.
- Applicability: `APPLICABLE` statically, but executable failure injection is `UNKNOWN` without a safe test-only DB constraint/fault mechanism.

## Excluded scenarios

- Group Event single read: `NOT_APPLICABLE`, endpoint absent.
- GE-E4 duplicate business schedule: excluded until uniqueness policy exists.
- GE-E6 membership race: excluded from initial three due unknown in-flight authorization policy.
- Group Invitation: out of scope.

## Observability

- JMeter: count, throughput, error rate, p50/p95/p99/max.
- Actuator: collector attempts the workflow metric list; current checkout is expected to return `NOT_AVAILABLE` because Actuator is absent.
- Pinpoint: only supplied trace evidence is used; otherwise `UNKNOWN`.
- DB/Redis: scripts must return explicit `PASS`, `FAIL`, or `NOT_AVAILABLE`; never synthesize values.

## Safety and cleanup

- `useRealOpenAI=true` fails dry-run/measured validation.
- base URL must be allowlisted as local/test and must not be a production host.
- testRunId must be non-empty and unique.
- cleanup may target only testRunId-scoped data and explicitly extracted Redis keys.
- JWT and secret values are runtime inputs and are excluded from JTL, logs and tracked files.

## Completion check

- Every scenario has scenarioId/hypothesisId: **PASS**
- Actual endpoint and DTO fields used: **PASS**
- Assertion, invariant, cleanup, OpenAI mode and risk defined: **PASS**
- NOT_APPLICABLE/out-of-scope scenarios excluded: **PASS**
