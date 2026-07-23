# PlanWith Performance Harness Instructions

## 1. Project context

PlanWith는 Spring Boot 기반 일정 관리 서비스다.

현재 저장소에서 확인된 기본 기술 스택:

- Java 17
- Spring Boot 3.3.5
- Gradle
- Spring MVC
- Spring Data JPA
- Spring Security + JWT
- Redis
- MySQL
- OpenAI API 연동

성능 고도화의 우선순위는 다음과 같다.

1. **P1 — Chatbot**
   - GPT를 이용한 의도 분류
   - 일정·투두 생성안 생성
   - Redis 임시 저장
   - 사용자의 수락 이후 일정·투두 DB 반영
2. **P2 — Group concurrency**
   - 그룹 초대 중복 요청
   - 동일 초대의 동시 수락
   - 동일 초대의 수락·거절 경합
   - 그룹 멤버 중복 생성 및 상태 일관성

성능 분석과 개선은 이 두 범위를 우선하며, 다른 기능은 호출 경로에 포함될 때만 조사한다.

---

## 2. Required document order

모든 성능 관련 작업 전에 다음 문서를 순서대로 읽는다.

1. `docs/codex/00-project-overview.md`
2. `docs/codex/01-performance-scope.md`

Chatbot 작업이면 추가로 읽는다.

4. `docs/codex/chatbot/00-feature-overview.md`
5. `docs/codex/chatbot/01-test-scenarios.md`

Group 작업이면 추가로 읽는다.

4. `docs/codex/group/00-feature-overview.md`
5. `docs/codex/group/01-concurrency-scenarios.md`

실험 설계·실행·결과 분석이면 추가로 읽는다.

6. `docs/codex/performance/00-experiment-contract.md`
7. `docs/codex/performance/01-result-template.md`
8. `performance/plans/README.md`

문서와 로컬 소스 코드가 다르면 **로컬 소스 코드를 우선**한다.
차이가 발견되면 임의로 정리하지 말고 `Specification mismatch`로 보고한다.

현재 첨부 API 명세와 공개 저장소 코드 사이에는 그룹 초대 Endpoint 표기가 다를 수 있다.

- API 명세 예시: `/group-invitation/...`
- 공개 저장소 `develop` 코드 예시: `/groups-invitation/...`

JMeter 시나리오에는 반드시 현재 로컬 Controller annotation에서 확인한 실제 경로를 사용한다.

---

## 3. Classification rules

모든 분석 결과를 다음으로 구분한다.

- **FACT**: 코드, 설정 또는 측정값으로 직접 확인됨
- **HYPOTHESIS**: 병목 또는 동시성 오류 가능성이 있으나 측정이 필요함
- **UNKNOWN**: 현재 자료만으로 판단할 수 없음

정적 분석만으로 다음 표현을 사용하지 않는다.

- “실제 병목이다”
- “성능이 개선된다”
- “동시성 문제가 해결됐다”

대신 다음처럼 표현한다.

- “병목 후보이다”
- “동일 조건 부하 테스트로 검증해야 한다”
- “DB 제약조건과 동시 요청 결과를 확인해야 한다”

---

## 4. Approval gates

다음 결과는 먼저 채팅으로 제시하고 사용자 승인을 받은 뒤 파일로 저장하거나 코드를 수정한다.

1. 실제 호출 흐름과 코드 맵
2. 병목 가설 목록
3. 동시성 가설 목록
4. Actuator/Micrometer 변경 계획
5. Pinpoint 실행 계획
6. JMeter Test Plan Tree
7. DB 스키마·인덱스·제약조건 변경 계획
8. 성능 개선 코드 계획
9. 동시성 제어 방식 선택

승인 전에는 프로덕션 코드, 설정, DB 스키마를 수정하지 않는다.

---

## 5. P1 — Chatbot investigation rules

우선 조사할 Endpoint:

- `POST /chatbot/messages`
- `POST /chatbot/message?accept={accept}&isAlarmed={isAlarmed}`

반드시 실제 Controller의 annotation과 파라미터 바인딩을 확인한다.

우선 조사할 파일:

- `src/main/java/com/JAI/chatbot/controller/ChatbotController.java`
- `src/main/java/com/JAI/chatbot/service/ChatbotServiceImpl.java`
- `src/main/java/com/JAI/chatbot/service/ChatGPTServiceImpl.java`
- `src/main/java/com/JAI/chatbot/RedisChatbotUtil.java`
- `src/main/java/com/JAI/chatbot/mapper/ChatbotMapper.java`
- `src/main/java/com/JAI/config/OpenAIConfig.java`
- `src/main/java/com/JAI/config/RedisConfig.java`
- `src/main/java/com/JAI/global/util/RedisUtil.java`
- Chatbot이 호출하는 personal event, personal todo, category, alarm 서비스
- JWT filter와 사용자 인증 흐름
- `application*.yaml`
- `build.gradle`

다음 항목을 우선 검증한다.

### Chatbot latency candidates

- `/chatbot/messages` 한 요청에서 OpenAI API가 몇 번 호출되는지
- 의도 분류와 생성 요청이 직렬로 수행되는지
- OpenAI HTTP client의 connect/read timeout
- HTTP connection pooling 및 재사용 설정
- 외부 API 오류 시 retry 여부와 최대 지연
- Redis 요청당 GET/SET 횟수
- Redis read-modify-write 과정의 중복 조회
- JSON 추출·파싱 과정의 비용 및 실패 시 로그
- 요청 경로의 `System.out`, `printStackTrace`
- 생성 항목 수에 따른 DB 조회·INSERT 증가량
- GPT 호출 중 DB transaction 또는 connection을 점유하는지

### Chatbot correctness and concurrency candidates

