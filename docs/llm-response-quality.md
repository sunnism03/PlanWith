# LLM 응답 품질 및 승인 안전성

## 구현 상태와 범위

기준: `origin/develop`의 `cb3a501` (승인 멱등성 PR이 합쳐진 상태). 작업 브랜치: `codex/llm-response-quality`.

런타임 검증·복구, 200건 **검수 후보** 데이터셋, Promptfoo 연결, 지표 및 회귀 게이트를 구현했다. 실제 OpenAI API 평가 결과는 **미측정**이다. 환경에 `OPENAI_API_KEY`가 없고 사람의 라벨 검수도 완료되지 않았다. Mock/단위 테스트 결과를 의미 정확도로 해석하지 않는다.

Event는 개인 일정, Todo와 Plan Recommendation은 개인 Todo로 저장된다. Plan Recommendation에는 별도 목표/계획 엔티티가 없으며 단계별 Todo 제안만 가능하다. 반복 규칙, 시리즈 ID, 반복 종료 조건 또는 발생일 생성기가 기존 모델에 없다. 이번 변경은 새 반복 모델을 추가하지 않는다. 명시적 반복 요청은 `UNSUPPORTED_RECURRENCE`로 중단하고, 반복 정확도는 N/A로 보고한다. 반복 기능을 지원하려면 별도 규칙 DTO/DB/API, 시간대·DST·종료일/횟수·월말 정책, 발생일 중복/수정 정책 및 마이그레이션이 먼저 필요하다.

## A. 기존 문제와 해결 근거

| 기존 위치 | 문제 상황 / 원인 | 변경 | 근거 및 한계 |
|---|---|---|---|
| ChatGPTServiceImpl.findIntention | 한국어 문자열을 완전 일치 비교 | enum을 갖는 strict intent 객체 | 분류 구조를 검증하지만 의미 오분류는 여전히 가능 |
| JsonConstants.EVENT/TODO | 따옴표 누락, 스마트 따옴표, 날짜/시간 필드 혼동 | OutputSchemas + QualityPrompts | 요청·응답 필드와 nullable 계약 명시 |
| JsonConstants.DATE_INFO | 클래스 로드 시점 날짜 고정 | 요청 시작 시 Clock을 고정하고 서울 시간 사용 | 자정 경계와 상대 날짜 회귀 테스트 |
| ChatbotMapper | 정규식으로 설명이 섞인 JSON 허용, 예외 통합, 값 자동 보충 | 전체 JSON 엄격 파싱, 중복 키·여분 필드·타입·실제 날짜 검증 | BaselineRegressionTest에서 이전 수용/현재 거부 재현 |
| ChatbotServiceImpl | 생성값을 바로 승인용 Redis에 공개 | 추출 → 업무 검증 → READY 스냅샷 | 실패 시 요청/토큰 정리, 승인용 결과 미생성 |
| ApprovalPersistenceService | 생성 단계와 별도 검증 구현 | 공통 BusinessValidator 재사용 | 기존 트랜잭션·ledger·권한·멱등성 구조 유지 |
| ChatGPTServiceImpl / OpenAIConfig | HTTP 상태·거부·잘림 구별 및 호출 상한 없음 | 오류 코드, 제한 재시도, 시간/토큰 예산 | loopback HTTP 테스트, 실제 API는 미검증 |
| ChatbotServiceImpl 로그 | 토큰 stdout 노출 | 활성 로그와 오래된 디버그 예시 제거 | 사용자 입력·응답·키는 오류 메시지에 포함하지 않음 |
| gradle.yml | develop/main push 시 EC2 배포, 테스트 제외 | 별도 llm-quality.yml | 배포 워크플로는 변경하지 않음 |

재현된 개선은 고정 응답에 대한 동작 변화다. 실제 모델에서 각 문제가 얼마나 빈번한지는 측정하지 않았다. 엄격 파싱은 이전에 수용하던 설명 포함 JSON을 거절하는 비용이 있지만 Structured Outputs 계약을 어긴 응답을 안전하게 중단한다. 필수 날짜 누락, 모호한 AM/PM, 반복 요청은 추가 확인이 필요하므로 자동 생성 성공률과 사용 편의성이 일부 감소할 수 있다.

## B. 파일별 변경

