# Codex Workflow — PlanWith Performance & Concurrency

## 0. 공통 원칙

Codex는 모든 단계에서 아래 우선순위를 따른다.

1. 현재 체크아웃된 로컬 코드
2. 로컬 DB 스키마·Entity·Repository 제약조건
3. `application*.yml`, Dockerfile, 실행 스크립트 등 실제 실행 설정
4. `AGENTS.md`와 `docs/codex/*`
5. 첨부된 API·기능 설명
6. 원격 저장소 문서

문서와 코드가 충돌하면 **코드를 기준으로 판단**하고 불일치를 별도로 보고한다.

모든 분석 결과는 다음 중 하나로 표시한다.

- **FACT**: 코드·설정·스키마에서 직접 확인됨
- **HYPOTHESIS**: 정황상 가능성이 있으나 실행 검증 필요
- **UNKNOWN**: 현재 자료만으로 확인 불가

공통 제한:

- 현재 Goal이 허용하지 않는 애플리케이션 코드, DB 스키마, 설정 파일은 수정하지 않는다.
- secret 값은 출력하지 않는다. 필요한 경우 환경변수 이름과 파일 경로만 보고한다.
- 운영 데이터와 실제 OpenAI API를 대상으로 대규모 부하를 발생시키지 않는다.
- 실제 OpenAI 호출은 1~3 users smoke test로 제한하고, 고부하는 mock/stub 환경을 사용한다.
- JMeter GUI는 시나리오 검증에만 사용하고 실제 실행은 CLI 모드로 설계한다.
- 성능 비교 시 baseline/after의 환경, 데이터, thread, ramp-up, duration이 동일해야 한다.


### 실행 모델과 승인 게이트

이 문서는 다음 Goal로 나누어 실행한다.

1. **Analysis Goal**: Phase 1~7. 정적 분석, 계획 문서, 테스트 하네스와 mock dry run까지 수행한다.
2. **Baseline Goal**: Phase 8A~8B. 애플리케이션 코드를 바꾸지 않고 공통 baseline을 측정한다.
3. **Per-Experiment Goal**: Phase 9~10. 한 번에 하나의 experiment만 별도 worktree에서 개선하고 비교한다.
4. **Final Summary**: Phase 11. 완료된 실험만 종합한다.

다음 작업은 Goal 안에서 별도 승인 없이 진행할 수 있다.

- 코드 정적 분석
- 문서와 `performance/plans/**` 파일 생성
- 테스트 하네스, mock, fixture, JMX, 수집·검증·cleanup 스크립트 생성
- 격리된 테스트 환경의 1-user dry run
- 테스트 환경의 measured baseline과 결과 정리

다음 작업 직전에는 멈춘다.

- Analysis Goal 중 애플리케이션 동작 코드 변경
- DB schema 또는 migration 변경
- 운영 환경 대상 실행
- 실제 OpenAI 고부하 실행
- 비용이 발생하는 인프라 생성
- 원격 push·PR·merge
- 여러 experiment 변경 통합

Per-Experiment Goal을 사용자가 시작한 행위 자체를 해당 experiment 범위의 최소 애플리케이션 코드 수정 승인으로 본다. 별도의 중복 승인 없이 수정과 검증을 계속하되, 해당 experiment 범위를 벗어나는 변경은 금지한다.

워크플로가 명시하는 로컬 commit은 허용한다. 로컬 commit은 재현 가능한 경계를 고정하기 위한 것이며 원격 push, PR 생성, 원격 merge는 항상 금지한다.

입력 파일 계약:

- 필수 입력이 없으면 해당 Phase를 `BLOCKED`로 기록하고 진행하지 않는다.
- 선택 입력은 존재할 때만 읽는다.
- 선택 입력이 없으면 해당 항목을 `NOT_APPLICABLE`로 기록하고 계속한다.
- 각 Phase는 완료 시 선언된 Outputs를 실제로 생성하고 `performance/state.md`를 갱신한다.

### Git commit과 artifact 정책

Git tracked 대상:

- `AGENTS.md`
- `docs/codex/**`
- `performance/plans/**`
- `performance/harness/**`의 JMX, 스크립트, 설정
- 작은 크기의 manifest
- `baseline-plan.md`, `improvement-plan.md`, `summary.md`, `comparison.md`
- experiment state 파일

Git에서 제외할 대상:

- JMeter raw JTL과 전체 HTML report
- Actuator JSONL raw time series
- application raw log
- Pinpoint raw export
- 임시 CSV와 screenshot
- JWT 또는 secret이 포함된 파일
- 대용량 임시 측정 파일과 실행 중 temporary 파일

raw artifact는 결과 경로에 보존하되 commit하지 않는다. 각 raw artifact의 상대 경로, SHA-256, 크기, 생성 시각과 검증 상태는 해당 run의 `artifact-manifest.json`에 기록한다.

commit 순서와 책임:

1. `HARNESS_COMMIT`: 테스트 하네스와 dry-run 검증 구조를 고정한다.
2. `BASELINE_COMMIT`: Phase 8A까지의 실행 가능한 애플리케이션, 하네스와 계획을 첫 measured baseline 전에 고정한다.
3. `BASELINE_EVIDENCE_COMMIT`: baseline summary, manifest와 state만 포함한다. execution-affecting 파일을 변경하지 않는다.
4. `EXP_PLAN_COMMIT`: 해당 experiment의 `improvement-plan.md`를 애플리케이션 변경 전에 고정한다.
5. `IMPROVEMENT_COMMIT`: 해당 experiment의 애플리케이션과 관련 테스트 변경만 포함한다.
6. `REPORT_COMMIT`: after summary, artifact manifest와 comparison 문서만 포함하며 애플리케이션 변경을 포함하지 않는다. Phase 9 종료 시 report-only commit을 만들고 Phase 10에서 comparison을 추가한 새 report-only commit을 만들 수 있으며, state의 `reportCommit`은 최신 SHA를 가리킨다.

최종 integration branch에는 원칙적으로 각 experiment의 `IMPROVEMENT_COMMIT`만 적용한다. raw artifact, report commit 또는 다른 experiment의 commit을 자동 merge하지 않는다.

Git commit SHA를 그 commit 내부 파일에 자기 참조로 기록할 수는 없다. 따라서 `HARNESS_COMMIT`과 `BASELINE_COMMIT`은 각각 검증 대상 content snapshot의 SHA이고, 그 SHA를 기록한 manifest와 state는 바로 다음 metadata-only local commit에 저장한다. metadata-only commit은 execution-affecting 파일을 변경할 수 없으며 원래 content commit의 의미를 바꾸지 않는다.

이 문서에서 clean working tree는 다음을 뜻한다.

- uncommitted tracked 변경이 없음
- ignore된 raw 측정 파일은 존재할 수 있음
- secret과 JWT 파일은 commit 대상이 아님
- 다른 experiment의 tracked 변경이 없음

### 상태 전이와 실패 처리

Phase 상태 전이는 다음만 허용한다.

- `NOT_STARTED → IN_PROGRESS`
- `IN_PROGRESS → COMPLETED`
- `IN_PROGRESS → BLOCKED`
- `IN_PROGRESS → FAILED`
- `NOT_STARTED → SKIPPED`
- `NOT_STARTED → NOT_APPLICABLE`
- `BLOCKED → IN_PROGRESS`: blocker 해결 근거가 기록된 경우
- `FAILED → IN_PROGRESS`: cleanup 완료, 새 runId 발급과 retryCount 증가 후
- `SKIPPED → IN_PROGRESS`: 사용자가 명시적으로 재개한 경우

`NOT_APPLICABLE`은 코드 또는 범위가 변경되지 않는 한 terminal 상태다. `FAILED`, `SKIPPED`, `NOT_APPLICABLE`에는 reason과 timestamp를 반드시 기록한다.

baseline verdict는 `NOT_RUN`, `RUNNING`, `CONFIRMED`, `PARTIALLY_CONFIRMED`, `DISPROVED`, `INCONCLUSIVE`, `FAILED`, `INVALIDATED`만 사용한다.

cleanup status는 `NOT_REQUIRED`, `PENDING`, `IN_PROGRESS`, `COMPLETED`, `FAILED`만 사용한다. rollback status는 `NOT_REQUIRED`, `READY`, `IN_PROGRESS`, `COMPLETED`, `FAILED`만 사용한다.

retry 규칙:

1. 실패한 runId를 재사용하지 않는다.
2. 실패 artifact를 삭제하지 않고 `failed/<runId>`에 보존한다.
3. 데이터가 생성된 실행은 cleanup 성공 전 재시도하지 않는다.
4. 자동 재시도는 transient failure에 한해 최대 1회다.
5. 두 번째 실패 또는 원인 불명 실패는 `BLOCKED`로 전환한다.
6. retryCount, 실패 원인과 변경된 조건을 기록한다.
7. 테스트 조건 변경은 retry가 아니라 새로운 baseline generation으로 처리한다.

cleanup 실패 시 다음 run을 실행하지 않고 experiment를 `BLOCKED`로 전환하며 남은 DB·Redis 데이터와 수동 복구 방법을 기록한다. rollback 실패 시 after run과 integration을 진행하지 않고 experiment를 `BLOCKED`로 전환한다. destructive reset은 자동 수행하지 않는다.

### Completion 검증 공통 규칙

Phase 1~5는 필수 output 파일 존재, 0보다 큰 파일 크기, 필수 heading, `FACT`/`HYPOTHESIS`/`UNKNOWN` 분류와 입력 파일 경로 기록을 확인해야 완료할 수 있다.

Phase 6.5 이전에는 문서 기반 사전 검증을 사용한다. Phase 6.5에서 다음 검증 스크립트를 생성한 이후에는 해당 Phase의 validation command가 exit code 0이어야 완료할 수 있다.

