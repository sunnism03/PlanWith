# Phase 4B — Group Event Concurrency Hypotheses

## Input files

- `performance/plans/group-event-crud-code-map.md`
- Current Group Event, Mapping, GroupSetting and Alarm entities/repositories/services

No race is treated as confirmed without execution. Applicability means the current API and authorization structure permit the requests.

## Control inventory

- **FACT**: Group Event/Mapping entities have no `@Version`.
- **FACT**: repositories declare no optimistic/pessimistic lock.
- **FACT**: Group Event create/update/delete have no service-level transaction.
- **FACT**: mapping delete uses a separate repository transaction.
- **FACT**: no entity unique constraint prevents duplicate `(group_event_id, group_setting_id)` mappings.
- **FACT**: no affected-row count is checked.
- **FACT**: any group member can update or delete.
- **UNKNOWN**: live MySQL indexes, foreign keys, isolation level and out-of-source constraints.

## Applicability table

| scenarioId | Scenario | Verdict | Code evidence |
|---|---|---|---|
| GE-E1 | Concurrent update same event | `APPLICABLE` | any member can read/save same ID; no version/lock |
| GE-E2 | Update versus delete | `APPLICABLE` | update and delete share no transaction/lock |
| GE-E3 | Double delete | `APPLICABLE` | endpoint can be replayed; no idempotency handling |
| GE-E4 | Similar concurrent create | `APPLICABLE` technically; business duplicate rule `UNKNOWN` | no idempotency/unique business key |
| GE-E5 | Event versus Mapping/Alarm partial update | `APPLICABLE` | separate repository operations without outer transaction |
| GE-E6 | Membership removal versus CRUD | `APPLICABLE` | membership is checked before later writes without lock |
| GE-R1 | Single-event concurrent read | `NOT_APPLICABLE` | no single-event GET endpoint |

## GE-E1 — Concurrent update

- Preconditions: one event; two authenticated members in the path group; distinct valid update bodies.
- Interleaving: A and B both load old event → A saves version A → B saves version B.
- Expected current result: **HYPOTHESIS** last successful save silently overwrites prior fields; mapping/alarm changes can interleave.
- GroupEvent invariant: a success response must not be silently lost without an explicit conflict policy.
- Mapping invariant: final assigned users must correspond to the winning event version without duplicates.
- Alarm invariant: alarms must match final assigned users and event data.
- HTTP: both may return success; must be measured.
- Evidence: no `@Version`, lock or stale request check.

## GE-E2 — Update versus delete

- Preconditions: one event, two authenticated group members.
- Interleaving: updater loads event → deleter removes event/mappings → updater saves entity or mutates mappings/alarms.
- Expected current result: **UNKNOWN**; JPA merge/delete timing may yield resurrection, exception, or partial associated data.
- Invariant: no orphan mapping/alarm; a successful update must not claim durable state that is immediately absent without defined conflict semantics.
- HTTP: one or both success/error outcomes must be correlated with final DB state.

## GE-E3 — Double delete

- Preconditions: one event; same or distinct authorized members.
- Interleaving: both call `deleteById` near-simultaneously.
- Expected current result: **UNKNOWN**; second delete may succeed or throw depending on repository/entity timing.
- Invariant: event, mappings and alarms are absent; no 5xx from an otherwise idempotent policy unless API explicitly defines it.
- Evidence: no explicit lookup/idempotency branch or affected-row validation.

## GE-E4 — Similar concurrent create

- Preconditions: same group and equivalent create bodies.
- Expected current result: **HYPOTHESIS** two independent event rows are created.
- Business invariant: whether equivalent schedules must be unique is **UNKNOWN**.
- Test selection: exclude unless Phase 7 can state a real product invariant; technical duplication alone is not treated as a defect.

## GE-E5 — Mapping and Alarm partial update

- Preconditions: update changes assigned members and/or alarm data; inject a later mapping/alarm failure.
- Interleaving: Event save → per-member mapping deletes/adds → per-member alarm operations.
- Expected current result: **HYPOTHESIS** partial mapping/alarm changes remain because there is no outer transaction.
- Critical code fact: removal query deletes by `groupSettingId` only, potentially affecting other events even without concurrency.
- Invariants:
  - every mapping references an existing event and membership;
  - every group-event alarm references a valid mapping;
  - removed assignees have no event alarm;
  - unrelated event mappings remain unchanged.

## GE-E6 — Membership removal versus CRUD

- Preconditions: event requester is a member at validation time; leader/owner concurrently removes that membership.
- Interleaving: CRUD membership exists check → membership delete → later event/mapping/alarm writes.
- Expected current result: **UNKNOWN**.
- Invariant: a non-member must not receive new assignments or retain unauthorized mutation effects according to product policy.
- Product policy after in-flight authorization is **UNKNOWN**.

## DB final-state checks

For applicable scenarios, the harness must query or otherwise verify:

1. event row count and final field values;
2. duplicate `(group_event_id, group_setting_id)` pairs;
3. mappings whose event or group setting is absent;
4. alarms whose mapping is absent;
5. deleted event mappings/alarms;
6. unrelated event mappings before versus after;
7. response status paired with committed final state.

No live DB result is claimed here.

## Priority test selection for Phase 6

1. `GE-E1` concurrent update — direct lost-update candidate.
2. `GE-E2` update/delete race — lifecycle and orphan invariant.
3. `GE-E5` mapping/alarm partial update — broadest correctness risk, though deterministic fault injection may be required.

`GE-E3` may be included as a lower-cost correctness scenario if GE-E5 cannot be safely injected. `GE-E4` and `GE-E6` require clearer product invariants before selection.

## Final judgment

- **HYPOTHESIS**: 일부 수정·삭제 및 연관 데이터 시나리오에 동시성 테스트가 필요하다.
- **FACT**: current authorization allows multiple group members to target the same event.
- **UNKNOWN**: live DB constraints and exact concurrent outcomes.

## Completion check

- All candidates classified: **PASS**
- Applicable races include code evidence and interleaving: **PASS**
- Final DB invariants defined without invented values: **PASS**
- Priority scenarios limited to three: **PASS**

