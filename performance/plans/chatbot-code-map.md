# Phase 2 — Chatbot Code Map

## Input files

- `performance/plans/context-verification.md`
- `src/main/java/com/JAI/chatbot/controller/ChatbotController.java`
- `src/main/java/com/JAI/chatbot/service/ChatbotServiceImpl.java`
- `src/main/java/com/JAI/chatbot/service/ChatGPTServiceImpl.java`
- `src/main/java/com/JAI/chatbot/RedisChatbotUtil.java`
- `src/main/java/com/JAI/chatbot/mapper/ChatbotMapper.java`
- `src/main/java/com/JAI/config/OpenAIConfig.java`
- `src/main/java/com/JAI/global/util/RedisUtil.java`
- Personal Event, Personal Todo, Category, Alarm, JWT 관련 호출 코드

## Actual endpoints

- **FACT**: `POST /chatbot/messages`
- **FACT**: `POST /chatbot/message?accept={accept}&isAlarmed={isAlarmed}`
- **FACT**: 두 endpoint 모두 JSON body와 Bearer access token을 요구한다.

## A. Message generation flow

| Step | File and method | I/O and calls | Transaction | Classification |
|---|---|---|---|---|
| Controller | `ChatbotController.message` | `ChatbotReqDTO`, principal | 없음 | **FACT** |
| Request staging | `ChatbotServiceImpl.saveRequest` | Redis payload SET + token mapping SET | 없음 | **FACT** |
| Intention load | `ChatbotServiceImpl.analyzeIntention` | Redis token GET + payload GET | 없음 | **FACT** |
| Intention HTTP | `ChatGPTServiceImpl.findIntention` | synchronous `RestTemplate.postForObject` | 없음 | **FACT** |
| Intention update | `RedisChatbotUtil.saveChatbotReq` | token/payload 반복 GET 후 payload SET | 없음 | **FACT** |
| Generation load | `ChatbotServiceImpl.createResponseJson` | Redis token GET + payload GET | 없음 | **FACT** |
| Generation HTTP | `ChatGPTServiceImpl.postMessage` | synchronous `RestTemplate.postForObject` | 없음 | **FACT** |
| Parse | `ChatbotMapper.toChatbotEventResp` 또는 `toChatbotTodoResp` | greedy regex로 JSON 추출 후 Jackson parse | 없음 | **FACT** |
| Response staging | `saveChatbotEventResp` 또는 `saveChatbotTodoResp` | token/payload 반복 GET 후 payload SET | 없음 | **FACT** |
| Response | `ChatbotController.message` | responses와 token 반환 | 없음 | **FACT** |

- **FACT**: intention이 request에 이미 있어도 `findIntention` 외부 호출은 항상 수행된다.
- **FACT**: 한 `/chatbot/messages` 요청은 OpenAI를 정확히 2회 직렬 호출한다.
- **FACT**: intention을 새로 저장하는 일반 경로의 Redis 명령 후보는 총 16회다: 최초 SET 2회, 두 read-modify-write 구간에서 GET 12회와 SET 2회.
- **FACT**: request에 intention이 이미 있으면 intention update가 생략되어 Redis 명령 후보는 11회다.
- **HYPOTHESIS**: Redis driver 내부 round trip과 connection 사용량은 실행 trace로 확인해야 한다.

## B. Accept flow

| Step | File and method | I/O and calls | Transaction | Classification |
|---|---|---|---|---|
| Controller | `ChatbotController.accept` | token body, accept/isAlarmed query | 없음 | **FACT** |
| Initial validation | `ChatbotServiceImpl.validateAcceptAlarm` | token mapping GET + payload GET | 없음 | **FACT** |
| Branch | `saveEvent` 또는 `saveTodo` | payload를 다시 GET | 없음 | **FACT** |
| Event conversion | `ChatbotMapper.toPersonalEventCreateReqDTO` | DTO 변환 | 없음 | **FACT** |
| Event persistence | `PersonalEventServiceImpl.createPersonalEvent` | user/category 조회, event INSERT, 선택적 alarm INSERT | service transaction 없음 | **FACT** |
| Todo conversion | `ChatbotMapper.toPersonalTodoCreateReq` | DTO 변환 | 없음 | **FACT** |
| Todo persistence | `PersonalTodoServiceImpl.createPersonalTodo` | category 조회, todo INSERT | 항목 하나당 `@Transactional` | **FACT** |