- `performance/harness/bin/validate-plans.sh`
- `performance/harness/bin/validate-harness.sh`
- `performance/harness/bin/validate-run-artifacts.sh`
- `performance/harness/bin/validate-comparison.sh`
- `performance/harness/bin/scan-secrets.sh`

### 상태 파일

`performance/state.md`는 workflowVersion, 전역 진행 위치, baseline generation, Phase별 상태와 experiment별 baseline·Git·improvement·after·comparison·cleanup·rollback 상태를 유지한다. 각 experiment의 진행 위치와 판정은 이 파일만 읽어도 복원할 수 있어야 한다.

허용 상태:

- `NOT_STARTED`
- `IN_PROGRESS`
- `BLOCKED`
- `COMPLETED`
- `FAILED`
- `SKIPPED`
- `NOT_APPLICABLE`

각 Phase 시작과 종료 시 `performance/state.md`를 갱신한다.

---

> 이번 워크플로의 그룹 기능 범위는 **Group Event CRUD**이다. Group Invitation(그룹 초대) 기능은 이번 분석과 실험 범위에서 제외한다.

---

## Phase 1 — Context Verification

### Inputs

- `AGENTS.md`
- `docs/codex/planwith-performance-workflow.md`
- 현재 체크아웃된 로컬 코드와 실행 설정

### Preconditions

- 저장소 루트에서 작업 중이어야 한다.
- 현재 branch와 commit을 확인할 수 있어야 한다.

### Allowed changes

- `performance/plans/context-verification.md`
- `performance/state.md`

### Forbidden changes

- 애플리케이션 코드, 테스트 코드, DB 스키마, 설정 파일

### Outputs

- `performance/plans/context-verification.md`

### Completion criteria

- 실제 Chatbot 및 Group Event CRUD endpoint, DTO, 인증, Redis, 실행 방식이 분류됨
- 각 결론이 `FACT`, `HYPOTHESIS`, `UNKNOWN`으로 표시됨

### Blocked conditions

- 저장소 코드 또는 설정 파일을 읽을 수 없음
- 현재 branch·commit을 확인할 수 없음


AGENTS.md와 docs/codex 문서를 지정된 순서로 읽어라.
허용된 출력 파일 외에는 수정하지 마라.

현재 체크아웃된 로컬 branch와 실제 코드를 기준으로 다음을 채팅에 보고하라.

1. Spring Boot, Java, Gradle 버전
2. 실제 실행 방식
    - Docker 여부
    - Dockerfile ENTRYPOINT
    - 활성 profile 구조
    - runtime env 파일 경로
3. 실제 Chatbot Endpoint
    - 메시지 생성
    - 일정/투두 수락 또는 거절
4. 실제 Group Event CRUD Endpoint
    - 그룹 일정 생성
    - 그룹 일정 단건 조회
    - 그룹 일정 목록 조회
    - 그룹 일정 수정
    - 그룹 일정 삭제
5. Controller 기준 request/response DTO
6. JWT 인증 방식과 토큰 전달 위치
7. Redis가 사용되는 기능과 key 구조
8. 첨부 API 명세·기능 설명과 코드의 불일치
9. 테스트 실행 명령
10. secret 파일은 값을 출력하지 말고 경로와 환경변수 이름만 보고

각 항목을 FACT/HYPOTHESIS/UNKNOWN으로 구분하라.

결과를 `performance/plans/context-verification.md`에 저장하고 `performance/state.md`를 갱신한다.

---

## Phase 2 — Chatbot Code Map

### Inputs

- `performance/plans/context-verification.md`
- Chatbot 관련 Controller, Service, HTTP client, Redis, Repository 코드

### Preconditions

- Phase 1이 `COMPLETED` 상태여야 한다.

### Allowed changes

- `performance/plans/chatbot-code-map.md`
- `performance/state.md`

### Forbidden changes

- 애플리케이션 코드, 설정, DB 스키마

### Outputs

- `performance/plans/chatbot-code-map.md`

### Completion criteria

- 메시지 생성, 수락, 거절 흐름이 실제 annotation과 코드 근거로 끝까지 추적됨
- 외부 HTTP, Redis, SQL, transaction, 예외 처리 위치가 정리됨

### Blocked conditions

- 실제 endpoint 또는 핵심 호출 흐름을 찾을 수 없음


PlanWith Chatbot의 핵심 범위:

- 사용자 메시지 수신
- OpenAI Responses API 호출
- 응답 JSON 파싱 및 일정/투두 후보 생성
- Redis 임시 저장 및 token 관리
- 사용자 수락/거절
- PersonalEvent 또는 PersonalTodo 영속화
- 알림 생성 여부 처리

P1 Chatbot 호출 흐름을 정적 분석하라.
아직 파일과 코드를 수정하지 마라.

반드시 실제 Controller annotation을 기준으로 아래 흐름을 각각 분리하라.

A. Chatbot 메시지 생성 요청
B. 생성 결과 수락 요청
C. 생성 결과 거절 요청

Controller부터 다음 계층을 끝까지 추적하라.

Controller
→ 인증 사용자 조회
→ Service
→ OpenAI HTTP Client
→ 응답 파싱/정규화
→ Redis token 저장·조회·삭제
→ Event/Todo Mapper 또는 변환 로직
→ PersonalEvent/PersonalTodo 저장
→ Alarm 생성

각 단계에 대해 다음을 표로 작성하라.

- 파일 경로
- 클래스와 메서드
- 입력과 출력
- 외부 I/O
- 예상 Redis 명령과 호출 횟수
- 예상 SQL 종류와 호출 횟수
- transaction 시작·종료 위치
- 예외 처리
- 중복 요청 처리 방식
- FACT/HYPOTHESIS/UNKNOWN

추가로 아래를 확인하라.

1. OpenAI 요청이 한 사용자 요청 안에서 몇 번 발생하는지
2. RestTemplate/WebClient 생성 방식
3. connect/read timeout
4. connection pooling 여부
5. Redis token TTL
6. token consume/delete 순서
7. accept=false일 때 Redis 상태
8. 일정과 투두가 여러 개일 때 저장 순서
9. 중간 저장 실패 시 rollback 범위
10. OpenAI 응답 JSON 추출 방식과 정규식 사용 여부

결과를 `performance/plans/chatbot-code-map.md`에 저장하고 `performance/state.md`를 갱신한다.

---

## Phase 3 — Chatbot Bottleneck & Correctness Hypotheses

### Inputs

- `performance/plans/chatbot-code-map.md`

### Preconditions

- Phase 2가 `COMPLETED` 상태여야 한다.

### Allowed changes

- `performance/plans/chatbot-bottleneck-hypotheses.md`
- `performance/state.md`

### Forbidden changes

- 개선 코드, 설정 변경, 테스트 실행

### Outputs

- `performance/plans/chatbot-bottleneck-hypotheses.md`

### Completion criteria

- 각 가설에 hypothesisId가 부여됨
- 코드 근거, 재현법, 지표, invariant, 판정 기준이 연결됨

### Blocked conditions

- code map의 근거가 불완전해 재현 가능한 가설을 만들 수 없음


완료된 Chatbot code map을 기반으로 성능 병목과 정확성 가설을 작성하라.
아직 코드를 수정하지 마라.

반드시 다음 가설을 검토하라.

- OpenAI 순차 또는 중복 호출
- HTTP client timeout 부재
- connection pooling 부재
- Redis 동일 key 중복 조회
- GET 후 DELETE 형태의 비원자적 token 소비
- 동일 token 동시 수락
- 동일 요청 재전송 시 중복 Event/Todo 생성
- 다중 Event/Todo 순차 save
- saveAll 가능 여부
- Alarm 생성까지 포함된 긴 transaction
- 일부 항목 저장 후 실패하는 partial failure
- OpenAI 응답 JSON 정규식 추출 실패
- 민감 정보 또는 전체 응답 body 로그 기록
- 외부 API 장애가 Tomcat thread를 오래 점유하는 문제

각 가설마다 아래를 작성하라.

1. 근거가 되는 코드
2. 재현 시나리오
3. JMeter 또는 통합 테스트 방법
4. 관측할 JMeter 지표
5. 관측할 Actuator 지표
6. 관측할 Pinpoint trace
7. DB/Redis invariant
8. 성공·실패 판정 기준
9. 운영 위험도
10. 우선순위

결과를 `performance/plans/chatbot-bottleneck-hypotheses.md`에 저장하고 `performance/state.md`를 갱신한다.

---

## Phase 4 — Group Event CRUD and Concurrency Analysis

Phase 4는 실제 CRUD 흐름을 먼저 확정하는 Phase 4A와, 확인된 구조에서 적용 가능한 race만 도출하는 Phase 4B로 나눈다. Group Invitation은 두 단계 모두의 범위 밖이다.

## Phase 4A — Group Event CRUD Code Map

### Inputs

- `performance/plans/context-verification.md`
- Group Event 관련 Controller, Service, Entity, Repository, Security 코드

### Preconditions

- Phase 1이 `COMPLETED` 상태여야 한다.

### Allowed changes

- `performance/plans/group-event-crud-code-map.md`
- `performance/state.md`

### Forbidden changes

- 애플리케이션 코드, DB 스키마, locking 코드

### Outputs

- `performance/plans/group-event-crud-code-map.md`

### Completion criteria

- 생성·단건 조회·목록 조회·수정·삭제 흐름이 복원됨
- GroupEvent, Mapping, Alarm 관계와 권한 구조가 확인됨

### Blocked conditions

- Group Event 구현 또는 권한 검증 코드를 찾을 수 없음


P2 Group Event CRUD 기능의 실제 흐름과 동시성 문제 가능성을 분석하라.
허용된 출력 파일 외에는 수정하지 마라.

