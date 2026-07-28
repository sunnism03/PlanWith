# Phase 1 — Context Verification

## Input files

- `AGENTS.md`
- `docs/codex/planwith-performance-workflow.md`
- `build.gradle`
- `gradle/wrapper/gradle-wrapper.properties`
- `Dockerfile`
- `scripts/start.sh`
- `scripts/stop.sh`
- `src/main/resources/application.yaml`
- `src/main/resources/application-dev.yaml`
- Chatbot, Group Event, Security, Redis의 현재 로컬 Java 소스

## Repository identity

- **FACT**: branch는 `perf/chatbot-schedule`이다.
- **FACT**: 조사 시작 commit은 `ae69de9fcb5cc5c8dac9ebbde403791adc022de0`이다.
- **FACT**: 조사 시작 시 `AGENTS.md`, workflow 문서와 `performance/state.md`에 아직 commit되지 않은 사용자 변경이 있다. 이 변경은 보존한다.

## Runtime and build

- **FACT**: Spring Boot `3.3.5`, Java toolchain `17`, Gradle wrapper `8.2`이다.
- **FACT**: 현재 로컬 JVM은 Java 17이다.
- **FACT**: Dockerfile base runtime은 Java 21 JRE이고 `java -jar app.jar`로 실행한다.
- **FACT**: Dockerfile은 port `8080`만 expose한다.
- **FACT**: 배포 스크립트도 별도 profile 인자 없이 `java -jar`로 실행한다.
- **FACT**: 기본 Spring profile은 `dev`이다.
- **FACT**: runtime 설정 파일은 `src/main/resources/application.yaml`과 `src/main/resources/application-dev.yaml`이다.
- **FACT**: datasource, Redis, JWT와 OpenAI 값은 환경변수로 주입할 수 있다. 값은 기록하지 않는다.
- **FACT**: 테스트 명령은 `./gradlew test`이다.
- **UNKNOWN**: sandbox가 기본 Gradle cache lock을 열지 못해 현재 wrapper 실행은 검증되지 않았다. 별도 workspace-local Gradle cache로 재검증해야 한다.

## Actual endpoints

### Chatbot

| Method | Endpoint | Request | Response | Classification |
|---|---|---|---|---|
| POST | `/chatbot/messages` | JSON `ChatbotReqDTO` | HTTP 200, `ApiResponse<ChatbotResponseWrapper>` | **FACT** |
| POST | `/chatbot/message?accept={boolean}&isAlarmed={boolean}` | JSON `TokenReqDTO` | `ApiResponse<Void>`; exact HTTP status not yet executed | **FACT** contract / **UNKNOWN** status |

`ChatbotReqDTO`는 `intention`, `categoryId`, 필수 `prompt`를 가진다. `TokenReqDTO`는 필수 `token`을 가진다. 생성 응답의 `data`에는 `responses`와 `tokenReqDTO`가 있다.

- **Specification mismatch**: `ApiResponse.onCreateSuccess` 정적 factory의 `@ResponseStatus`는 Controller response status를 201로 만들지 않았고 실제 mock dry run에서 `/chatbot/messages`는 HTTP 200을 반환했다.

### Group Event CRUD

| Operation | Method | Actual endpoint | Request/response | Classification |
|---|---|---|---|---|
| 월 목록 | GET | `/groupEvents/{group_id}/events?year=&month=` | `GetGroupEventResDTO` | **FACT** |
| 특정 할당 사용자 월 목록 | GET | `/groupEvents/{group_id}/events/{user_id}?year=&month=` | `GetGroupEventResDTO` | **FACT** |
| 사용자 전체 그룹 월 목록 | GET | `/groupEvents?year=&month=` | `GetGroupEventResDTO` | **FACT** |
| 생성 | POST | `/groupEvents/{group_id}/events` | `GroupEventCreateReqDTO` → `GroupEventResDTO` | **FACT** |
| 수정 | PATCH | `/groupEvents/{group_id}/events/{group_event_id}` | `GroupEventUpdateReqDTO` → `GroupEventResDTO` | **FACT** |
| 삭제 | DELETE | `/groupEvents/{group_id}/events/{group_event_id}` | body 없음 | **FACT** |
| 단건 조회 | — | 구현 없음 | — | **FACT** |

Group Event 생성 DTO의 필수 필드는 `title`, `startDate`, `endDate`, `isAlarmed`, `groupId`, `assignedMemberList`이다. 수정 DTO는 body의 `groupEventId`만 필수이고 나머지는 선택이다.

## Authentication

- **FACT**: access token은 `Authorization: Bearer <token>` header로 전달한다.
- **FACT**: `JWTFilter`가 token email로 매 요청 User를 DB 조회하고 `CustomUserDetails`를 SecurityContext에 저장한다.
- **FACT**: Controller는 `@AuthenticationPrincipal CustomUserDetails`를 사용한다.
- **HYPOTHESIS**: Security matcher가 하위 경로까지 의도대로 보호하는지는 Spring Security matcher 동작을 실행 검증해야 한다.

## Redis usage and key structure

- **FACT**: Chatbot payload key는 `chatbot:{randomKey}`, token mapping은 `chatbot_token:{token}`이며 TTL은 각각 3600초이다.
- **FACT**: JWT refresh/logout 계열도 Redis를 사용하지만 Chatbot 성능 범위에서는 인증 경로에 포함될 때만 관측한다.
- **FACT**: Chatbot 데이터는 JSON 문자열로 저장된다.
- **FACT**: 현재 reject 삭제 구현은 조회한 raw `key`만 삭제하며 `chatbot:` prefix와 `chatbot_token:` mapping을 삭제하지 않는다.

## Specification mismatch

- **FACT**: workflow가 요구한 Group Event 단건 조회 endpoint는 현재 Controller에 없다.
- **FACT**: workflow 범위는 Group Event CRUD이고 Group Invitation은 제외된다. 저장소에 Group Invitation 구현은 존재하지만 분석 대상에 포함하지 않는다.
- **FACT**: Docker runtime Java 21과 Gradle Java toolchain 17이 다르다.
- **FACT**: workflow가 가정한 Actuator/Micrometer, management port `9091`, `dev-perf` profile과 Pinpoint 설정은 현재 build와 resources에 없다.
- **FACT**: `docs/codex/00-project-overview.md` 등 이전 AGENTS 지침에서 언급된 보조 문서 9개는 현재 checkout에 없다.

## Secret handling

- **FACT**: datasource credential, JWT secret, OpenAI key와 실제 endpoint 값은 이 문서에 저장하지 않았다.
- **FACT**: 사용되는 환경변수 이름은 `SPRING_DATASOURCE_URL`, datasource credential 변수, `SPRING_DATA_REDIS_HOST`, `SPRING_DATA_REDIS_PORT`, JWT secret 변수, `OPENAI_API_KEY`, `OPENAI_API_URL` 계열이다.

## Completion check

- Required output exists and is non-empty: **PASS**
- Required headings: **PASS**
- FACT/HYPOTHESIS/UNKNOWN classifications: **PASS**
- Input paths recorded: **PASS**