- **FACT**: accept 요청당 Redis GET 후보는 4회다.
- **FACT**: 응답 항목 수를 `N`이라 하면 event/todo 생성 service가 순차적으로 N번 호출된다.
- **FACT**: accept 성공 뒤 Chatbot Redis payload와 token mapping을 삭제하지 않는다.
- **FACT**: 전체 accept orchestration에는 transaction이 없다. Todo는 항목별 transaction이고 Event는 명시적 transaction이 없다.
- **HYPOTHESIS**: 여러 항목 중 하나가 실패하면 앞서 commit된 항목만 남는 partial write가 가능하다.

## C. Reject flow

| Step | File and method | I/O and calls | Transaction | Classification |
|---|---|---|---|---|
| Validation | `validateAcceptAlarm` | Redis GET 2회 | 없음 | **FACT** |
| Delete lookup | `RedisChatbotUtil.deleteChatbotData` | token mapping GET | 없음 | **FACT** |
| Delete | `RedisUtil.delete(key)` | raw random key DELETE | 없음 | **FACT** |

- **FACT**: reject 경로의 Redis 명령 후보는 GET 3회와 DELETE 1회다.
- **FACT**: 실제 payload key는 `chatbot:{key}`인데 삭제는 prefix 없는 `{key}`를 대상으로 한다.
- **FACT**: `chatbot_token:{token}` mapping도 삭제하지 않는다.
- **HYPOTHESIS**: 일반 저장 상태에서는 reject 후 payload와 token mapping이 TTL까지 남는다.

## External HTTP client

- **FACT**: `OpenAIConfig`가 singleton `RestTemplate` bean을 생성한다.
- **FACT**: explicit connect/read timeout이 없다.
- **FACT**: 별도 pooled request factory 또는 retry policy가 없다.
- **FACT**: 호출은 synchronous/blocking이다.
- **UNKNOWN**: 실제 네트워크 timeout과 connection reuse는 runtime/JDK 기본값 및 환경을 측정해야 한다.

## Redis lifecycle

- **FACT**: payload `chatbot:{key}`와 mapping `chatbot_token:{token}` TTL은 3600초다.
- **FACT**: update는 read-modify-write이며 compare-and-set, Lua, transaction 또는 atomic consume이 없다.
- **FACT**: `saveChatbotReq`와 response save는 같은 token mapping을 여러 번 조회한다.
- **FACT**: accept/reject 경합을 직렬화하는 상태 또는 lock이 없다.

## Parsing and logging

- **FACT**: JSON extraction regex는 `\\{.*\\}` with DOTALL인 greedy pattern이다.
- **FACT**: parse exception에서 `printStackTrace()`를 호출한다.
- **FACT**: message 생성 경로에서 token과 intention을 `System.out`으로 출력한다.
- **HYPOTHESIS**: token log는 테스트 결과와 application log에 남을 수 있어 secret-like identifier 노출 검사가 필요하다.

## SQL and transaction map

- **FACT**: JWT filter는 authenticated request마다 email 기반 User SELECT를 수행한다.
- **FACT**: Event 한 건은 최소 User SELECT, Category SELECT, Event INSERT를 유발하고 alarm 사용 시 Alarm INSERT가 추가된다.
- **FACT**: Todo 한 건은 Category SELECT와 Todo INSERT를 유발한다.
- **HYPOTHESIS**: lazy loading과 converter 접근으로 추가 SELECT가 발생할 수 있으므로 SQL count는 trace로 검증해야 한다.
- **FACT**: OpenAI HTTP 호출 동안 명시적 application transaction은 열려 있지 않다.

## Error and duplicate handling

- **FACT**: OpenAI null/malformed response에 대한 명시적 retry/fallback이 없다.
- **FACT**: token consume에 idempotency key, consumed state 또는 atomic delete가 없다.
- **FACT**: 동일 token의 동시 accept는 둘 다 같은 staged data를 읽을 수 있다.
- **HYPOTHESIS**: 동일 token 동시 accept는 중복 Event/Todo row를 만들 수 있다.

## Completion check

- Required output exists and is non-empty: **PASS**
- Message/accept/reject traced end-to-end: **PASS**
- External HTTP, Redis, SQL and transaction boundaries recorded: **PASS**
- FACT/HYPOTHESIS/UNKNOWN classifications: **PASS**