이 기능에 동시성 문제가 존재한다고 미리 가정하지 마라.

먼저 실제 Controller, Service, Entity, Repository,
Security, 그룹 멤버 권한 검증 코드를 기준으로
현재 그룹 일정 CRUD 기능의 구조를 복원하라.

분석 범위:

- 그룹 일정 생성
- 그룹 일정 단건·목록 조회
- 그룹 일정 수정
- 그룹 일정 삭제
- 그룹 일정과 사용자·그룹 사이의 mapping
- 그룹 일정 관련 알림 생성·수정·삭제
- 그룹 일정 참여자 또는 멤버별 데이터 처리

### 1. 실제 기능 플로우 복원

각 CRUD 기능을 다음 순서로 추적하라.

HTTP Endpoint
→ Controller
→ 인증 사용자 확인
→ 그룹 멤버 및 권한 검증
→ Service
→ GroupEvent 조회·생성·수정·삭제
→ GroupEventMapping 처리
→ Alarm 처리
→ Repository 및 DB 반영

각 단계에 대해 다음을 표로 작성하라.

- HTTP method와 실제 endpoint
- request/response DTO
- Controller 파일과 메서드
- Service 파일과 메서드
- Repository 호출
- Entity 변경 사항
- SQL 예상 종류와 횟수
- transaction 시작·종료 위치
- 예외 처리 방식
- FACT/HYPOTHESIS/UNKNOWN

### 2. 데이터 공유 구조 확인

다음 내용을 코드 근거와 함께 확인하라.

1. 하나의 그룹 일정이 GroupEvent 단일 row로 공유되는가
2. 그룹 멤버마다 별도의 일정 row 또는 mapping row가 생성되는가
3. GroupEvent와 GroupEventMapping의 관계
4. 일정 작성자, 그룹장, 일반 멤버의 수정·삭제 권한
5. 여러 그룹원이 같은 GroupEvent ID를 수정할 수 있는가
6. 여러 그룹원이 같은 GroupEvent ID를 삭제할 수 있는가
7. 일정 수정 시 기존 Entity를 조회한 뒤 dirty checking으로 변경하는가
8. 일정 삭제가 hard delete인지 soft delete인지
9. 일정 삭제 시 mapping과 alarm이 cascade 또는 직접 삭제되는가
10. 그룹 일정 수정이 개인별 일정·알림 상태에도 영향을 주는가

Phase 4A 결과를 `performance/plans/group-event-crud-code-map.md`에 저장하고 `performance/state.md`를 갱신한다.

---

## Phase 4B — Group Event Concurrency Hypotheses

### Inputs

- `performance/plans/group-event-crud-code-map.md`
- 현재 체크아웃된 Group Event 관련 코드와 DB 제약조건

### Preconditions

- Phase 4A가 `COMPLETED` 상태여야 한다.
- Group Event의 실제 CRUD 흐름과 권한 구조가 확인되어야 한다.

### Allowed changes

- `performance/plans/group-event-concurrency-hypotheses.md`
- `performance/state.md`

### Forbidden changes

- 애플리케이션 코드
- DB migration 또는 제약조건
- locking 코드
- JMeter JMX

### Outputs

- `performance/plans/group-event-concurrency-hypotheses.md`

### Completion criteria

- 모든 후보 race가 `APPLICABLE`, `NOT_APPLICABLE`, `UNKNOWN` 중 하나로 분류됨
- 적용 가능한 race에 코드 근거, interleaving, 예상 최종 상태, invariant가 연결됨
- 우선 테스트할 시나리오가 최대 3개로 제한됨

### Blocked conditions

- 권한 구조나 데이터 관계를 코드에서 확인할 수 없음
- DB 제약조건을 확인할 수 없어 적용 가능성을 판정할 수 없음

### 3. 동시 조작 가능 여부 판단

아래 질문에 먼저 답하라.

- 현재 API와 권한 구조상 여러 그룹원이 같은 일정을 동시에 수정할 수 있는가
- 같은 일정을 수정하는 동안 다른 사용자가 삭제할 수 있는가
- 같은 일정에 대해 두 번의 삭제 요청이 동시에 들어올 수 있는가
- 동일하거나 유사한 그룹 일정이 동시에 중복 생성될 수 있는가
- 한 사용자의 수정이 다른 사용자의 변경 내용을 덮어쓸 수 있는가
- 현재 구조상 동시성 검증이 실제로 필요한 기능인가

각 항목을 다음 중 하나로 판정하라.

- APPLICABLE: 현재 구조상 실제 발생 가능
- NOT_APPLICABLE: 권한 또는 데이터 구조상 발생 불가
- UNKNOWN: 추가 실행 검증 또는 데이터 확인 필요

판정에는 반드시 코드 근거를 포함하라.

### 4. 동시성 제어 구현 여부 확인

다음 항목을 실제 코드와 DB 제약조건에서 확인하라.

- @Transactional 위치와 범위
- transaction isolation level
- Entity의 @Version optimistic locking
- PESSIMISTIC_WRITE 또는 repository lock
- native query 또는 update query 사용 여부
- save 전 exists/find 검사 구조
- unique constraint
- DB index
- affected row count 검증
- 삭제 대상 재조회 여부
- ObjectOptimisticLockingFailureException 처리
- DataIntegrityViolationException 처리
- 이미 삭제된 일정 수정 시 처리 방식
- 이미 수정된 일정에 대한 stale request 처리 방식
- 예외 발생 시 GroupEvent, Mapping, Alarm 전체 rollback 여부

### 5. 후보 Race Condition 검토

아래 시나리오를 무조건 문제로 확정하지 말고,
현재 코드 구조상 발생 가능한 경우에만 분석 대상으로 선정하라.

E1. 동일 그룹 일정에 대한 동시 수정
- 사용자 A와 B가 같은 기존 값을 조회
- A와 B가 서로 다른 내용으로 수정
- 마지막 commit이 이전 변경을 덮어쓰는 lost update 가능성

E2. 동일 그룹 일정에 대한 수정과 삭제 경합
- 사용자 A가 일정을 수정
- 사용자 B가 같은 일정을 삭제
- 수정 성공 응답 후 실제 row가 삭제되거나
  삭제 중 연관 mapping·alarm 처리와 충돌할 가능성

E3. 동일 그룹 일정에 대한 중복 삭제
- 동일한 GroupEvent ID에 두 개의 삭제 요청
- 한 요청 성공 후 다른 요청의 예외·멱등성 처리

E4. 동일 그룹에 유사 일정 동시 생성
- 동일 사용자가 요청을 재전송
- 서로 다른 그룹원이 동일한 일정 정보를 동시에 생성
- 업무적으로 중복을 막아야 하는 기능인지 여부

E5. 일정 변경과 Alarm·Mapping 변경의 경합
- GroupEvent 수정 또는 삭제 중
  GroupEventMapping이나 Alarm이 별도 transaction에서 변경되는지
- 부분 반영 또는 고아 데이터 발생 가능성

E6. 그룹 멤버 상태 변경과 일정 CRUD의 경합
- 일정 수정·삭제 도중 사용자가 그룹에서 탈퇴하거나 강퇴되는 경우
- 권한 검증 시점과 실제 commit 시점 사이의 상태 변경 가능성

각 적용 가능한 시나리오에 대해 다음을 작성하라.

1. 사전 조건
2. 요청 주체
3. 가능한 실행 순서 interleaving
4. 현재 코드에서 예상되는 결과
5. 발생 가능한 문제
6. 최종 GroupEvent 상태
7. 최종 GroupEventMapping 상태
8. 최종 Alarm 상태
9. 응답 HTTP status
10. 코드 근거
11. FACT/HYPOTHESIS/UNKNOWN

### 6. DB 최종 상태와 Invariant

적용 가능한 race마다 가능한 최종 DB 상태를 표로 작성하라.

반드시 다음 invariant를 검토하라.

- 존재하는 GroupEventMapping은 유효한 GroupEvent를 참조해야 한다
- 존재하는 Alarm은 유효한 GroupEvent 또는 mapping을 참조해야 한다
- 삭제된 일정의 mapping이나 alarm이 남아서는 안 된다
- 권한이 없는 사용자의 수정·삭제가 반영돼서는 안 된다
- 하나의 수정 요청이 성공했다고 응답한 뒤 변경 내용이 조용히 유실돼서는 안 된다
- partial update 또는 partial delete가 발생해서는 안 된다
- 동일 요청 재전송 시 데이터가 비정상적으로 중복 생성돼서는 안 된다

### 7. 최종 판단

분석 결과를 다음 세 영역으로 나눠 보고하라.

A. 현재 그룹 일정 CRUD 기능의 실제 플로우
B. 실제로 발생 가능한 동시성 문제
C. 현재 구조에서는 발생하기 어려운 가정상의 문제

마지막에는 다음 결론 중 하나를 선택하라.

1. 그룹 일정 CRUD에 동시성 테스트가 반드시 필요함
2. 일부 수정·삭제 시나리오에만 동시성 테스트가 필요함
3. 현재 권한·데이터 구조에서는 동시 조작 가능성이 낮음
4. 자료가 부족해 추가 확인이 필요함

결론과 함께 우선 테스트할 시나리오를 최대 3개까지만 제안하라.

아직 JMeter JMX, 테스트 코드, DB migration,
locking 코드 또는 애플리케이션 코드를 생성하지 마라.
결과를 `performance/plans/group-event-concurrency-hypotheses.md`에 저장하고 `performance/state.md`를 갱신한다.

---

## Phase 5 — Observability Readiness Gate

### Inputs

- `performance/plans/context-verification.md`
- 현재 build, profile, Docker, Actuator, Pinpoint 설정과 실행 자료

### Preconditions

- 테스트 환경이 운영 환경과 구분되어야 한다.

