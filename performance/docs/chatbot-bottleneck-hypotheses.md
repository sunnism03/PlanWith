# Chatbot Bottleneck Hypotheses

## Scope and evidence rules

This document is based on the current local source code. Static structure is
classified as `FACT`; runtime impact remains a `HYPOTHESIS` until the user
provides comparable JMeter, Actuator, and Pinpoint evidence.

The prerequisite documents named by the root `AGENTS.md` do not exist in this
checkout:

- `docs/codex/00-project-overview.md`
- `docs/codex/01-performance-scope.md`
- `docs/codex/chatbot/00-feature-overview.md`
- `docs/codex/chatbot/01-test-scenarios.md`
- `docs/codex/performance/00-experiment-contract.md`
- `docs/codex/performance/01-result-template.md`
- `performance/plans/README.md`

This is a `Specification mismatch`. The current source and existing
`performance/plans/**` evidence were used instead.

## Current request flow

`POST /chatbot/messages`

1. `JWTFilter.doFilterInternal` parses the JWT and calls
   `JWTService.getUserByEmail`.
2. `UserRepository.findByEmail` performs one authentication-related user query.
3. `ChatbotController.message` calls `ChatbotServiceImpl.saveRequest`.
4. `RedisChatbotUtil.saveChatbotData` serializes the request and performs two
   Redis writes:
   `chatbot:<payloadId>` and `chatbot_token:<token>`, both with a 3,600-second
   TTL.
5. `ChatbotServiceImpl.analyzeIntention` reads the staged payload and calls
   `ChatGPTServiceImpl.findIntention` synchronously.
6. `findIntention` performs the first blocking `RestTemplate.postForObject`.
7. When the original intention is null, `saveChatbotReq` performs a Redis
   read-modify-write.
8. `ChatbotServiceImpl.createResponseJson` reads the staged payload again and
   calls `ChatGPTServiceImpl.postMessage` synchronously.
9. `postMessage` performs the second blocking `RestTemplate.postForObject`.
10. `ChatbotMapper` extracts JSON with a greedy regular expression and parses
    it with Jackson.
11. The response is stored through another Redis read-modify-write and returned.

There is no `@Async`, `CompletableFuture`, explicit executor, lock, Redis
transaction, or application transaction in this message-generation flow.
There is also no application cache abstraction in this flow.

## Detailed static findings

| Area | Current-code finding | Classification |
|---|---|---|
| Controller → Service → Repository | Message generation enters `ChatbotController`, uses `ChatbotServiceImpl` and `ChatGPTServiceImpl`; the only expected JPA repository call is the authentication `UserRepository.findByEmail` reached through `JWTFilter`/`JWTService` | FACT |
| OpenAI calls | `findIntention` once and `postMessage` once; both are blocking and serial | FACT |
| Expected DB queries | One User SELECT per authenticated request; message generation itself performs no Event/Todo repository write | FACT structure; runtime SQL count UNKNOWN until trace |
| Redis keys | `chatbot:<payloadId>` and `chatbot_token:<token>`, each with 3,600-second TTL | FACT |
| Redis commands | Null-intention EVENT path has a static candidate of 12 GET and 4 SET commands; driver-level command/span count requires runtime confirmation | FACT code path + UNKNOWN runtime |
| Transactions | No application `@Transactional` boundary surrounds message generation or either external HTTP call | FACT |
| Concurrency control | No lock, atomic consume, compare-and-set, or Redis transaction in the staging/update path | FACT |
| Async processing | No async annotation, future, reactive client, or separate executor; servlet thread performs all work | FACT |
| HTTP connection pool | `new RestTemplate()` uses the default request factory; no explicit pooled request factory is configured | FACT; actual runtime reuse UNKNOWN |
| DB connection pool | Spring Boot Data JPA normally supplies Hikari in this dependency set, and Actuator names are expected, but pool sizing and contention must be read from the deployed runtime | FACT dependency + UNKNOWN runtime |
| Redis connection | `LettuceConnectionFactory` backs `RedisTemplate`; deployed connection behavior and contention require runtime metrics/trace | FACT configuration + UNKNOWN runtime |
| Parsing | Greedy regex extraction, Jackson tree parsing, second typed parse, and stream validation occur after the second external call | FACT |
| Serialization | The whole staged Chatbot DTO is repeatedly deserialized and reserialized during updates | FACT |
| Timeout/retry | No explicit connect/read timeout, retry, circuit breaker, or fallback exists in application code | FACT |
| Exceptions | RestTemplate exceptions are not normalized in `ChatGPTServiceImpl`; mapper catches broad exceptions, prints a stack trace, then throws a Chatbot exception | FACT |
| Logs | message path writes operation names, generated Chatbot token, and intention through `System.out` | FACT |
| Pinpoint boundaries | Controller, service methods, two RestTemplate calls, User SELECT, RedisTemplate calls, and exception traces are the intended evidence boundaries; visibility depends on actual agent instrumentation | FACT code boundary + UNKNOWN traceability |

