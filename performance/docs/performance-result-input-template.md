# Performance Result Input Template

Copy this template for one run. Replace unknown values with `UNKNOWN`; do not
estimate. Never include JWT, API keys, passwords, Authorization headers, or
real user data.

## 실행 정보

- runId:
- experimentId:
- hypothesisId:
- Git commit:
- Docker image ID:
- Docker image tag:
- active profile:
- 실행 일시 KST:
- 실행 일시 UTC:
- 종료 일시 KST:
- 종료 일시 UTC:
- JMeter scenario:
- JMX SHA-256:
- threads:
- iterations:
- ramp-up:
- duration:
- warm-up 분리 여부:
- mock/real OpenAI:
- mock delay:
- mock failure profile:
- API base URL 식별자(비밀 없는 별칭):
- 서버 재시작 전/후 count:
- 테스트 중 배포 또는 설정 변경:

## 비교 가능성

- 비교 대상 runId:
- application commit 일치:
- JMX hash 일치:
- mock response/delay 일치:
- users/fixture shape 일치:
- thread/iteration/ramp-up 일치:
- 서버·DB·Redis 사양 일치:
- Pinpoint sampling 설정 일치:
- 알려진 차이:
- 데이터 신뢰성 판정:

## JMeter

- sample count:
- success count:
- failure count:
- error rate:
- throughput (requests/second):
- average (ms):
- p50 (ms):
- p90 (ms):
- p95 (ms):
- p99 (ms):
- max (ms):
- connect time (ms):
- latency (ms):
- received bytes:
- sent bytes:
- HTTP status 분포:
- assertion failure:
- 주요 오류 응답 분류:
- JTL 상대 경로:
- JMeter log 상대 경로:

오류 body는 secret을 제거한 뒤 오류 형식과 대표 메시지만 기록한다.

## Actuator

수집 주기:

수집 누락 구간:

### HTTP

- `http.server.requests` count delta:
- 대상 URI:
- 대상 URI total time delta:
- 대상 URI max:
- status/exception tags:

### Tomcat

- `tomcat.threads.busy` peak:
- `tomcat.threads.current` peak:
- max threads:

### Hikari

- pool tag:
- `hikaricp.connections.active` peak:
- `hikaricp.connections.idle`:
- `hikaricp.connections.pending` peak:
- max:
- `hikaricp.connections.timeout` delta:

### JVM and CPU

- JVM heap start:
- JVM heap peak:
- JVM heap end:
- GC pause count delta:
- GC pause total time delta:
- process CPU average:
- process CPU peak:
- system CPU average:
- system CPU peak:

모든 값에 Actuator가 제공한 단위와 tag를 함께 기록한다.

## Pinpoint

- Application:
- Agent:
- 조회 시간 범위 KST:
- 조회 시간 범위 UTC:
- transaction count:
- 전체 transaction duration:
- Controller span:
- Service span:
- OpenAI external call 1 elapsed:
- OpenAI external call 2 elapsed:
- external HTTP 호출 순서:
- external HTTP 호출 횟수:
- DB query count:
- DB query cumulative elapsed:
- 인증 User SELECT elapsed:
- Redis span count:
- Redis cumulative elapsed:
- 가장 긴 span:
- 동시 요청 시 증가한 span:
- exception 또는 timeout trace:
- sampling 또는 누락 가능성:
- Transaction List 캡처:
- 대표 Call Tree 캡처:
- 가장 느린 transaction 캡처:
- SQL/Redis/HTTP span 캡처:

이미지를 제공할 수 없으면 위 항목을 Pinpoint transaction 상세 화면에서
텍스트로 옮겨 적는다. 확인하지 못한 값은 `UNKNOWN`으로 둔다.

## DB·Redis 정확성

- 전용 테스트 사용자 수:
- Event 사전/사후 count:
- Todo 사전/사후 count:
- Redis exact-key ledger 생성:
- cleanup 전 exact key 존재:
- cleanup 전 TTL:
- cleanup 후 exact key 부재:
- cleanup 후 TTL `-2`:
- ledger 밖 key 보존:
- cleanup 상태:
- 공유 fixture 변경 여부:
- 예상하지 않은 데이터 변경:

## Latency decomposition

측정 근거가 있는 값만 작성한다.

```text
전체 응답 시간:
Controller 자체 처리:
Service 내부 처리:
OpenAI 호출 1:
OpenAI 호출 2:
DB:
Redis:
serialization/parsing:
thread/connection 대기:
기타:
설명되지 않은 시간:
```

각 항목의 근거:

- 측정된 사실:
- Pinpoint상 관찰:
- 합리적인 추정:
- 추가 검증 필요:

## Run verdict

- 데이터 신뢰성: `RELIABLE / PARTIAL / UNRELIABLE`
- 가설 판정: `CONFIRMED / PARTIALLY_CONFIRMED / DISPROVED / INCONCLUSIVE`
- 판정 근거:
- 다음 권장 실행:
- 비교에 사용하면 안 되는 이유:

