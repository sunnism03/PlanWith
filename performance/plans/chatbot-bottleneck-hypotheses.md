# Phase 3 — Chatbot Bottleneck and Correctness Hypotheses

## Input files

- `performance/plans/chatbot-code-map.md`
- 해당 문서가 참조한 현재 Chatbot, Redis, Event, Todo, Alarm 코드

정적 분석은 병목 또는 장애를 확정하지 않는다. 아래 항목은 동일 조건 실행으로 검증할 가설이다.

## Hypothesis summary

| hypothesisId | Title | Evidence classification | Priority |
|---|---|---|---|
| CHAT-PERF-01 | 직렬 OpenAI 2회 호출이 message latency를 합산한다 | **FACT** 구조, **HYPOTHESIS** 영향 | P1 |
| CHAT-REL-01 | timeout 부재가 요청 thread 장기 점유를 유발한다 | **FACT** 설정, **HYPOTHESIS** 영향 | P1 |
| CHAT-PERF-02 | Redis read-modify-write와 중복 GET이 불필요한 왕복을 만든다 | **FACT** 호출 구조, **HYPOTHESIS** 영향 | P2 |
| CHAT-CON-01 | 동일 token 동시 accept가 중복 row를 만든다 | **FACT** 원자성 부재, **HYPOTHESIS** 결과 | P1 |
| CHAT-COR-01 | accept/reject 후 Redis token lifecycle이 잘못 유지된다 | **FACT** 코드, 실행 최종 상태는 **HYPOTHESIS** | P1 |
| CHAT-COR-02 | multi-item accept 실패가 partial write를 남긴다 | **FACT** transaction 경계, **HYPOTHESIS** 결과 | P1 |
| CHAT-OBS-01 | token stdout와 stack trace가 관측 노이즈·노출을 만든다 | **FACT** 코드 | P2 |
| CHAT-COR-03 | greedy JSON extraction이 malformed/multiple object 응답을 오인한다 | **FACT** parser, **HYPOTHESIS** 실패 범위 | P2 |

## CHAT-PERF-01 — Serial OpenAI calls

- Code evidence: `ChatbotController.message`가 `analyzeIntention` 완료 후 `createResponseJson`을 호출하고 각각 `RestTemplate.postForObject`를 1회 실행한다.
- Reproduction: mock delay를 각 호출에 동일하게 부여하고 1 user message를 반복한다.
- JMeter: C1의 latency p50/p95/p99와 error rate.
- Actuator: `http.server.requests`, Tomcat busy threads.
- Pinpoint: 외부 HTTP span 2개의 순서와 합산 elapsed. 접근 불가 시 `UNKNOWN`.
- Invariant: 응답은 1개 token과 예상 response list를 가져야 하며 DB write는 없어야 한다.
- Decision: external HTTP count가 2이고 total response가 두 mock delay 합에 근접하면 `CONFIRMED`.
- Risk/priority: 사용자 직접 지연, P1.

## CHAT-REL-01 — Missing timeout and failure propagation

- Code evidence: 기본 `RestTemplate`이며 connect/read timeout, retry, circuit breaker가 없다.
- Reproduction: mock이 응답을 지연하거나 연결을 완료하지 않는 failure profile.
- JMeter: timeout error, max latency, active request 수.
- Actuator: Tomcat busy/max, CPU, Hikari는 참고.
- Pinpoint: external HTTP longest span과 exception.
- Invariant: 실패 요청이 Event/Todo DB row를 만들지 않아야 하고 staged Redis TTL은 유한해야 한다.
- Decision: configured client timeout 없이 request가 harness timeout까지 점유되면 `CONFIRMED`.
- Risk/priority: thread exhaustion 후보, P1.

## CHAT-PERF-02 — Redis command amplification

- Code evidence: 일반 message 경로는 정적 호출 맵상 Redis 명령 후보 16회이며 두 update 구간에서 token/payload를 반복 조회한다.
- Reproduction: OpenAI mock delay를 0 또는 낮은 고정값으로 두고 C1 실행.
- JMeter: throughput, p95/p99.
- Actuator: CPU와 Tomcat threads.
- Pinpoint: Redis span count/elapsed. 접근 불가 시 command instrumentation은 `UNKNOWN`.
- Redis invariant: payload와 token mapping은 동일 TTL 정책을 유지해야 한다.
- Decision: trace 또는 instrumented stub에서 예상 중복 GET이 관찰되면 구조 확인; latency 영향은 after 비교 전까지 미확정.
- Risk/priority: 외부 API 지연보다 작을 수 있음, P2.

## CHAT-CON-01 — Duplicate accept

- Code evidence: accept는 staged data를 GET한 뒤 순차 INSERT하고 consumed state, atomic delete, lock, unique request key가 없다.
- Reproduction: 하나의 valid token에 C4 N-way synchronized accept.
- JMeter: 요청별 status와 error.
- Actuator: HTTP count/error, Hikari active/pending.
- Pinpoint: concurrent SQL INSERT와 Redis GET.
- DB invariant: 예상 생성 row 수는 token response item 수를 초과하지 않아야 한다.
- Redis invariant: 성공 뒤 token은 재사용 불가해야 한다.
- Decision: item 수보다 많은 Event/Todo row 또는 둘 이상의 완전 성공이 관찰되면 `CONFIRMED`.
- Risk/priority: 사용자 데이터 중복, P1.

## CHAT-COR-01 — Broken token cleanup

- Code evidence: accept는 delete를 호출하지 않는다. reject는 `chatbot:{key}` 대신 raw `{key}`를 삭제하고 token mapping도 삭제하지 않는다.
- Reproduction: C3 성공 accept와 C6 reject 전후 Redis key/TTL 검사.
- JMeter: response status와 재사용 요청 status.
- Actuator/Pinpoint: Redis DELETE span과 command 대상은 값 노출 없이 prefix category로만 기록.
- DB invariant: reject는 DB row를 만들지 않는다.
- Redis invariant: 성공 accept/reject 뒤 payload와 mapping 모두 없어야 한다.
- Decision: 어느 key든 남거나 token 재사용이 가능하면 `CONFIRMED`.
- Risk/priority: 중복 처리와 stale data, P1.

## CHAT-COR-02 — Partial multi-item write

- Code evidence: orchestration transaction이 없고 Todo는 항목별 transaction, Event create에는 명시적 transaction이 없다.
- Reproduction: C5에서 두 번째 이후 항목만 category/validation 또는 persistence failure가 발생하도록 격리된 fixture 사용.
- JMeter: single request status.
- Actuator: Hikari/HTTP error.
- Pinpoint: 성공 INSERT 후 실패 span.
- DB invariant: 요청 전체 성공이면 전부, 실패면 정책상 전부 rollback되어야 한다. 현재 기대 정책은 **UNKNOWN**이므로 결과를 명시한다.
- Redis invariant: 실패 token의 재시도 정책과 staged data 상태를 기록한다.
- Decision: 실패 응답 뒤 일부 row가 남으면 partial write `CONFIRMED`.
- Risk/priority: 정합성, P1.

## CHAT-OBS-01 — Token and stack-trace output

- Code evidence: `System.out.println`으로 token/intention 출력, parser에서 `printStackTrace`.
- Reproduction: C1 성공 및 malformed mock response.
- JMeter: 기능 assertion만 사용.
- Actuator/Pinpoint: `NOT_APPLICABLE` for direct proof.
- Log invariant: JWT, OpenAI key, Chatbot token과 full sensitive body가 결과에 없어야 한다.
- Decision: secret scan이 token pattern 또는 stack trace를 찾으면 `CONFIRMED`.
- Risk/priority: 운영 로그와 결과 보안, P2.

## CHAT-COR-03 — Greedy JSON extraction

- Code evidence: `Pattern.compile("\\{.*\\}", DOTALL)` 뒤 root의 `events`/`todos`를 읽는다.
- Reproduction: markdown fence, 앞뒤 object, malformed content를 mock response profile로 제공.
- JMeter: expected 422/error contract와 no-DB-write assertion.
- Actuator/Pinpoint: error count/exception; parser method span은 보이지 않을 수 있어 `UNKNOWN`.
- DB/Redis invariant: DB write 없음; staged input은 TTL 내 존재 여부 기록.
- Decision: 유효한 단일 payload가 잘못 실패하거나 여러 object가 잘못 결합되면 `CONFIRMED`.
- Risk/priority: 외부 응답 호환성, P2.

## SaveAll and transaction note

- **FACT**: 현재 repository는 `saveAll`을 직접 호출하지 않는다.
- **HYPOTHESIS**: `saveAll`은 호출 overhead를 줄일 수 있으나 JDBC batching 설정이 없어 SQL round trip 개선은 보장되지 않는다.
- **FACT**: OpenAI 호출 동안 DB transaction을 점유하는 명시적 annotation은 없다.

## Completion check

- Every hypothesis has an ID: **PASS**
- Code evidence and reproduction linked: **PASS**
- JMeter, Actuator, Pinpoint and invariants linked: **PASS**
- Static claims separated from measured claims: **PASS**