### Allowed changes

- `performance/plans/observability-checklist.md`
- `performance/state.md`

### Forbidden changes

- 애플리케이션 코드와 실행 설정 직접 수정

### Outputs

- `performance/plans/observability-checklist.md`

### Completion criteria

- Actuator, Pinpoint, 네트워크·보안, baseline 비교 준비도가 판정됨
- 최종 상태가 `READY_FOR_BASELINE`, `PARTIALLY_READY`, `NOT_READY` 중 하나임

### Blocked conditions

- 필수 Actuator 또는 실행 환경 자료에 접근할 수 없어 readiness를 판정할 수 없음
- Pinpoint evidence가 해당 experiment의 필수 판정 근거인데 Web, screenshot, export 또는 summary 어느 것도 제공되지 않음

Pinpoint가 필수 판정 근거가 아닌 경우 접근 불가는 Phase 전체 blocker가 아니다. 해당 항목을 `UNKNOWN`으로 기록하고 결과를 `PARTIALLY_READY`로 판정할 수 있다.


현재 기준 환경:

- Spring Boot Actuator/Micrometer 적용
- `dev-perf` profile group 사용
- 애플리케이션 API port: `8080`
- Actuator management port: `9091`
- Docker host binding: `127.0.0.1:9091`
- Pinpoint Agent 및 Collector 연결 예정 또는 적용 완료

현재 build.gradle, application.yaml, application-dev.yaml,
application-perf.yml, Dockerfile, Docker 실행 방식을 확인하라.

아직 애플리케이션 코드와 설정을 수정하지 마라.

목표는 baseline/after 비교 실험을 시작할 수 있는 상태인지 판정하는 것이다.

### 1. Actuator/Micrometer 검증

다음 metric이 실제 /actuator/metrics 목록에 존재하는지 확인하라.

- http.server.requests
- process.cpu.usage
- jvm.memory.used
- jvm.gc.pause
- hikaricp.connections.active
- hikaricp.connections.pending
- hikaricp.connections.timeout
- tomcat.threads.busy
- tomcat.threads.current

각 metric을 다음으로 분류하라.

- READY: 실제 endpoint 응답 확인
- NOT_READY: 설정 또는 실행 변경 필요
- NOT_APPLICABLE: 현재 애플리케이션 구조에서는 생성되지 않음
- UNKNOWN: 실행 환경 접근 필요

http.server.requests는 실제 API 요청을 1건 발생시킨 뒤
대상 URI와 method 태그가 생성되는지도 확인하라.

### 2. Pinpoint 검증

다음 trace가 실제로 확인 가능한지 검증하라.

- Tomcat request
- Controller
- Service
- JPA/Hibernate
- MySQL query
- Redis command
- OpenAI 외부 HTTP 요청
- 예외 및 timeout

각 항목을 다음으로 분류하라.

- TRACEABLE
- PARTIALLY_TRACEABLE
- NOT_TRACEABLE
- UNKNOWN

Codex가 Pinpoint Web에 직접 접근할 수 없다면
확인됐다고 추정하지 마라.

사용자가 제공한 다음 자료만 근거로 판단하라.

- Pinpoint screenshot
- trace export
- trace ID
- 사용자가 작성한 trace-summary.md
- Agent/Collector 로그

### 3. 네트워크 및 보안 검증

다음을 확인하라.

- Actuator가 EC2 localhost에만 publish되는지
- Pinpoint Web이 SSH tunnel 또는 제한된 보안 그룹으로 접근되는지
- Collector 9991, 9992, 9993이 PlanWith 서버에만 허용되는지
- runtime.env와 secret 값이 로그에 출력되지 않는지
- 실제 OpenAI request/response body가 불필요하게 기록되지 않는지

### 4. 비교 실험 가능 여부 판정

아래 조건을 모두 만족해야 READY로 판정하라.

- Actuator 주요 metric 조회 가능
- 5초 단위 수집 스크립트 실행 가능
- Pinpoint에 단일 API trace가 표시됨
- JMeter 요청과 trace를 testRunId 또는 시간 구간으로 연결 가능
- baseline과 after에서 같은 관측 설정을 사용할 수 있음

최종 판정:

- READY_FOR_BASELINE
- PARTIALLY_READY
- NOT_READY

부족한 항목이 있다면 최소 변경만 제안하라. 아직 직접 수정하지 마라.

결과를 `performance/plans/observability-checklist.md`에 저장하고 `performance/state.md`를 갱신한다.


---

여기서는 기존의 그룹 초대 시나리오를 빼고, 앞에서 분석한 **그룹 일정 CRUD 동시성 후보만 조건부로 포함**해야 해.

## Phase 6 — JMeter Test Design

### Inputs

- `performance/plans/context-verification.md`
- `performance/plans/chatbot-code-map.md`
- `performance/plans/chatbot-bottleneck-hypotheses.md`
- `performance/plans/group-event-crud-code-map.md`
- `performance/plans/group-event-concurrency-hypotheses.md`
- `performance/plans/observability-checklist.md`

### Preconditions

- Phase 5 결과가 최소 `PARTIALLY_READY` 이상이어야 한다.

### Allowed changes

- `performance/plans/jmeter-test-design.md`
- `performance/state.md`

### Forbidden changes

- JMX와 실행 스크립트 생성
- 애플리케이션 코드 변경

### Outputs

- `performance/plans/jmeter-test-design.md`

### Completion criteria

- 각 시나리오가 hypothesisId와 연결됨
- 실제 endpoint, DTO, 인증, 데이터 격리, assertion, invariant가 정의됨
- Group Event는 Phase 4B에서 `APPLICABLE`인 시나리오만 포함됨
- 모든 시나리오에 `scenarioId`, `hypothesisId`, endpoint와 method, assertion, invariant, cleanup, OpenAI mode와 risk가 존재함
- `performance/harness/bin/validate-plans.sh`가 생성되기 전에는 동일 항목을 문서 체크리스트로 검증함

### Blocked conditions

- 필수 planning 문서가 없음
- 실제 요청 형식과 인증 방법을 확정할 수 없음


### Chatbot 후보 시나리오

- C1 Chatbot message with mocked OpenAI
- C2 Real OpenAI Compatibility Smoke (`SMOKE_ONLY`)
- C3 Chatbot accept single request
- C4 Chatbot duplicate accept concurrency
- C5 Chatbot multi-item accept
- C6 Chatbot reject and token cleanup

### Group Event 후보 시나리오

아래 시나리오는 Phase 4에서 APPLICABLE로 판정된 경우에만 포함한다.

- E1 Concurrent update of the same GroupEvent
- E2 Update versus delete race
- E3 Double delete of the same GroupEvent
- E4 Duplicate creation in the same group
- E5 Event update/delete versus Mapping or Alarm processing
- E6 Group membership change versus GroupEvent CRUD

### C2 Real OpenAI Compatibility Smoke 제한

- 목적은 실제 OpenAI API 연결과 응답 호환성의 최소 확인이다.
- 사용자는 최대 1~3명이며 동시성 barrier와 고부하를 금지한다.
- 비용과 rate limit은 성능 결과로 해석하지 않는다.
- selected experiment 후보, resume-strength 평가, measured baseline, baseline/after 성능 비교, throughput 비교와 p95/p99 개선 판정에서 제외한다.
- 결과는 선택적으로 `performance/results/smoke/openai-real/<runId>/**`에 저장한다.
- 실패는 mock 기반 baseline을 자동 무효화하지 않고 실제 API 호환성 blocker 또는 risk로 별도 기록한다.
- `useRealOpenAI=true`인 run은 measured baseline으로 실행할 수 없다.

완료된 다음 문서를 읽어라.

- performance/plans/context-verification.md
- performance/plans/chatbot-code-map.md
- performance/plans/chatbot-bottleneck-hypotheses.md
- performance/plans/group-event-crud-code-map.md
- performance/plans/group-event-concurrency-hypotheses.md
    - 파일이 실제로 존재하는 경우에만 읽는다.
- performance/plans/observability-checklist.md

아직 JMX 파일이나 애플리케이션 코드를 만들지 마라.

실제 Controller, DTO, Security, JWT, Redis,
GroupEvent, GroupEventMapping, Alarm 구현을 기준으로
JMeter Test Plan Tree를 채팅에 제시하라.

Group Event 시나리오는 Phase 4에서 APPLICABLE로 판정된 것만 포함하라.
NOT_APPLICABLE인 시나리오를 억지로 테스트 대상으로 만들지 마라.

각 시나리오마다 다음을 작성하라.

1. scenarioId
2. 연결되는 hypothesisId
3. 목적
4. 대상 endpoint와 HTTP method
5. request query/path/body
6. 사전 데이터
7. 인증 및 사용자별 JWT 준비 방식
8. 사용자별 데이터 격리 방식
9. Redis token, GroupEvent ID 등 식별자 추출 방식
10. 동시 시작 방식
11. Thread Group 설정
12. assertion
13. 허용 HTTP status
14. response JSON 필수 필드
15. DB 사후 invariant
16. Redis 사후 invariant
17. 데이터 정리 방식
18. 외부 JMeter property
19. Actuator 관측 지표
20. Pinpoint에서 확인할 span
21. 실제 OpenAI 사용 여부
22. 비용·운영 위험

동시성 시나리오는 일반 ramp-up만 사용하지 말고,
Synchronization Timer 또는 동등한 barrier를 사용해
요청이 최대한 동시에 시작되도록 설계하라.

필수 외부 파라미터:

- baseUrl
- threads
- rampUp
- duration
- testRunId
- connectTimeout
- responseTimeout
- usersCsv
- useRealOpenAI
- mockOpenAiDelayMs

설계 원칙:

- 사용자별 JWT와 test data를 분리
- testRunId로 실행 데이터를 식별
- 응답 ID는 JSON Extractor로 추출
- HTTP 응답뿐 아니라 DB/Redis invariant까지 검증
- 실제 OpenAI는 1~3 users smoke로 제한
- 고부하는 mock OpenAI 사용
- GUI Listener를 measured run에 포함하지 않음
- warm-up과 measured run을 분리
- 동일 scenario를 baseline과 after에 재사용 가능하게 설계

결과를 `performance/plans/jmeter-test-design.md`에 저장하고 `performance/state.md`를 갱신한다.


---

## Phase 6.5 — Test Harness Implementation and Dry Run

### Inputs

- `performance/plans/jmeter-test-design.md`
- 실제 애플리케이션 실행 방법과 테스트 환경 정보

### Preconditions

- Phase 6이 `COMPLETED` 상태여야 한다.
- 운영 데이터와 분리된 테스트 DB·Redis를 사용할 수 있어야 한다.

### Allowed changes

- `performance/harness/**`
- `performance/plans/test-harness-report.md`
- `performance/state.md`
- mock, fixture, JMX, 실행·수집·검증·cleanup 스크립트

### Forbidden changes

- 애플리케이션 성능 개선 코드
- 동시성 제어 코드
- DB migration
- 운영 설정과 배포 구조

### Outputs

- `performance/harness/**`
- `performance/harness/README.md` 또는 동등한 실행 방법
- `performance/harness/harness-manifest.json`
- JMeter JMX
- `users.csv` 또는 사용자별 테스트 데이터 입력 구조
- 사용자별 JWT 입력 구조
- OpenAI mock/stub 및 `mockOpenAiDelayMs` 설정
- Actuator 5초 수집 스크립트
- run metadata 수집 스크립트
- DB 사전·사후 invariant 검증 스크립트
- Redis 사전·사후 invariant 검증 스크립트
- 테스트 데이터 cleanup 스크립트
- JMeter CLI 실행 스크립트
- 결과 파일 무결성 검증 스크립트
- `performance/harness/bin/validate-plans.sh`
- `performance/harness/bin/validate-harness.sh`
- `performance/harness/bin/validate-run-artifacts.sh`
- `performance/harness/bin/validate-comparison.sh`
- `performance/harness/bin/scan-secrets.sh`
- `performance/plans/test-harness-report.md`

`harness-manifest.json` 필수 필드:

- `harnessVersion`
- `createdAt`
- `harnessCommit`
- `jmeterPlanHashes`
- `scriptHashes`
- `mockConfigurationHash`
- `dryRunId`
- `dryRunStatus`
- `verificationCommand`
- `secretScanStatus`

### Dry-run sequence

1. mock OpenAI 환경 확인
2. 1 user 테스트 데이터 및 JWT 준비
3. DB·Redis 사전 invariant 검사
4. Actuator 수집 시작
5. JMeter CLI dry run
6. assertion 및 JSON Extractor 검증
7. DB·Redis 사후 invariant 검사
8. cleanup 실행
9. 결과 파일과 secret 노출 여부 검사

### Completion criteria

- 1-user dry run 성공
- assertion과 extractor가 실제 응답 구조에서 정상 동작
- JMeter, Actuator, metadata, DB·Redis 검사 결과가 생성됨
- cleanup 후 테스트 데이터가 정리됨
- 실제 OpenAI가 호출되지 않음
- secret이 결과 파일에 포함되지 않음
- 실행 방법이 README에 재현 가능하게 기록됨
- `performance/harness/harness-manifest.json`이 존재하고 필수 필드를 모두 포함함
- `dryRunStatus=PASSED`이고 1-user dry-run 결과가 존재함
- secret scan과 harness verification command가 exit code 0으로 종료됨
- `HARNESS_COMMIT`이 실제 로컬 Git commit SHA로 manifest와 `performance/state.md`의 `baseline.harnessCommit`에 기록됨
- commit 이후 uncommitted tracked 변경이 없음
- raw 결과와 JWT·secret 값이 Git tracked 파일에 포함되지 않음

### Blocked conditions

- mock으로 OpenAI endpoint를 대체할 수 없음
- 테스트 DB·Redis를 운영 환경과 분리할 수 없음
- 테스트 사용자와 JWT를 준비할 수 없음
- JMeter CLI를 실행할 수 없음
- 테스트 하네스 commit 생성 실패
- harness manifest 생성 또는 검증 실패
- tracked 파일에 secret 또는 JWT가 포함됨
- commit 이후 tracked working tree가 clean하지 않음

Phase 6.5 완료 절차:

1. 테스트 하네스 tracked 파일을 모두 완성한다.
2. 1-user mock dry run을 성공시킨다.
3. raw dry-run 결과와 secret 파일이 Git tracked 대상이 아닌지 확인한다.
4. 하네스 구성 파일의 hash와 검증 결과를 계산한다.
5. 테스트 하네스 content tracked 파일을 로컬 commit으로 고정한다.
6. 해당 content commit SHA를 `HARNESS_COMMIT`으로 확정한다.
7. `HARNESS_COMMIT`을 포함한 최종 `harness-manifest.json`을 생성·검증하고 `performance/state.md`의 `baseline.harnessCommit`에 기록한다.
8. manifest와 state를 metadata-only local commit에 저장한 뒤 uncommitted tracked 변경이 없는지 확인한다.
9. 원격 push 또는 PR은 생성하지 않는다.

Phase 6.5가 완료되지 않으면 Phase 7 및 measured baseline으로 진행하지 않는다. dry run의 각 필수 검증 결과와 생성 artifact 경로를 `performance/plans/test-harness-report.md`에 기록하고 `performance/state.md`를 갱신한다.

---

## Phase 7 — Select Resume-Strength Experiments

### Inputs

- `performance/plans/chatbot-bottleneck-hypotheses.md`
- `performance/plans/group-event-crud-code-map.md`
- `performance/plans/group-event-concurrency-hypotheses.md`
- `performance/plans/observability-checklist.md`
- `performance/plans/jmeter-test-design.md`
- `performance/plans/test-harness-report.md`

### Preconditions

- Phase 6.5 dry run이 성공해야 한다.

### Allowed changes

- `performance/plans/selected-experiments.md`
- `performance/state.md`

### Forbidden changes

- 애플리케이션 개선 코드와 measured baseline 실행

### Outputs

- `performance/plans/selected-experiments.md`
- `performance/state.md`의 실제 selected ID별 `experiments.<experimentId>` 항목

### Completion criteria

- 재현 가능하고 독립 비교 가능한 실험만 최대 3개 선정됨
- 근거가 부족하면 1~2개만 선정할 수 있음
- 후속 Phase는 파일에 실제 기록된 experimentId만 처리함
- 각 experiment가 실제 hypothesisId와 scenarioId를 참조하고 독립 비교 가능성을 기록함
- `SMOKE_ONLY` 시나리오가 제외됨

### Blocked conditions

- dry run이 실패함
- 재현 가능한 후보가 하나도 없음


아직 애플리케이션 개선 코드를 수정하거나 추가 JMeter run 또는 measured run을 실행하지 마라.

다음 문서에서 확인된 문제와 가설을 모두 수집하라.

- performance/plans/chatbot-bottleneck-hypotheses.md
- performance/plans/group-event-crud-code-map.md
- performance/plans/group-event-concurrency-hypotheses.md
- performance/plans/observability-checklist.md

목표는 단순히 기술적으로 심각한 문제를 숫자에 맞춰 고르는 것이 아니다. 최대 3개를 선정한다.

AI Service Backend Engineer 또는 Backend Engineer 신입 이력서에서
가장 강한 문제 해결 사례가 될 수 있는 실험을 선정하는 것이다.

단, 이력서 매력도를 위해 실제로 존재하지 않는 문제를 선정하거나
검증되지 않은 가설을 사실처럼 취급하지 마라.

### 1. 필수 통과 조건

후보는 다음 조건을 모두 만족해야 한다.

- 실제 코드 근거가 있음
- 재현 가능한 테스트 시나리오가 있음
- baseline과 after를 동일 조건으로 비교할 수 있음
- 사용자 경험, 데이터 정합성 또는 운영 안정성과 연결됨
- 독립된 변경으로 효과를 검증할 수 있음

통과하지 못한 후보는 점수 평가 전에 제외하라.

### 2. 이력서 매력도 평가

각 후보를 아래 기준으로 평가하라.

- 목표 직무 연관성: 20점
- 기술적 깊이: 20점
- 정량 측정 가능성: 20점
- 서비스 영향도: 15점
- End-to-End 문제 해결 범위: 10점
- 신입 지원자 차별성: 10점
- 기간 내 완성 가능성: 5점

총점은 100점이다.

각 점수에는 반드시 코드와 테스트 근거를 작성하라.

### 3. 감점 기준

다음 후보는 감점하라.

- 단순 설정 또는 도구 설치에 그치는 문제
- 효과가 매우 작은 미세 최적화
- 다른 후보와 근본 원인이 같은 문제
- 정량 결과를 만들기 어려운 문제
- 실제 서비스 위험과 연결하기 어려운 문제
- 광범위한 재설계가 필요해 독립 검증이 어려운 문제

### 4. 포트폴리오 구성 원칙

가능하면 최종 선정 실험들이 가능하면 서로 다른 역량을 보여주도록 선정하라.

우선 구성:

1. 데이터 정확성·동시성 문제 1개
2. 성능·응답 지연 문제 1개
3. 외부 의존성 안정성 또는 DB 동시성 문제 1개

비슷한 외부 API 지연 가설은 하나의 experiment로 묶어라.

예:
- OpenAI 순차 호출
- timeout 부재
- connection pooling 부재