## CHAT-PERF-01

가설 ID: `CHAT-PERF-01`

가설명: 직렬 OpenAI 2회 호출이 Chatbot 생성 latency의 주요 구성요소이다.

분류: `FACT+HYPOTHESIS`

근거 코드:

- `ChatbotController.message`
- `ChatbotServiceImpl.analyzeIntention`
- `ChatbotServiceImpl.createResponseJson`
- `ChatGPTServiceImpl.findIntention`
- `ChatGPTServiceImpl.postMessage`

예상 병목 원인: 첫 외부 호출이 반환된 뒤에만 두 번째 호출을 시작하는
동기·직렬 구조다. 요청당 외부 HTTP 호출 수는 최신 코드에서도 정확히
2회다.

부하 증가 시 예상 현상: mock delay가 증가하면 전체 응답 시간이 두 호출의
지연 합에 가깝게 증가하고, 동시 사용자가 늘면 servlet thread가 외부 응답을
기다리는 시간이 늘어난다.

확인해야 할 지표: JMeter p50/p95/p99/max, throughput, error rate,
`http.server.requests`, Tomcat busy threads, 외부 HTTP span 수와 elapsed.

Pinpoint에서 확인할 위치:
`ChatbotController.message → ChatbotServiceImpl.analyzeIntention →
ChatGPTServiceImpl.findIntention → RestTemplate`, 이어서
`createResponseJson → ChatGPTServiceImpl.postMessage → RestTemplate`.

JMeter로 검증할 방법: S2에서 동일 입력과 동일 환경으로 mock delay
0/300/1000/3000ms를 각각 독립 실행하고 응답 시간 기울기와 외부 span 2개를
비교한다.

가설이 맞다고 판단할 기준: 외부 span이 요청당 2개이며 순차 실행되고, delay
증가분이 전체 latency에 반복 가능하게 거의 두 배로 반영된다.

가설이 틀렸다고 판단할 기준: 외부 호출이 2개가 아니거나 겹쳐 실행되며,
mock delay 변화가 전체 latency 변화의 주된 설명 변수가 아니다.

## CHAT-PERF-02

가설 ID: `CHAT-PERF-02`

가설명: Redis read-modify-write와 반복 GET이 요청당 불필요한 round trip을
만든다.

분류: `FACT+HYPOTHESIS`

근거 코드:

- `RedisChatbotUtil.getKeyByToken`
- `RedisChatbotUtil.getChatbotData`
- `RedisChatbotUtil.saveChatbotReq`
- `RedisChatbotUtil.saveChatbotEventResp`
- `RedisChatbotUtil.saveChatbotTodoResp`

예상 병목 원인: `getChatbotData`마다 token mapping과 payload를 각각
조회하고, update 메서드는 key 조회 후 다시 `getChatbotData`, 저장 직전에
다시 key를 조회한다. intention이 null인 일반 EVENT 경로의 정적 명령 후보는
SET 4회와 GET 12회, 총 16회다.

부하 증가 시 예상 현상: Redis RTT 또는 Redis connection contention이
커질수록 외부 API 지연 외의 고정 비용과 tail latency가 증가한다.

확인해야 할 지표: Pinpoint Redis span count/elapsed, Redis command latency,
JMeter latency, CPU, Tomcat busy threads.

Pinpoint에서 확인할 위치:
`ChatbotServiceImpl.saveRequest/analyzeIntention/createResponseJson` 아래
`RedisChatbotUtil`과 `RedisTemplate`.

JMeter로 검증할 방법: S2의 0ms mock profile과 S3/S9의 단계별 동시성
실행에서 Redis span 수와 누적 시간을 비교한다.

가설이 맞다고 판단할 기준: 요청당 반복 Redis GET이 trace에서 확인되고,
낮은 mock delay 또는 높은 동시성에서 Redis 누적 시간이 유의한 비중을
차지한다.

가설이 틀렸다고 판단할 기준: Redis span 수가 코드상 후보와 다르거나 Redis
누적 시간이 전체와 tail latency에서 무시 가능한 수준이다.

## CHAT-PERF-03

가설 ID: `CHAT-PERF-03`

가설명: 동기 외부 I/O 대기가 Tomcat request thread 포화와 p95/p99 급증을
유발한다.

분류: `FACT+HYPOTHESIS`

근거 코드:

- `ChatGPTServiceImpl`의 동기 `RestTemplate.postForObject`
- 비동기 annotation 또는 별도 executor 부재
- `application-dev-perf.yaml`의 Tomcat metric 노출

