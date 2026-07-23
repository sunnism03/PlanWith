# Chatbot Test Scenarios

## C1 — Message generation with mocked OpenAI

### Purpose

OpenAI 지연을 통제한 상태에서 Spring Boot, Redis, JSON parsing,
Tomcat thread의 처리 특성을 측정한다.

### Request

- Endpoint: 로컬 `ChatbotController`에서 확정
- 후보: `POST /chatbot/messages`
- Intention은 EVENT, TODO, PLAN_RECOMMENDATION을 분리한다.

### Mock variants

- fixed latency: 0ms / 300ms / 1000ms / 3000ms
- result size: 1 / 10 / 50 items
- normal JSON
- invalid JSON
- timeout
- 429 또는 5xx

### Observe

- total p95/p99
- external HTTP elapsed
- Tomcat busy threads
- Redis operation count and elapsed
- JSON mapping elapsed
- CPU and heap
- error classification

---

## C2 — Accept one generated result

### Purpose

단일 token 수락의 정상 흐름과 Redis cleanup을 검증한다.

### Setup

1. 사용자와 category 준비
2. Chatbot message 요청
3. 반환 token 저장
4. 후보 item 수를 1개로 고정

### Assertions

- HTTP success
- Event 또는 Todo row가 정확히 1개 증가
- 다른 사용자 row 증가 없음
- token 재조회 결과가 정책과 일치
- 관련 Redis key와 token mapping 정리 여부 확인

---

## C3 — Duplicate accept concurrency

### Purpose

동일 token을 동시에 수락할 때 중복 생성이 발생하는지 검증한다.

### Load

- 같은 사용자
- 같은 token
- 2 / 5 / 10 concurrent requests
- 가능한 한 같은 시점에 시작

### Invariants

- 성공적으로 생성된 Event/Todo set은 한 번만 존재
- token은 한 요청만 consume 가능
- 중복 요청은 명시적 충돌 또는 이미 처리됨 응답
- 부분 생성 없음
- Redis 최종 상태 일관적

### Result fields

- success count
- conflict count
- unexpected error count
- created row count
- duplicate row count
- remaining Redis keys

---

## C4 — Multi-item accept scaling

### Purpose

GPT 후보 수가 늘어날 때 DB 호출 수와 응답 시간이 어떻게 증가하는지 확인한다.

### Sizes

- 1
- 5
- 10
- 30
- 50

### Observe

- total latency
- item당 SQL 수
- user/category 반복 조회 수
- insert count
- alarm insert count
- transaction duration
- Hikari active/pending
- failure rollback behavior

---

## C5 — Real OpenAI smoke

### Purpose

실제 외부 API의 timeout, 응답 변동성, 오류 처리를 확인한다.

### Limits

- 1~3 concurrent users
- 짧은 실행
- 제한된 prompt
- 비용과 rate limit 기록

### Do not use for

- 서버 최대 throughput 산정
- 30명 이상 부하
- 성능 개선 전후의 핵심 비교