이들이 하나의 근본 원인과 개선안으로 연결된다면
‘외부 LLM 호출 지연 및 장애 전파’ 하나의 실험으로 통합하라.

### 5. 출력 형식

전체 후보를 다음 표로 작성하라.

- hypothesisId
- 문제 제목
- 코드 근거
- 재현 가능 여부
- 목표 직무 연관성
- 기술적 깊이
- 정량 측정 가능성
- 서비스 영향도
- End-to-End 범위
- 신입 차별성
- 완성 가능성
- 감점
- 최종 점수
- 선정 여부
- 탈락 이유
- 예상 이력서 한 줄

최종 선정 후보에는 experimentId를 부여하라.

- EXP-01
- EXP-02
- EXP-03

ID는 선정 순서대로 연속 부여하되 최대 3개이다. 예를 들어 두 개만 선정되면 `EXP-01`, `EXP-02`만 존재하며 후속 Phase는 `EXP-03`을 생성하거나 추정하지 않는다.

각 선정 후보에 대해 다음을 추가로 작성하라.

- 이력서에서 강조할 핵심 역량
- 개선 전 측정 지표
- 개선 후 목표 지표
- Actuator 관측 항목
- Pinpoint 관측 span
- DB/Redis invariant
- 예상 이력서 bullet 초안

실제 근거가 충분한 후보가 3개 미만이면
숫자를 맞추기 위해 억지로 선정하지 마라.

아직 개선안을 확정하거나 코드를 수정하지 마라.

결과를 `performance/plans/selected-experiments.md`에 저장하고 `performance/state.md`를 갱신한다.

---

## Phase 8A — Baseline Run Plan

### Inputs

- `performance/plans/selected-experiments.md`
- `performance/plans/test-harness-report.md`
- `performance/harness/**`

### Preconditions

- 선택된 experimentId가 하나 이상 존재해야 한다.
- Phase 6.5 dry run이 성공해야 한다.

### Allowed changes

- `performance/results/<experimentId>/baseline-plan.md`
- `performance/plans/baseline-source-manifest.json`
- `performance/state.md`

### Forbidden changes

- measured run
- 애플리케이션 코드 변경

### Outputs

- 선택된 각 실험의 `baseline-plan.md`
- `performance/plans/baseline-source-manifest.json`

`baseline-source-manifest.json` 필수 필드:

- `baselineGeneration`
- `baselineCommit`
- `harnessCommit`
- `baselineBranch`
- `frozenAt`
- `executionPathHashes`
- `jmeterScenarioHashes`
- `activeProfile`
- `dockerImageId`
- `dockerImageDigest`
- `verificationCommand`

### Completion criteria

- 실행 대상 experiment, 실행 명령, 환경, 테스트 데이터, JMeter 설정, mock 설정, 관측 항목, invariant, 정리 방법, 중단 조건, 예상 결과 파일이 정의됨
- 실제 선택된 각 experiment의 `baseline-plan.md`가 존재함
- `baseline-source-manifest.json`이 존재하고 필수 필드를 포함함
- 첫 measured baseline 전에 `BASELINE_COMMIT`과 baseline branch가 기록됨
- source manifest verification command가 exit code 0으로 종료됨
- `HARNESS_COMMIT`이 `BASELINE_COMMIT`의 ancestor임

### Blocked conditions

- 테스트 환경을 운영 환경과 구분할 수 없음
- 선택된 experiment의 dry-run 근거 또는 실행 가능한 harness가 없음
- `HARNESS_COMMIT` ancestry, baseline local commit 또는 source manifest 검증 실패
- execution-affecting 파일에 uncommitted tracked 변경이 있음

`performance/plans/selected-experiments.md`에 실제 기록된 experimentId만 순회한다. 각 실험에 대해 실행 대상, 명령, 환경, 데이터, JMeter와 mock 설정, 관측 항목, DB·Redis invariant, cleanup, 중단 조건, 예상 결과 파일을 `performance/results/<experimentId>/baseline-plan.md`에 기록한다. 이 Phase에서는 warm-up을 포함한 measured run을 실행하지 않는다. 모든 계획 파일을 만든 뒤 `performance/state.md`를 갱신한다.

모든 baseline 계획을 완성한 뒤 첫 measured baseline 전에 다음 순서로 source를 동결한다.

1. Phase 8A의 모든 `baseline-plan.md`를 완성하고 검증한다.
2. Phase 1~8A에서 생성된 tracked planning 및 harness 파일을 로컬 commit한다.
3. 현재 HEAD를 `BASELINE_COMMIT`으로 기록한다.
4. 현재 branch를 `baselineBranch`로 기록한다.
5. `HARNESS_COMMIT`이 `BASELINE_COMMIT`의 ancestor인지 검증한다.
6. execution-affecting 파일의 SHA-256 manifest를 생성한다.
7. `BASELINE_COMMIT`, source manifest와 baseline generation을 첫 measured baseline 전에 동결한다.
8. source manifest와 state는 execution-affecting 변경이 없는 metadata-only local commit으로 저장하고 tracked working tree가 clean한지 확인한다.

execution-affecting 파일은 최소한 다음을 포함한다.

- 애플리케이션 소스와 테스트 소스
- Gradle 설정
- DB migration
- `application*.yml`
- Dockerfile과 Compose 설정
- `performance/harness/**` 및 JMeter JMX
- mock 설정과 테스트 데이터 생성 스크립트
- DB·Redis invariant 검증 스크립트

동결 후 모든 selected experiment의 baseline이 끝날 때까지 execution-affecting 파일을 변경하지 않는다. 애플리케이션 코드, 하네스 동작, JMeter 시나리오, mock 설정, 테스트 데이터 생성 방식, DB·Redis invariant 검사 방식, active profile 또는 Docker 실행 설정이 변경되면 기존 generation을 `INVALIDATED`로 표시하고 baseline generation을 증가시킨다. 이후 새로운 `BASELINE_COMMIT`과 manifest를 확정하고 모든 selected experiment baseline을 재측정한다. 일부 실험만 새 commit으로 측정하지 않는다.

---

## Phase 8B — Baseline Execution

### Inputs

- `performance/plans/selected-experiments.md`
- 실제 선택된 각 experiment의 `performance/results/<experimentId>/baseline-plan.md`
- `performance/plans/baseline-source-manifest.json`
- `performance/plans/test-harness-report.md`
- `performance/harness/**`

### Preconditions

- 테스트 환경임이 확인됨
- 운영 데이터가 아닌 테스트 데이터임이 확인됨
- OpenAI mock 사용이 확인됨
- `selected-experiments.md`에 기록된 experimentId임
- Phase 6.5의 1-user dry run 성공이 확인됨
- 새로운 runId와 빈 결과 경로가 준비됨
- 대상 `baseline-plan.md`가 존재하고 검증됨
- `BASELINE_COMMIT`이 기록됨
- `performance/plans/baseline-source-manifest.json` 검증 성공
- 현재 execution-affecting 파일이 source manifest와 일치함
- `useRealOpenAI=false`임

### Allowed changes

- `performance/results/<experimentId>/baseline/**`
- `performance/state.md`

### Forbidden changes

- 애플리케이션 동작 코드 변경

### Outputs

- 각 experiment의 `performance/results/<experimentId>/baseline/run-metadata.json`
- 각 experiment의 `performance/results/<experimentId>/baseline/artifact-manifest.json`
- 각 experiment의 `performance/results/<experimentId>/baseline/summary.md`
- `performance/state.md`의 공통 `baselineCommit` 및 `harnessCommit`
- baseline summary, manifest와 state만 포함하는 `BASELINE_EVIDENCE_COMMIT`

### Completion criteria

- 선택된 각 실험이 독립 measured run으로 실행됨
- `CONFIRMED`, `PARTIALLY_CONFIRMED`, `DISPROVED`, `INCONCLUSIVE` 중 하나로 판정됨
- 모든 baseline 측정이 끝난 애플리케이션 commit이 `BASELINE_COMMIT`으로 고정됨
- 각 run의 필수 세 파일과 필수 필드가 존재함
- `performance/harness/bin/validate-run-artifacts.sh`가 exit code 0으로 종료됨

### Blocked conditions

- dry run 실패
- 테스트 DB/Redis 격리 불가
- 필수 관측 또는 결과 파일 생성 실패
- `BASELINE_COMMIT` 또는 source manifest 불일치
- `useRealOpenAI=true`
- cleanup 실패 또는 artifact validation 실패


`performance/plans/selected-experiments.md`에 실제 기록된 실험만 대상으로 한다.

아직 코드를 수정하지 마라.

Phase 8A에서 작성하고 검증한 각 experiment의 `baseline-plan.md`를 실제로 실행한다. Phase 8B에서는 새로운 baseline 계획을 작성하지 않는다.

각 실험은 다음 순서로 진행한다.

1. `BASELINE_COMMIT`과 source manifest 검증
2. experiment의 `baseline-plan.md` 로드
3. 새로운 runId 생성
4. 사전 invariant 검사
5. Actuator 수집 시작
6. warm-up 실행
7. measured JMeter run 실행
8. Actuator 수집 종료
9. Pinpoint evidence 기록
10. 사후 invariant 검사
11. cleanup
12. artifact manifest 생성
13. 결과 무결성 검증
14. baseline 판정
15. experiment 상태 갱신

모든 baseline은 Phase 8A에서 첫 measured baseline 전에 동결한 동일한 `BASELINE_COMMIT`을 사용한다.

- `BASELINE_COMMIT`
- baseline branch
- working tree clean 여부
- 테스트 하네스 commit SHA
- JMeter scenario hash
- Docker image ID 또는 digest
- active profile
- 측정 환경

각 baseline에서 반드시 기록할 metadata:

- experimentId
- runId
- git commit
- branch
- Docker image ID 및 digest
- Java/Spring Boot 버전
- active profile
- EC2 instance type
- RDS/Redis 환경
- OpenAI real/mock 여부
- mock delay
- JMeter scenario hash
- threads
- rampUp
- duration
- test data
- testRunId
- 시작·종료 시각
- Actuator 수집 주기
- Pinpoint sampling 설정

`run-metadata.json` 필수 필드:

- `experimentId`
- `scenarioId`
- `runId`
- `baselineGeneration`
- `baselineCommit`
- `harnessCommit`
- `jmeterScenarioHash`
- `activeProfile`
- `openAiMode`
- `threads`
- `rampUp`
- `duration`
- `testRunId`
- `startedAt`
- `endedAt`

`artifact-manifest.json`의 각 artifact entry 필수 필드:

- `runId`
- `artifactType`
- `relativePath`
- `sha256`
- `sizeBytes`
- `createdAt`
- `validationStatus`

Actuator는 5초 간격 time series로 저장한다.

필수 Actuator 분석값:

- process.cpu.usage average/peak
- jvm.memory.used start/peak/end
- jvm.gc.pause count delta
- jvm.gc.pause total time delta
- tomcat.threads.busy peak
- tomcat.threads.current peak
- hikaricp.connections.active peak
- hikaricp.connections.pending peak
- hikaricp.connections.timeout delta
- http.server.requests count delta
- http.server.requests total time delta
- 대상 URI의 max

Pinpoint 대표 trace에는 다음을 기록한다.

- trace 관찰 시간
- 총 elapsed time
- Controller/Service 시간
- SQL 호출 횟수와 누적 시간
- Redis 호출 횟수와 누적 시간
- 외부 HTTP 호출 횟수와 누적 시간
- 가장 긴 span
- 발생 예외
- 대표 trace를 선정한 기준

Pinpoint Web에 직접 접근할 수 없다면
사용자에게 screenshot, trace export 또는 trace-summary.md를 요청하고,
확인하지 못한 값을 추정하지 마라.

각 run의 결과는 `performance/results/<experimentId>/baseline/**`에 저장한다.

각 실험 결과를 다음으로 판정하라.

- CONFIRMED
- PARTIALLY_CONFIRMED
- DISPROVED
- INCONCLUSIVE

DISPROVED 또는 INCONCLUSIVE인 후보를 바로 개선하지 마라.
필요하면 selected-experiments의 다음 순위 후보로 교체를 제안하라.

모든 selected experiment baseline이 완료되면 검증된 baseline summary, artifact manifest와 state만 `BASELINE_EVIDENCE_COMMIT`으로 로컬 commit한다. 이 commit에는 execution-affecting 파일 변경을 포함하지 않고 SHA를 `performance/state.md`의 `baseline.baselineEvidenceCommit`에 기록한다.


---

선정된 실험을 한 번에 고치지 않고 `selected-experiments.md`에 실제 기록된 experimentId를 하나씩 반복 처리한다.

## Phase 9 — Isolated Improvement and After Run

### Inputs

- `performance/plans/selected-experiments.md`
- 대상 experiment의 `performance/results/<experimentId>/baseline/**`
- `performance/state.md`의 `BASELINE_COMMIT`

### Preconditions

- 대상 실험이 `CONFIRMED` 또는 `PARTIALLY_CONFIRMED`여야 한다.
- 한 번에 하나의 experimentId만 처리한다.
- 사용자가 대상 Per-Experiment Goal을 시작해야 한다.

### Allowed changes

- `BASELINE_COMMIT`에서 만든 실험 전용 branch와 worktree의 최소 애플리케이션 변경
- 관련 테스트
- `performance/results/<experimentId>/improvement-plan.md`
- `performance/results/<experimentId>/after/**`
- `performance/results/<experimentId>/experiment-state.yml`

### Forbidden changes

- 다른 experiment 변경 포함
- 무관한 리팩터링
- 원격 push·merge·PR
- 운영 환경 변경

### Outputs

- `performance/results/<experimentId>/improvement-plan.md`
- `performance/results/<experimentId>/after/run-metadata.json`
- `performance/results/<experimentId>/after/artifact-manifest.json`
- `performance/results/<experimentId>/after/summary.md`
- `performance/results/<experimentId>/experiment-state.yml`

### Completion criteria

- 실험별 별도 branch/worktree가 `BASELINE_COMMIT`에서 분기됨
- 다른 실험 commit이 포함되지 않음
- `improvement-plan.md`와 `EXP_PLAN_COMMIT`이 애플리케이션 변경 전에 생성됨
- 단위·통합 테스트가 통과하고 `IMPROVEMENT_COMMIT`이 기록됨
- `IMPROVEMENT_COMMIT` 생성 후 change scope가 `CHANGE_SCOPE_VALIDATED`로 판정됨
- after artifact manifest 검증 명령이 exit code 0으로 종료됨
- working tree가 clean하고 rollback 명령이 기록됨
- baseline과 동일 조건의 after run이 완료됨

### Blocked conditions

- merge base가 `BASELINE_COMMIT`이 아님
- worktree가 오염되었거나 사용자 변경을 안전하게 보존할 수 없음
- baseline 비교 조건을 재현할 수 없음
- change scope가 `CHANGE_SCOPE_REJECTED` 또는 `CHANGE_SCOPE_UNKNOWN`임
- rollback 준비 또는 artifact 검증 실패


baseline에서 CONFIRMED 또는 PARTIALLY_CONFIRMED로 판정된 실험만 개선한다.

한 번에 하나의 experimentId만 처리하라.
다른 experiment의 변경을 섞지 마라.

현재 처리할 experimentId를 먼저 명시하라.

### Step 0. Baseline commit과 worktree 격리

모든 baseline에 사용된 하나의 애플리케이션 commit을 `BASELINE_COMMIT`으로 사용한다.

각 experiment는 반드시 `BASELINE_COMMIT`에서 분기한 별도 branch와 별도 worktree를 사용한다.

- `perf/exp-01`
- `perf/exp-02`
- `perf/exp-03`

실제 선정된 experimentId만 생성한다.

변경 전에는 다음만 검증한다.

1. 현재 branch가 대상 experiment 전용 branch인가
2. `BASELINE_COMMIT`이 branch의 ancestor인가
3. branch 시작 시점에 execution-affecting 차이가 없는가
4. `BASELINE_COMMIT` 이후에는 허용된 baseline evidence만 존재하는가
5. uncommitted tracked 변경이 없는가
6. 다른 experiment branch가 아닌가

변경 전에는 application diff가 아직 없으므로 변경 범위의 최종 판정을 하지 않는다.

EXP-02의 after에 EXP-01 변경이 포함되어서는 안 되고, EXP-03의 after에도 다른 experiment 변경이 포함되어서는 안 된다.

성공한 개선의 통합은 모든 개별 비교가 끝난 뒤 별도의 integration branch에서만 수행한다.

### Step 1. 개선안 제안

최소 2가지 대안을 비교하라.

- 정확성
- 성능
- 구현 복잡도
- 변경 파일
- DB migration 필요 여부
- lock 범위
- transaction 범위
- deadlock 위험
- Redis 원자성
- 외부 API 장애 대응
- rollback 전략
- 기존 API 호환성
- 재검증 시나리오

baseline 근거가 없는 추측성 최적화는 제외하라.

대상 Per-Experiment Goal이 시작되지 않았다면 애플리케이션 코드를 수정하지 말고 멈춘다. Goal이 시작된 경우 그 행위가 해당 experiment의 최소 변경 승인이므로 별도 승인을 다시 요구하지 않는다.

`improvement-plan.md`를 먼저 작성하고 로컬 `EXP_PLAN_COMMIT`으로 고정한다. 이 commit에는 애플리케이션 또는 테스트 변경을 포함하지 않는다.

### Step 2. 변경 적용

대상 Per-Experiment Goal 안에서:

1. 변경 전 commit을 기록
2. 해당 experiment에 필요한 최소 코드만 수정
3. 테스트 추가
4. ./gradlew test 실행
5. 관련 통합·동시성 테스트 실행
6. 해당 experiment의 애플리케이션과 테스트 변경만 `IMPROVEMENT_COMMIT`으로 기록
7. rollback 방법 작성

### Step 2.5 변경 후 diff 검증

`IMPROVEMENT_COMMIT` 생성 후 다음을 검사한다.

1. `BASELINE_COMMIT..IMPROVEMENT_COMMIT` commit 목록
2. 변경 파일 목록
3. 애플리케이션 및 테스트 diff
4. `improvement-plan.md`에 없는 변경
5. 다른 experiment 관련 변경
6. 무관한 리팩터링
7. DB migration 유무
8. API 호환성 변경 유무
9. execution-affecting 파일의 변경 범위
10. rollback 가능 여부

결과는 `CHANGE_SCOPE_VALIDATED`, `CHANGE_SCOPE_REJECTED`, `CHANGE_SCOPE_UNKNOWN` 중 하나로 experiment state에 기록한다. `CHANGE_SCOPE_VALIDATED`가 아니면 after measured run을 실행하지 않는다.

### Step 3. After Run

baseline과 동일한 조건으로 after run을 실행한다.

다음 항목이 baseline과 같은지 먼저 검증하라.

- EC2/RDS/Redis 환경
- active profile
- test data
- JMeter 파일
- threads
- rampUp
- duration
- mock/real OpenAI
- mock delay
- Actuator 수집 주기
- Pinpoint sampling 설정

의도된 차이는 application commit과 Docker image뿐이어야 한다.

동일하지 않은 조건이 있으면 실행 전에 보고하라.

after 결과는 `performance/results/<experimentId>/after/**`에 저장한다.

after 결과에도 baseline과 동일하게 다음을 저장한다.

