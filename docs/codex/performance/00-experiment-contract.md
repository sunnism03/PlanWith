# Performance Experiment Contract

## Run types

- `baseline`
- `after-<change-name>`
- `diagnostic`
- `concurrency`

## Required run metadata

```yaml
run_id:
run_type:
timestamp:
git_branch:
git_commit:
dirty_worktree:
java_version:
spring_boot_version:
spring_profile:
host_cpu:
host_memory:
application_instances:
database_type:
database_version:
database_rows:
redis_version:
openai_mode:
openai_mock_latency_ms:
jmeter_threads:
jmeter_ramp_up_seconds:
jmeter_duration_seconds:
jmeter_scenario:
pinpoint_version:
pinpoint_sampling:
notes:
```

## Warm-up

측정 전에 별도의 warm-up을 실행한다.

- JVM class loading
- JIT compilation
- DB connection initialization
- Redis connection initialization

warm-up 결과는 본 측정 결과에서 제외한다.

## Chatbot performance metrics

- request count
- throughput
- error rate
- p50/p95/p99/max
- OpenAI HTTP elapsed
- Redis GET/SET/DELETE count
- Redis elapsed
- JSON mapping elapsed
- SQL count
- SQL elapsed
- Tomcat busy/max
- Hikari active/pending/max
- CPU
- heap
- GC pause

## Concurrency correctness metrics

- concurrent request count
- success/conflict/error count
- final invitation rows
- final membership rows
- final event/todo rows
- duplicate rows
- final invitation status
- remaining Redis keys
- alarm rows
- invariant pass/fail

## Comparison rule

성능 개선은 다음 조건을 모두 고려한다.

- 기능 assertion 통과
- error rate 증가 없음
- p95와 p99
- throughput
- saturation metric
- 같은 실험 조건

동시성 개선은 최종 invariant 통과를 최우선으로 한다.
응답 시간이 줄어도 중복 데이터가 생기면 실패다.

## Result storage

```text
performance/results/<run_id>/
├── run-metadata.yml
├── jmeter/
│   ├── result.jtl
│   ├── summary.json
│   └── html-report/
├── actuator/
│   └── metrics.jsonl
├── pinpoint/
│   ├── observations.md
│   └── screenshots/
├── database/
│   └── invariant-check.json
├── redis/
│   └── invariant-check.json
└── application/
    └── application.log
```