| 경로 (저장소 기준) | 구분 | 구현/수정 이유 |
|---|---|---|
| src/main/java/com/JAI/chatbot/quality/QualityError.java | 신규 | 오류 분류 |
| src/main/java/com/JAI/chatbot/quality/QualityException.java | 신규 | 정제된 원인과 HTTP 상태 |
| src/main/java/com/JAI/chatbot/quality/OutputSchemas.java | 신규 | intent/Event/Todo strict schema |
| src/main/java/com/JAI/chatbot/quality/QualityPrompts.java | 신규 | 시스템 지시/사용자 데이터 분리, 요청별 날짜와 버전 |
| src/main/java/com/JAI/chatbot/quality/BusinessValidator.java | 신규 | 생성·READY·승인 공통 업무 규칙 |
| src/main/java/com/JAI/chatbot/quality/InputPolicy.java | 신규 | 명시적 반복/모호한 AM·PM 차단 |
| src/main/java/com/JAI/chatbot/quality/RelativeDateGuard.java | 신규 | 단일 상대 날짜의 결정적 근거 검증 |
| src/main/java/com/JAI/chatbot/quality/CallBudget.java | 신규 | 단조 시계 예산, 호출 수, 토큰 계측 |
| src/main/java/com/JAI/chatbot/quality/ExtractionService.java | 신규 | 2단계 호출 유지, 제한된 재생성 |
| src/main/java/com/JAI/chatbot/quality/SchemaCheck.java | 신규 | 출력 스키마 검증과 업무 검증의 평가상 분리 |
| src/main/java/com/JAI/chatbot/service/ChatGPTService.java | 수정 | 내부 구조화 호출 인터페이스 |
| src/main/java/com/JAI/chatbot/service/ChatGPTServiceImpl.java | 수정 | strict 요청, HTTP 복구, refusal/finish_reason |
| src/main/java/com/JAI/chatbot/service/ChatbotServiceImpl.java | 수정 | 새 파이프라인과 Redis 연결, 실패 정리 |
| src/main/java/com/JAI/chatbot/controller/ChatbotController.java | 수정 | 공유 예산 안에서 분류·생성 실행 |
| src/main/java/com/JAI/chatbot/mapper/ChatbotMapper.java | 수정 | 정규식 제거, 엄격 파싱 및 검증 |
| src/main/java/com/JAI/chatbot/domain/JsonConstants.java | 삭제 | 정적 날짜와 잘못된 JSON 예시 제거 |
| src/main/java/com/JAI/chatbot/controller/dto/ChatGPTMessageDTO.java | 수정 | refusal 읽기 (요청 null 필드는 보내지 않음) |
| src/main/java/com/JAI/chatbot/controller/dto/request/ChatGPTReqDTO.java | 수정 | response_format, temperature, 출력 토큰 상한 |
| src/main/java/com/JAI/chatbot/controller/dto/response/ChatGPTChoiceDTO.java | 수정 | finish_reason |
| src/main/java/com/JAI/chatbot/controller/dto/response/ChatGPTRespDTO.java | 수정 | usage 토큰 계측 |
| src/main/java/com/JAI/config/OpenAIConfig.java | 수정 | Clock과 연결/읽기 타임아웃 |
| src/main/java/com/JAI/chatbot/RedisChatbotUtil.java | 수정 | READY 직전 전체 검증 |
| src/main/java/com/JAI/chatbot/approval/service/ApprovalPersistenceService.java | 수정 | 승인 트랜잭션 내부 동일 검증 |
| src/test/java/com/JAI/chatbot/quality/*.java | 신규 | 파서·도메인·복구·전송·안전성·baseline 재현 테스트 |
| src/test/java/com/JAI/chatbot/evaluation/ExtractionHarness.java | 신규 | 실제 서비스 CLI 평가, DB/Redis 연결 없음 |
| src/test/java/com/JAI/chatbot/evaluation/baseline/*.java | 신규 | cb3a501 프롬프트/매퍼 보존 |
| evaluation/data/{development,holdout}.json | 신규 | 200건 합성 후보, 검수 상태 및 분리 |
| evaluation/generate-dataset.py | 신규 | 달력/정책 후보 생성, 검수 라벨 덮어쓰기 방지 |
| evaluation/dataset.cjs | 신규 | 라벨 필드·중복·분리·검수 증거 검증 |
| evaluation/{provider,assertion,prepare}.cjs | 신규 | Promptfoo → Java 연결, 라벨 전달 방지 |
| evaluation/{metrics,gate,report}.cjs | 신규 | 지표·반복 비교·회귀 판정·보고서 |
| evaluation/metrics.test.cjs | 신규 | 지표·누락·실패·격리 자동 테스트 |
| evaluation/{smoke.json,smoke-provider.cjs} | 신규 | API 없는 Promptfoo 연결 검사 |
| evaluation/{package.json,package-lock.json} | 신규 | Promptfoo 0.124.1 고정 |
| .github/workflows/llm-quality.yml | 신규 | 배포와 독립된 PR/수동 평가 |
| build.gradle | 수정 | 테스트 전용 평가 classpath 생성 |
| .gitignore | 수정 | 의존성·생성 설정·모델 결과 제외 |

## C. 런타임 계약·복구·저장 안전성

OpenAI Chat Completions의 `response_format.type=json_schema`, `strict=true`를 사용한다. 모든 object는 `additionalProperties=false`, 모든 필드는 required이다. 날짜/시각의 미지정 표현은 JSON null이다. Event의 시작/종료 날짜, Todo의 날짜는 READY 업무 계약상 필수다. Event 종료 날짜가 생략됐을 때 시작 날짜와 같은 날짜를 사용하도록 프롬프트에서 명시한다. 시작 시각만 있는 Event는 허용하고 종료 시각만 있는 Event는 확인을 요청한다. 시각 미지정은 null이며 임의 시각을 생성하지 않는다.

내부 모델 출력은 `{status, events}` 또는 `{status, todos}`이다. 정상 사용자 응답은 기존 `ChatbotResponseWrapper.responses` 및 `tokenReqDTO` 형태를 유지한다. 에러도 기존 ApiResponse를 사용하며 원인 코드와 필드가 message에 포함된다. 오류 처리 시 새 토큰은 반환하지 않는다. 사용자는 정보를 구체화해 새 요청을 제출한다. 인라인 수정 API나 다중 턴 확인 세션은 추가하지 않았다.

지원 모델 allowlist: `gpt-4o-mini`, `gpt-4o-mini-2024-07-18`, `gpt-4o`, `gpt-4o-2024-08-06`, `gpt-4o-2024-11-20`. 평가 기본값은 `gpt-4o-mini-2024-07-18`. 배포 전에 기존 비공개 `openai.model` 설정을 확인해야 한다. 미지원 모델은 `API_CONFIGURATION`으로 명확히 실패하며 JSON mode로 강등하지 않는다. 새 모델은 문서 확인과 전송 테스트 후 allowlist를 확장한다. API 키 권한과 해당 모델 이용 가능 여부는 실제 호출 전까지 미검증이다.

| 오류 | 탐지 | 자동 복구 | 최종 처리 / 테스트 |
|---|---|---|---|
| SCHEMA_INVALID | JSON·필드·타입·날짜 파싱 | 없음 | 400, READY 없음; 파서/잘못된 타입/중복키 테스트 |
| REQUIRED_FIELD_MISSING | 빈 제목/빈 목록/상한 초과 | 없음 | 400, 입력 확인; 업무 검증 테스트 |
| INVALID_DATE_RANGE | 날짜 역전/동일일 시간 역전 | 추가 생성 최대 1회 | 재검증 실패 400; 복구 상한 테스트 |
| SEMANTIC_MISMATCH | 지원 범위의 상대 날짜 근거 불일치 | 추가 생성 최대 1회 | 재검증 실패 400; 성공/반복실패 테스트 |
| AMBIGUOUS_INPUT | 필수 날짜 null, bare AM/PM, 모델 확인 상태, intent 충돌 | 없음 | 400, 사용자 구체화 요청 |
| UNSUPPORTED_RECURRENCE | 명시적 반복 표현/모델 상태 | 없음 | 400, 반복을 지원한다고 가장하지 않음 |
| API_RATE_LIMIT | HTTP 429 | 해당 호출 추가 1회, 공유 예산 내 | Retry-After 초과/재실패 503 |
| API_UNAVAILABLE | 500/502/503/504 | 해당 호출 추가 1회, 공유 예산 내 | 기타 5xx는 재시도 없음; 503 |
| API_TIMEOUT | 전송 연결/읽기 실패 | 없음 (불명확한 처리 결과를 무조건 재호출하지 않음) | 503; 짧은 timeout 테스트 |
| API_CONFIGURATION | 400/401/403/404/422, 모델 미지원 | 없음 | 503, 설정 점검 |
| MODEL_REFUSAL | message.refusal | 없음 | 400; 거부 테스트 |
| INCOMPLETE_RESPONSE | 빈 choices/content, finish_reason!=stop | 없음 | 400; 잘림/누락 테스트 |
| CALL_BUDGET_EXCEEDED | 단조 시간·전체 호출 상한 | 없음 | 503; 만료/전체 횟수 테스트 |

분류+생성 두 단계는 유지한다. 논리 요청 최대 5 API 호출, 40초 예산, 각 연결 최대 2초·읽기 최대 8초(남은 예산에 맞게 축소), 출력 최대 4096 토큰이다. HTTP 재시도 간 지수 backoff 기준 250ms + 0..250ms jitter 및 Retry-After(초/HTTP 날짜)를 적용한다. 현재 호출별 재시도가 1회이므로 지수 지연의 첫 단계만 사용된다. 출력 재생성에도 같은 전체 예산을 공유한다. 회복 가능한 필드/코드만 시스템 지시에 추가하고 실패 응답 본문은 다시 넣지 않는다.

40초 예산은 시도 전/후·대기·소켓 타임아웃뿐 아니라 Future 대기에도 적용한다. upstream이 계속 작은 조각을 보내더라도 요청 대기를 중단하고 미검증 결과를 반환하지 않는다. 중단된 blocking I/O가 즉시 끝나지 않을 가능성에 대비해 HTTP 작업은 최대 16개 daemon worker와 대기열 없는 pool로 제한하며 포화 시 503으로 실패한다. 소켓 자체에도 읽기 제한이 적용된다. 연속 응답 조각을 보내는 HTTP stub으로 전체 deadline을 검증한다.

Redis 요청 데이터와 승인 READY 스냅샷은 구분된다. 전체 검증이 끝난 목록만 READY로 만든다. 생성 실패는 요청 키와 토큰 매핑을 삭제한다. 기존 READY 스냅샷은 불변이며 승인/거절은 기존 owner-checked Lua 및 ledger 처리로 정리한다. TTL은 기존 1시간이다. 승인 시 동일 BusinessValidator를 트랜잭션 안에서 다시 적용하며 일부 항목만 저장하지 않는다. 기존 사용자/개인 카테고리 권한, DB unique ledger, 동일 토큰 재승인 방지는 유지한다. DB 마이그레이션은 이번 변경에 필요하지 않다. 기존 승인 ledger 마이그레이션은 기존 문서를 따른다.

Java 상대 날짜 guard는 **단일·단순** 오늘/내일/모레/어제 표현만 확인한다. 복수 날짜, 기간, 숫자/요일이 섞인 표현은 보수적으로 검사에서 제외한다. 복잡한 자연어, 날짜 없는 입력에서 모델이 그럴듯한 날짜를 만들어 낸 경우, 근거 없는 추가 항목, 다중 AM/PM 해석을 완벽히 탐지하지 못한다. 사용자 미리보기·승인과 검수된 오프라인 평가가 계속 필요하다.

## D. 데이터와 평가 실행

총 200건: 달력 40, 상대 날짜 30, 시각 30, 복수 항목 20, 모호/그 외 요청 20, 잘못된 날짜·시각 20, 반복 20, 계획 추천 20. 같은 의미의 문장을 바꿔 수를 채우는 대신 윤년·월말·연말·시각 경계·항목 수를 바꾸는 합성 경계 사례다. 실제 사용자 발화 분포를 대표한다고 주장하지 않는다.

개발 139건 / holdout 61건. 관련 달력/시각/상대 날짜 사례는 boundary family 단위로 분리한다. **사람 검수 완료 0건, review_required 200건**. 결정적 달력/정책 예상값과 주관적인 계획 추천 draft를 구분한다. 계획 추천 정답은 특히 사람의 재설계/허용 별칭 검수가 필요하다. 검수자는 원문, 기준 날짜, 정책, 모든 항목을 확인하고 `label_review_status=reviewed`, `reviewer`, `reviewed_at=YYYY-MM-DD`를 기록한다. 자동 생성 스크립트는 검수 완료 파일을 덮어쓰지 않는다.

최종 평가는 holdout 전체 검수 후에만 준비된다. 미검수 항목을 최종 분모에 넣지 않으며, 검수된 쉬운 항목만 선택하는 것도 방지한다. holdout 파일은 별도 저장하나 저장소 접근자가 읽을 수 있으므로 암호학적 비공개 데이터는 아니다. 개발 중에는 development만 사용하고 holdout 결과를 보고 같은 세트에 반복 튜닝하지 않는다. 실패를 튜닝 데이터로 옮겼다면 새 독립 holdout 버전을 만들고 해시를 변경해야 한다.

Promptfoo 공식 JavaScript custom provider가 테스트 전용 Java CLI를 실행한다. 이 CLI는 실제 `ExtractionService`, `ChatGPTServiceImpl`, `ChatbotMapper`를 사용하지만 전체 Spring Boot component scan, JPA, Redis, 운영 HTTP API를 실행하지 않는다. 외부 접근 가능한 평가 엔드포인트도 없다. 모델에 전달하는 값은 user_input/reference_date/timezone/mode뿐이며 정답 라벨은 제외한다. 기준 날짜와 서울 시간대를 고정하고 모델·프롬프트 버전·commit·dataset hash를 기록한다.

Baseline은 cb3a501의 프롬프트·매퍼·분류 문자열 비교를 보존한다. 공정 비교를 위해 양쪽 모델, temperature=0, 출력 4096 토큰, 요청 기준 날짜를 동일하게 제어한다. 따라서 서버 시작 시각이 고정되는 baseline 버그의 자연 발생 빈도는 이 비교로 측정하지 않고 별도 Clock 회귀 테스트로 다룬다. 최초 생성 raw 출력과 검증·복구 후 final 결과를 분리한다. raw도 평가 시 같은 스키마/업무 검증기로 점검하지만 수정하거나 재생성하지 않는다.

필요 환경: Java 17, Node >=22.22 (CI는 24), npm. 저장소 루트에서:

```sh
./gradlew test bootJar evaluationClasspath
node evaluation/dataset.cjs
npm ci --prefix evaluation
npm test --prefix evaluation
# 실제 모델 호출 없는 연결 검증
PROMPTFOO_DISABLE_TELEMETRY=1 evaluation/node_modules/.bin/promptfoo eval -c evaluation/smoke.json --no-cache --no-table -o evaluation/reports/smoke.json
# 개발용, 미검수 draft임을 표시 (최종 게이트 PASS 불가)
node evaluation/prepare.cjs --exploratory
# 실제 실행 전 환경에 OPENAI_API_KEY를 설정한다. 키를 명령/저장소에 기록하지 않는다.
# EVAL_MODEL 기본값: gpt-4o-mini-2024-07-18
npm run eval --prefix evaluation
npm run report --prefix evaluation
# 최종 평가: holdout 라벨 전체 검수 후
node evaluation/prepare.cjs
npm run eval --prefix evaluation
npm run report --prefix evaluation
```

실제 모델 실행은 provider 2개 × 3회 반복 × 선택한 사례 수이며 재시도/재생성으로 호출이 더 늘 수 있다. 비용 승인을 받은 범위에서 실행한다. `EVAL_INPUT_USD_PER_MILLION`, `EVAL_OUTPUT_USD_PER_MILLION`을 현재 계약 가격으로 명시하고 usage가 완전할 때만 비용을 계산한다. 설정이 없거나 실패 응답에서 usage를 얻지 못하면 비용은 null(미측정)이다. 부분 토큰 집계에는 measured_calls/complete를 함께 표시한다.

결과: `evaluation/reports/promptfoo.json`, `summary.json`. 자동 생성된 결과는 git에서 제외한다. CI 요약에는 원문 없이 test ID/오류 유형/지표를 출력한다. 상세 artifact에는 합성 입력과 예상/실제 결과가 포함될 수 있으므로 실제 개인정보를 dataset에 넣지 않는다. Promptfoo의 표 출력은 `--no-table`로 끄며 telemetry와 공유를 끈다.

### 지표 정의

| 지표 | 분자/분모·정책 |
|---|---|
| Intent Macro-F1 | EVENT/TODO/PLAN_RECOMMENDATION/EXCEPTION 각각 precision/recall/F1; 해당 평가 세트에 정답 support가 있는 클래스의 비가중 평균. support=0은 별도 표시. INVALID 예측도 confusion matrix에 포함 |
| Date Exact Match | 제목으로 매칭 가능한 항목 중 모든 날짜 필드가 맞는 최대 일대일 매칭 수 / READY 정답 항목 수 |
| Time Exact Match | 동일 방식으로 모든 시간 필드를 비교. null과 키 누락은 다름 |
| Item P/R/F1 | 제목+날짜+시간이 모두 맞는 최대 이분 매칭 수 / 예측·정답 항목 수; 순서 무관, 중복은 1회만 소비 |
| 제목 동치 | NFC, 양끝/연속 공백만 정규화. 의미 유사도 추측 없음. 사람이 승인한 title_aliases만 허용 |
| Full Extraction | intent/status 일치, READY는 스키마·업무 검증·모든 항목 수/내용 일치; 확인/미지원은 빈 항목과 정확한 상태. 전체 요청을 분모로 사용 |
| Schema Valid | OutputSchemas가 실제 사용하는 필수/타입/enum/추가 필드 계약 통과 비율. 출력 본문을 받은 요청만 분모에 포함하며 Java 사전 차단/모델 거부는 스키마 분모에서 제외한다. 유효한 문자열 날짜가 실제 존재하는지는 BusinessValidator/mapper 책임 |
| Recurrence | 저장 모델 미지원이므로 N/A. 미지원 반환 정책 자체는 Full Extraction으로 평가 |
| Runtime Recovery | 최초 전송/출력 오류 후 READY로 복구한 요청 수 / 최초 오류 요청 수. 최초 오류율·최종 기술 실패율 별도. 모호성/미지원은 정상 안전 종료이며 기술 실패와 구분 |
| 호출/지연/비용 | API 호출 합계, 정상 2회를 초과한 추가 호출, 각 요청 wall-clock 지연 합계, 명시 가격+완전 usage일 때 비용. 자동 복구와 의미 정확도를 혼동하지 않음 |

분모 0은 null/N/A이다(정답이 있지만 예측이 0개면 Item P/R/F1은 0). partial 정답은 항목 지표에서만 반영하며 Full Extraction에는 부분 점수가 없다. 예상 정답이 READY가 아닌 요청은 Date/Time/Item 분모에서 제외한다.

## E. 정량 평가 결과

| 지표 | Baseline | Candidate | 변화량 | 측정 조건 |
|---|---|---|---|---|
| Intent Macro-F1 | 미측정 | 미측정 | 미측정 | 실제 API 키·검수 holdout 필요 |
| Date/Time Exact Match | 미측정 | 미측정 | 미측정 | 동일 |
| Item Precision/Recall/F1 | 미측정 | 미측정 | 미측정 | 동일 |
| Full Extraction Success | 미측정 | 미측정 | 미측정 | 동일 |
| Schema Valid Rate (실제 모델) | 미측정 | 미측정 | 미측정 | 동일 |
| Runtime Recovery Rate (실제 모델) | 미측정 | 미측정 | 미측정 | 동일 |
| Recurrence Accuracy | N/A | N/A | N/A | 미지원 |

실제 품질 평가를 바탕으로 한 프롬프트 튜닝/성능 개선 검증은 **미완료**다. 현재 개선은 코드 분석, 고정 응답 baseline 재현, 결정적 회귀 테스트에 근거한다. 실제 LLM 오류 빈도·비결정성·비용·지연의 개선 수치를 만들지 않았다.

## F. CI와 품질 게이트

`.github/workflows/llm-quality.yml`: develop/main 대상 PR 및 작업 브랜치 push에 결정적 Java/데이터/지표/Promptfoo Mock smoke를 수행한다. API Secret은 이 작업에 전달하지 않는다. 실제 LLM 미실행을 job summary에 명시한다. 기존 EC2 배포 gradle.yml은 수정하지 않았다.

실제 모델 평가는 `workflow_dispatch.run_live=true`로만 실행하며 `llm-evaluation` environment의 승인과 `OPENAI_API_KEY` Secret을 사용한다. `pull_request_target`은 사용하지 않는다. environment의 required reviewers와 허용 브랜치 제한을 설정하고, 코드를 검토한 SHA에서만 승인해야 한다. 평가 임의 코드가 Secret을 읽을 수 있으므로 fork 코드를 자동 실행하지 않는다. Workflow 권한은 contents:read이고 PR 댓글/코드 변경 권한은 없다.

결정적 테스트 실패, invalid READY는 즉시 실패한다. 실제 의미 회귀는 동일 사례/provider 각각 최소 3회 반복, paired case-cluster bootstrap 500회 95% 구간과 2%p 후보 임계값을 사용한다. 점추정 하락 >=2%p이고 구간 상한도 -2%p 이하이면 FAIL, 하락 가능성이 있지만 구간이 넓으면 INCONCLUSIVE로 차단한다. 61건 holdout과 모델 비결정성에 대한 초기 정책이며 운영 데이터로 보정해야 한다. 충분한 표본 없이 통계적으로 확정된 품질 기준이라고 주장하지 않는다. 누락/중복 provider 수, API 오류, 모델 불일치, mock 출력, 미검수 데이터는 PASS할 수 없다.

저장소 Settings → Rules → Rulesets(또는 Branch protection)의 develop/main에 `Deterministic quality checks`를 required status로 지정할 수 있다. 이 작업은 저장소 설정을 변경하지 않았다. 수동 live job은 PR마다 자동 실행되지 않으며 skipped job은 GitHub에서 성공처럼 취급될 수 있으므로 **이 체크 하나를 required로 지정하는 것만으로 모든 PR의 실제 LLM 평가가 강제되지는 않는다**. 실제 품질 검수를 강제하려면 관리자가 검토한 PR head SHA의 수동 실행 결과를 확인하는 병합 정책/별도 필수 검증 연계를 마련해야 한다. PR 생성과 merge는 사용자 담당이다.

## G. 테스트와 남은 검증

실행 증거와 정확한 건수는 `evaluation/reports/local-verification.json`을 참조한다. Java 테스트에는 기존 승인 테스트(동시 승인, rollback, 잘못된 소유자/토큰, 거절, TTL/lease)와 새 품질 테스트가 포함된다. 실제 사용자 DB에 접속하지 않는다. repository/context 테스트는 H2를 사용하며, 승인 통합 테스트 20건은 Testcontainers의 격리된 MySQL 8.0.40 및 Redis 7.2-alpine 컨테이너를 실제로 사용한다. 테스트 실행에는 Docker가 필요하다. XML/보고서의 skipped 값으로 실행 여부를 확인한다.

추가로 필요한 검증: 실제 모델/API 권한과 schema 수용, 200건 사람 검수, 동일 조건 baseline/candidate 반복 평가, 다양한 자연어 및 계획 추천 라벨 검토, 모델 가격/usage 비용, 운영 DB dialect와 운영 Redis 환경, required status/environment 설정. 운영 배포는 수행하지 않는다.

## H. 기술적 판단

1. 보편적 방법: strict structured output, 로컬 업무 검증, 오류별 제한 재시도, Clock 주입, 모델 결과와 코드 테스트 분리, 검수 holdout 평가 및 회귀 gate.
2. PlanWith에 맞춘 설계: Event/Todo/계획 추천의 실제 저장 모델 유지, 서울 기준 상대 날짜, 반복 모델 부재를 명시, 생성/READY/승인 공통 검증, 기존 ledger/Lua 트랜잭션과 충돌 최소화.
3. 포트폴리오에서 증명 가능한 부분: 정규식 수용 문제와 날짜 역전의 baseline 재현, 실패 출력의 승인 공개 차단, HTTP 오류 및 복구 예산 테스트, 순서/중복을 고려한 항목 평가, 검수되지 않은 라벨·Mock 결과로 성능을 과장하지 않는 평가 설계. 실제 모델 정확도 개선은 측정 후에만 주장할 수 있다.

## 공식 참고 자료

- [OpenAI Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs): strict schema, 필수 nullable 필드, refusal 및 지원 모델.
- [Promptfoo JavaScript provider](https://www.promptfoo.dev/docs/providers/custom-api/): custom provider 계약.
- [Promptfoo configuration reference](https://www.promptfoo.dev/docs/configuration/reference/): 반복 실행/캐시/출력 설정.

조회 당시 npm의 Promptfoo 최신 공개 버전은 0.124.1, 최소 Node는 22.22.0이었다. 해당 버전과 의존성을 lockfile로 고정했다.