- run-metadata.json
- JMeter JTL/HTML
- Actuator JSONL/summary
- Pinpoint trace-summary
- application log
- DB/Redis postconditions

raw artifact는 ignore 상태로 보존하고 `artifact-manifest.json`에서 상대 경로와 SHA-256으로 참조한다. Phase 9 종료 시 after summary와 manifest를 애플리케이션 변경이 없는 report-only `REPORT_COMMIT`에 기록해 tracked working tree를 clean하게 만든다. Phase 10 comparison을 추가하면 새 report-only commit을 만들고 state의 `reportCommit`을 최신 SHA로 갱신한다. 애플리케이션 변경 worktree와 report 기록 worktree를 분리한 경우 양쪽 모두 uncommitted tracked 변경이 없어야 한다.

---

## Phase 10 — Per-Experiment Before/After Comparison

### Inputs

- `performance/results/<experimentId>/baseline-plan.md`
- `performance/results/<experimentId>/improvement-plan.md`
- `performance/results/<experimentId>/baseline/run-metadata.json`
- `performance/results/<experimentId>/baseline/artifact-manifest.json`
- `performance/results/<experimentId>/baseline/summary.md`
- `performance/results/<experimentId>/after/run-metadata.json`
- `performance/results/<experimentId>/after/artifact-manifest.json`
- `performance/results/<experimentId>/after/summary.md`
- `performance/results/<experimentId>/experiment-state.yml`
- `performance/plans/baseline-source-manifest.json`

### Preconditions

- 두 run의 metadata와 결과 파일이 존재해야 한다.
- baseline과 after artifact manifest 검증 성공
- 두 run의 metadata 필수 필드 존재
- baselineCommit 일치
- scenario hash 일치
- 비교 가능성 판정 완료
- baseline과 after summary 존재
- improvementCommit 기록 존재

### Allowed changes

- `performance/results/<experimentId>/comparison.md`
- `performance/state.md`

### Forbidden changes

- 애플리케이션 코드 추가 수정

### Outputs

- `performance/results/<experimentId>/comparison.md`

### Completion criteria

- 비교 가능성이 판정되고 모든 핵심 지표와 정확성 invariant가 비교됨
- 최종 판정이 명시됨
- baseline과 after manifest 검증 성공
- metadata 비교 결과가 존재함
- 모든 필수 JMeter 지표와 적용 가능한 Actuator·Pinpoint 지표가 비교됨
- DB·Redis invariant, 절대 변화량과 변화율이 비교됨
- `performance/harness/bin/validate-comparison.sh`가 exit code 0으로 종료됨

### Blocked conditions

- run metadata가 없어 비교 조건을 확인할 수 없음
- manifest 검증 실패, baselineCommit 또는 scenario hash 불일치
- improvementCommit을 확인할 수 없음


각 experiment의 명시된 metadata, summary와 manifest를 비교한다. raw artifact는 검증된 manifest의 상대 경로로만 참조하며 임의의 wildcard 탐색 결과를 비교 근거로 사용하지 않는다.

먼저 run metadata 동일성을 검사하라.

비교 조건이 다르면:

- 직접 비교 가능
- 부분 비교 가능
- 비교 불가

로 구분하라.

### JMeter 비교

- request count
- throughput
- error rate
- average
- p50
- p95
- p99
- max

### Actuator 비교

- CPU average/peak
- JVM memory peak/end
- GC count delta
- GC total pause delta
- Tomcat busy thread peak
- Hikari active peak
- Hikari pending peak
- Hikari timeout delta
- HTTP request count/total time/max

### Pinpoint 비교

- total elapsed time
- Controller/Service elapsed
- SQL count 및 누적 시간
- Redis command count 및 누적 시간
- 외부 HTTP count 및 누적 시간
- longest span
- exception 발생 여부

### 정확성 비교

- 중복 Event/Todo
- Redis token 잔존
- token 중복 소비
- GroupEvent lost update
- orphan Mapping
- orphan Alarm
- partial write
- 최종 HTTP 응답의 일관성

각 지표에는 다음을 포함하라.

- baseline 값
- after 값
- 절대 변화량
- 변화율
- 개선 여부
- 측정 한계

최종 판정:

- VERIFIED_IMPROVEMENT
- CORRECTNESS_IMPROVEMENT
- PERFORMANCE_REGRESSION
- CORRECTNESS_REGRESSION
- NO_MEANINGFUL_CHANGE
- INCONCLUSIVE

보고서를 `performance/results/<experimentId>/comparison.md`에 저장하고 `performance/state.md`를 갱신한다.


---

## Phase 11 — Final Selected-Experiments Summary

### Inputs

- 실제 완료된 experiment의 `comparison.md`
- `performance/plans/selected-experiments.md`

### Preconditions

- 완료된 comparison이 하나 이상 존재해야 한다.

### Allowed changes

- `performance/results/final-summary.md`
- `performance/state.md`

### Forbidden changes

- 개별 branch 자동 merge
- 원격 push·PR

### Outputs

- `performance/results/final-summary.md`

### Completion criteria

- 실제 완료된 실험만 종합됨
- 확인된 측정값만 사용됨
- integration branch에서의 권장 병합 순서와 회귀 테스트가 제안됨

### Blocked conditions

- 완료된 comparison이 없음


`selected-experiments.md`에 기록되고 실제 완료된 experiment의 comparison.md만 읽어라.

완료된 선정 실험을 다음 기준으로 종합하라.

- 문제의 실제 원인
- 개선 내용
- 정확성 변화
- p95/p99 변화
- throughput 변화
- CPU/Memory/GC 변화
- Tomcat/Hikari 변화
- Pinpoint 주요 span 변화
- DB/Redis invariant 변화
- 구현 복잡도
- 남은 위험
- 측정 한계

각 실험을 다음으로 구분하라.

- 성공적으로 개선됨
- 정확성만 개선됨
- 성능만 개선됨
- 회귀 발생
- 추가 검증 필요
- 가설이 반증됨

완료된 실험을 종합해 다음을 작성하라.

1. 가장 효과가 컸던 개선
2. 가장 위험했던 문제
3. 성능보다 정확성이 중요했던 개선
4. 추가 최적화 후보
5. 포트폴리오에 사용할 수 있는 정량 결과
6. 결과를 일반화할 수 없는 부분

확인되지 않은 수치를 만들어내지 마라.

보고서를 `performance/results/final-summary.md`에 저장하고 `performance/state.md`를 `COMPLETED`로 갱신한다.

---

## Appendix A — 권장 디렉터리 구조

    PlanWith/
    ├── AGENTS.md
    ├── docs/
    │   └── codex/
    │       └── planwith-performance-workflow.md
    └── performance/
        ├── state.md
        ├── plans/
        ├── harness/
        └── results/

## Appendix B — `performance/state.md` 초기 템플릿

`performance/state.md`의 실제 초기 템플릿을 canonical source로 사용한다. Phase 7에서 experiment를 선정할 때 `experiments` 아래에 실제 선정된 ID만 다음 구조로 추가한다.

```yaml
experiments:
  EXP-01:
    hypothesisId: null
    scenarioId: null
    status: NOT_STARTED
    lastCompletedStep: null

    baseline:
      status: NOT_RUN
      runId: null
      verdict: null
      artifactManifest: null
      summary: null

    git:
      branch: null
      worktree: null
      baseCommit: null
      planCommit: null
      improvementCommit: null
      reportCommit: null

    improvement:
      status: NOT_STARTED
      changeScopeStatus: null
      planPath: null

    after:
      status: NOT_RUN
      runId: null
      artifactManifest: null
      summary: null

    comparison:
      status: NOT_STARTED
      verdict: null
      path: null

    cleanup:
      status: NOT_REQUIRED
      lastRunId: null
      error: null

    rollback:
      status: NOT_REQUIRED
      command: null
      error: null

    retryCount: 0
    blockers: []
    outputs: []
```

상태가 `FAILED`, `SKIPPED`, `NOT_APPLICABLE`이면 해당 Phase 또는 experiment 항목에 `reason`과 `statusChangedAt`을 추가한다. retry 시 실패 원인과 조건 변경은 blockers 또는 해당 run summary에 기록하고 state에서 경로를 참조한다.

## Appendix C — 최종 구조 검증 체크리스트

- Markdown code fence가 모두 정상적으로 닫혀 있음
- Group Event CRUD가 범위이고 Group Invitation이 제외됨
- 각 필수 input의 생성 Phase가 존재함
- Phase 6.5에서 `HARNESS_COMMIT`, manifest와 검증 스크립트가 생성됨
- Phase 6.5가 measured baseline보다 앞에 있고 mock dry run을 통과함
- `BASELINE_COMMIT`과 source manifest가 첫 measured baseline 전에 동결됨
- 동결 후 execution-affecting 변경이 차단됨
- Phase 8B는 검증된 plan을 실행하며 새 계획을 작성하지 않음
- real OpenAI smoke가 experiment 선정과 measured 비교에서 제외됨
- tracked report와 ignored raw artifact가 분리됨
- 최대 3개이며 실제 선정된 experimentId만 처리함
- 각 experiment가 별도 branch/worktree에서 실행됨
- EXP-02·EXP-03 결과에 이전 experiment 변경이 포함되지 않음
- change scope가 `IMPROVEMENT_COMMIT` 이후 검증됨
- Phase 10이 정확한 manifest와 metadata 경로만 사용함
- state만으로 Phase와 experiment별 진행 위치를 복원할 수 있음
- 실패, cleanup, retry와 rollback 상태 전이가 정의됨
- completion이 manifest와 validation command에 연결됨
- integration에는 `IMPROVEMENT_COMMIT`만 적용됨
- 운영 환경과 실제 OpenAI 고부하가 차단됨
- 완료된 모든 결과가 `performance/**`에 저장됨
