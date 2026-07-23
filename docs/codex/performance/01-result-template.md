# Performance Result Template

## 1. Run

- Run ID:
- Scenario:
- Git commit:
- Date:
- Environment:
- OpenAI mode:
- Threads / ramp-up / duration:

## 2. Preconditions

- User count:
- Category count:
- Event/Todo rows:
- Group count:
- Existing invitation rows:
- Redis initial state:
- Warm-up:

## 3. JMeter summary

| Metric | Value |
|---|---:|
| Requests | |
| Throughput | |
| Error rate | |
| Mean | |
| p50 | |
| p95 | |
| p99 | |
| Max | |

## 4. Actuator summary

| Metric | Baseline/Peak |
|---|---:|
| CPU | |
| Heap | |
| GC pause | |
| Tomcat busy threads | |
| Hikari active | |
| Hikari pending | |

## 5. Pinpoint observations

### Slow transaction

- Endpoint:
- Total elapsed:
- External HTTP:
- Redis:
- SQL:
- Controller/service:
- Exception:

### Call count

- External HTTP count:
- Redis count:
- SQL count:

## 6. Correctness invariants

| Invariant | Expected | Actual | Pass |
|---|---:|---:|---|
| Created Event/Todo rows | | | |
| Duplicate rows | 0 | | |
| Pending invitation rows | | | |
| Membership rows | | | |
| Final invitation status | | | |
| Remaining Redis key | | | |
| Alarm rows | | | |

## 7. Classification

### Confirmed

### Additional measurement needed

### Refuted

## 8. Improvement decision

- Chosen target:
- Evidence:
- Proposed change:
- Rejected alternatives:
- Risks:
- Rollback:
- Re-test scenario:

## 9. Limitations