예상 병목 원인: 각 요청 thread가 외부 호출 두 번이 끝날 때까지 점유된다.
RestTemplate의 별도 pooled request factory도 구성돼 있지 않다.

부하 증가 시 예상 현상: 일정 동시성 이후 throughput 증가가 둔화되고
Tomcat busy threads, p95/p99, timeout 또는 5xx가 함께 증가할 수 있다.

확인해야 할 지표: throughput, p95/p99, Tomcat busy/current threads,
process CPU, request error, Pinpoint queue/wait 및 external HTTP elapsed.

Pinpoint에서 확인할 위치: 동시 요청의 server thread와 두 외부 HTTP span.

JMeter로 검증할 방법: S3을 1/5/10/20/30 users로 각각 독립 실행하고, S4의
짧은 burst와 비교한다.

가설이 맞다고 판단할 기준: CPU가 포화되지 않았는데 busy threads와
tail latency가 함께 상승하고, 가장 긴 span이 외부 HTTP 대기다.

가설이 틀렸다고 판단할 기준: thread 지표가 안정적인 상태에서 DB, Redis,
CPU 또는 다른 span이 먼저 포화된다.

## CHAT-REL-01

가설 ID: `CHAT-REL-01`

가설명: 명시적 OpenAI connect/read timeout과 retry 정책 부재가 장애 시
요청 thread 장기 점유와 불안정한 실패 latency를 만든다.

분류: `FACT+HYPOTHESIS`

근거 코드:

- `OpenAIConfig.openAiRestTemplate`은 기본 `new RestTemplate()`만 사용
- timeout, retry, circuit breaker, fallback 설정 부재
- `ChatGPTServiceImpl`에 외부 예외 변환 로직 부재

예상 병목 원인: runtime/JDK 기본 timeout에 의존하고 외부 HTTP 예외가
Chatbot 전용 오류 계약으로 정규화되지 않는다.

부하 증가 시 예상 현상: delayed/timeout profile에서 active thread가 오래
유지되고 max latency와 5xx가 증가할 수 있다.

확인해야 할 지표: JMeter elapsed/max/error body, Tomcat busy threads,
external HTTP exception/elapsed, retry span 수.

Pinpoint에서 확인할 위치: 두 `RestTemplate` span과 exception trace.

JMeter로 검증할 방법: 안전한 mock 환경에서만 S7의 timeout, 4xx, 5xx,
malformed JSON profile을 각각 독립 실행한다.

가설이 맞다고 판단할 기준: timeout profile이 JMeter response timeout까지
thread를 점유하거나 오류 형식이 일관되지 않고, 재시도 span은 없다.

가설이 틀렸다고 판단할 기준: 별도 배포 계층에서 짧고 일관된 timeout,
fallback 또는 retry가 실제 trace로 확인된다.

## CHAT-PERF-04

가설 ID: `CHAT-PERF-04`

가설명: intention이 입력에 있어도 의도 분석 외부 호출을 항상 수행해
회피 가능한 latency와 비용이 발생한다.

분류: `FACT+HYPOTHESIS`

근거 코드:

- `ChatbotController.message`는 항상 `analyzeIntention` 호출
- `analyzeIntention`은 기존 intention 확인 전에 `findIntention` 호출
- 기존 intention은 외부 호출 후 Redis update 생략 여부에만 사용

예상 병목 원인: `intention=EVENT` 등 이미 분류된 요청도 첫 외부 호출을
생략하지 않는다.

부하 증가 시 예상 현상: intention 지정 여부와 무관하게 외부 호출 2회,
유사한 external elapsed 및 처리량 한계가 관찰된다.

확인해야 할 지표: 두 조건의 외부 HTTP 호출 수, 전체 latency, Redis span
수, throughput.

Pinpoint에서 확인할 위치: `analyzeIntention → findIntention` span 존재 여부.

JMeter로 검증할 방법: S6을 같은 prompt로 `intention` 빈 값과 `EVENT` 값
각각 실행한다.

가설이 맞다고 판단할 기준: 두 조건 모두 첫 외부 span이 존재하며 전체
latency 차이는 Redis update 한 구간 정도에 그친다.

가설이 틀렸다고 판단할 기준: intention 지정 조건에서 첫 외부 호출이 실제로
생략된다.

## CHAT-PERF-05

가설 ID: `CHAT-PERF-05`

가설명: 인증 필터의 사용자 DB 조회가 모든 Chatbot 요청에 고정 SQL 비용과
Hikari connection 수요를 추가한다.

분류: `FACT+HYPOTHESIS`

근거 코드:

- `JWTFilter.doFilterInternal`
- `JWTService.getUserByEmail`
- `UserRepository.findByEmail`