- 동일 token으로 수락 API를 동시에 호출했을 때 일정·투두가 중복 생성되는지
- 수락 성공 후 token과 Redis 데이터가 확실히 소비·삭제되는지
- 거절 후 Redis key와 token mapping이 모두 삭제되는지
- accept와 reject가 동시에 호출됐을 때 최종 상태가 일관적인지
- 다중 일정·투두 저장 도중 일부 실패 시 부분 저장되는지
- idempotency key 또는 원자적 token consume가 필요한지

---

## 6. P2 — Group concurrency investigation rules

우선 조사할 Endpoint는 실제 로컬 Controller에서 확정한다.

공개 저장소 기준 후보:

- 그룹 초대 생성
- 그룹 초대 수락
- 그룹 초대 거절
- 그룹 멤버 조회·탈퇴·강퇴
- 그룹 생성

우선 조사할 파일:

- `GroupInvitationController`
- `GroupInvitationServiceImpl`
- `GroupInvitationRepository`
- `GroupInvitation`
- `GroupSettingServiceImpl`
- `GroupSettingRepository`
- `GroupSetting`
- `GroupServiceImpl`
- `AlarmService`
- 관련 DB migration 또는 schema
- MySQL index와 unique constraint

다음 동시성 시나리오를 우선 검증한다.

1. 같은 그룹·같은 사용자에게 초대 요청 N개 동시 전송
2. 같은 초대 ID를 N개 요청으로 동시 수락
3. 같은 초대 ID에 수락과 거절 동시 전송
4. 초대 수락과 같은 사용자의 멤버 등록 요청이 경합
5. 알림 삭제 실패 또는 중복 처리 시 상태 일관성

반드시 다음 DB 제약조건을 확인한다.

- `(group_id, user_id, status)` 또는 동등한 초대 중복 방지 제약
- `(group_id, user_id)` 그룹 멤버 unique 제약
- `@Version` 또는 optimistic locking 여부
- select-for-update 또는 pessimistic lock 여부

`exists` 확인 후 `save`하는 check-then-act 구조는
동시 요청에서 안전하다고 가정하지 않는다.

---

## 7. Test separation rules

Chatbot 테스트를 다음 세 종류로 분리한다.

### A. Mock OpenAI performance test

- OpenAI API를 WireMock 또는 동일 목적 stub으로 대체
- 고정 JSON 응답 사용
- 지연 시간을 외부 파라미터로 제어
- 애플리케이션, Redis, DB, thread pool 병목 검증
- 실제 API 비용 발생 금지

### B. Real OpenAI smoke test

- 동시 사용자 1~3명
- 실제 외부 지연, timeout, 오류 처리 확인
- 처리량 한계 측정 용도로 사용하지 않음

### C. Concurrency correctness test

- 처리량보다 최종 DB·Redis 상태를 우선 검증
- barrier 또는 동시 시작에 가까운 요청 구성
- 성공 응답 수뿐 아니라 중복 row와 상태 불변식 검증

Group 테스트는 C 유형을 우선하며,
필요하면 별도의 일반 부하 테스트를 추가한다.

---

## 8. Safety rules

다음을 수행하지 않는다.

- 운영 서버에 JMeter 실행
- 실제 OpenAI API에 대량 부하 전송
- 실제 사용자 개인정보를 CSV에 저장
- API key, JWT secret, DB password, Redis password 출력
- `.env`, `application-prod.*`, secret 파일 내용 출력
- 승인 없는 DB 초기화
- 승인 없는 `DROP`, `TRUNCATE`, 전체 `DELETE`
- 승인 없는 production dependency 추가
- 승인 없는 lock·transaction isolation·unique constraint 변경

테스트 데이터 정리는 테스트 전용 DB에서만 수행한다.

---

## 9. Experiment rules

개선 전후 다음 조건을 동일하게 유지한다.

- Git commit 또는 변경점 기록
- Java와 Spring profile
- 서버·DB·Redis 사양
- DB row 수와 데이터 분포
- JMeter thread 수
- ramp-up
- duration
- 요청 데이터
- OpenAI mock 응답과 지연
- Pinpoint sampling 설정
- warm-up 조건

주요 결과 지표:

- throughput
- error rate
- p50
- p95
- p99
- max
- CPU
- JVM heap
- GC pause
- Tomcat busy/max threads
- Hikari active/pending/max
- Redis elapsed 및 호출 수
- SQL elapsed 및 호출 수
- external HTTP elapsed
- 최종 DB·Redis 불변식

평균 응답 시간만으로 개선을 판단하지 않는다.

---

## 10. Code modification rules

- 한 번에 하나의 확인된 병목 또는 동시성 문제만 수정한다.
- 기존 API contract를 유지한다.
- 성능 향상을 위해 기능 정확성을 희생하지 않는다.
- 수정 전 대상 파일·메서드·부작용을 설명한다.
- 수정 후 기능 테스트를 추가한다.
- `./gradlew test`를 실행한다.
- 동일 JMeter 시나리오로 재측정하기 전까지 개선됐다고 단정하지 않는다.
- DB 제약조건을 추가하면 기존 중복 데이터 정리 전략도 제시한다.
- lock을 추가하면 lock 범위, timeout, deadlock 가능성을 설명한다.

---

## 11. Required response format

모든 분석 응답은 다음 순서로 작성한다.

1. 읽은 문서와 파일
2. 실제 Endpoint
3. 실제 호출 흐름
4. FACT
5. HYPOTHESIS
6. UNKNOWN
7. 검증할 JMeter 시나리오
8. Actuator에서 볼 지표
9. Pinpoint에서 볼 trace 구간
10. DB·Redis에서 확인할 최종 상태
11. 사용자 승인이 필요한 항목

파일 경로와 메서드 이름을 가능한 한 구체적으로 적는다.