예상 병목 원인: 유효 JWT 요청마다 email 기반 User SELECT를 실행한다.
Chatbot message 본문 자체는 DB에 Event/Todo를 쓰지 않지만 인증 단계는 DB
connection을 사용한다.

부하 증가 시 예상 현상: 동시성 증가 시 Hikari active/pending과 인증 SQL
elapsed가 상승할 수 있다.

확인해야 할 지표: 요청당 SQL count, User SELECT elapsed, Hikari
active/pending/timeout, JMeter tail latency.

Pinpoint에서 확인할 위치: `JWTFilter → JWTService.getUserByEmail →
UserRepository.findByEmail`.

JMeter로 검증할 방법: S9를 1/5/10/20 users로 독립 실행하고 SQL count와
Hikari 지표를 동기화한다.

가설이 맞다고 판단할 기준: 요청당 User SELECT가 반복되고 Hikari
pending/timeout 또는 SQL elapsed가 tail latency 상승과 함께 증가한다.

가설이 틀렸다고 판단할 기준: 인증 DB 조회가 실제 배포에서 캐시/다른 계층으로
제거돼 있거나 Hikari와 SQL 시간이 병목에 기여하지 않는다.

## CHAT-PERF-06

가설 ID: `CHAT-PERF-06`

가설명: 응답 JSON 추출·이중 parsing과 Redis payload serialization이 큰
응답에서 CPU 및 allocation 비용을 증가시킨다.

분류: `FACT+HYPOTHESIS`

근거 코드:

- `ChatbotMapper.extractJson`의 DOTALL greedy `\\{.*\\}` 정규식
- `readTree(...).get(...).toString()` 뒤 `readValue`
- `RedisChatbotUtil`의 반복 ObjectMapper serialization/deserialization

예상 병목 원인: 문자열 전체 정규식 탐색, tree 변환 후 재파싱, staged
payload 전체 재직렬화가 응답 항목 수와 크기에 따라 증가한다.

부하 증가 시 예상 현상: 큰 mock payload 또는 지속 부하에서 CPU, heap,
GC pause와 application processing span이 증가할 수 있다.

확인해야 할 지표: process CPU, JVM heap, GC pause, Controller/Service
internal elapsed, response bytes.

Pinpoint에서 확인할 위치: 두 번째 external HTTP span 종료 후
`ChatbotMapper`와 Redis SET 사이의 application elapsed.

JMeter로 검증할 방법: S5 지속 부하에서 고정 payload 크기를 유지하고
heap/GC 추이를 본다. payload 크기 비교는 mock이 명시적으로 지원될 때만
별도 실행한다.

가설이 맞다고 판단할 기준: 외부/Redis/SQL elapsed로 설명되지 않는 내부
시간과 allocation/GC가 payload 크기 또는 지속 시간에 따라 증가한다.

가설이 틀렸다고 판단할 기준: 내부 parsing/serialization 시간이 측정
해상도 이하이고 CPU·GC 악화가 없다.

## CHAT-OBS-01

가설 ID: `CHAT-OBS-01`

가설명: 요청 token stdout와 parser stack trace가 로그 I/O, 관측 노이즈,
민감 식별자 노출을 만든다.

분류: `FACT+HYPOTHESIS`

근거 코드:

- `ChatbotServiceImpl`의 `System.out.println`
- `ChatbotMapper`의 `printStackTrace`

예상 병목 원인: 동기 stdout와 전체 stack trace가 실패/burst 상황에서
로그량을 늘린다.

부하 증가 시 예상 현상: 로그 파일 증가, exception profile에서 application
elapsed 증가, 결과물 secret scan 실패 가능성이 있다.

확인해야 할 지표: 로그 line/byte 증가량, exception rate, application
elapsed, secret scan.

Pinpoint에서 확인할 위치: 직접 span이 없을 수 있으므로 exception trace와
외부 span 이후의 unexplained application time을 참고한다.

JMeter로 검증할 방법: S7 malformed profile과 S4 burst 결과의 로그를
비밀값 없이 요약한다.

가설이 맞다고 판단할 기준: token 또는 반복 stack trace가 로그에서
확인되고 부하에 비례해 증가한다.

가설이 틀렸다고 판단할 기준: 배포 로그 계층에서 해당 출력이 제거되거나
비동기·비저장 처리되고 성능/보안 영향이 관찰되지 않는다.

## Cache applicability

`S8 Cache/Redis comparison` is `NOT_APPLICABLE` as a cache-hit/miss test.
The current Chatbot generation flow has no Spring Cache, memoization, or
response cache. Redis is used as per-request staging with random payload and
token keys, so repeating the same prompt cannot produce a cache hit. Redis
overhead is instead evaluated by `CHAT-PERF-02` through S2, S3, and Pinpoint
evidence.
