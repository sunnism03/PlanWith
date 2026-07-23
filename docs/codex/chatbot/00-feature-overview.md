# Chatbot Feature Overview

## User flow

Chatbot은 자연어 요청을 받아 다음 기능을 보조한다.

- 일정 자동 기입
- 투두 자동 기입
- 계획 추천

공개 저장소 기준으로 `/chatbot/messages`는 다음 흐름을 가진다.

```text
POST /chatbot/messages
→ request를 Redis에 저장하고 token 발급
→ OpenAI로 intention 분석
→ intention을 Redis에 반영
→ OpenAI로 일정·투두 생성 응답 요청
→ JSON 파싱
→ 생성 후보를 Redis에 저장
→ token과 후보 목록 반환
```

사용자가 결과를 수락하거나 거절하는 별도 Endpoint가 있다.

```text
POST /chatbot/message
→ token으로 Redis 데이터 조회
→ user 검증
→ accept=false: Redis 데이터 삭제 시도
→ accept=true:
   ├─ EVENT: PersonalEvent 생성 반복
   └─ TODO/PLAN_RECOMMENDATION: PersonalTodo 생성 반복
```

로컬 코드에서 최종 흐름을 다시 확인해야 한다.

## Confirmed review points

### External API

- intention 분석과 생성 응답이 별도 OpenAI 요청인지 확인
- HTTP client bean 생성 방식 확인
- connect/read timeout 확인
- connection pool 확인
- retry 정책 확인

### Redis

- request 저장 시 key와 token mapping이 별도로 저장되는지 확인
- token 조회 한 번에 Redis GET이 몇 번 발생하는지 확인
- update 시 기존 데이터를 반복 조회하는지 확인
- 수락·거절 이후 모든 관련 key가 삭제되는지 확인
- TTL과 token 재사용 가능 시간 확인

### Mapping

- OpenAI content에서 JSON을 추출하는 방식 확인
- JSON tree를 여러 번 parsing하는지 확인
- 목록 길이에 따라 CPU·memory가 어떻게 증가하는지 확인
- 예외 시 stack trace 출력 비용과 정보 노출 확인

### Persistence

- 생성된 Event/Todo 수만큼 service와 repository 호출이 반복되는지 확인
- 각 item에서 User·Category를 반복 조회하는지 확인
- 전체 저장의 transaction 원자성 확인
- 알람 생성이 item마다 추가 I/O를 발생시키는지 확인

## Core invariants

1. 한 token은 한 사용자의 데이터에만 접근 가능해야 한다.
2. accept=false이면 일정·투두가 생성되지 않아야 한다.
3. accept=true 한 번은 후보 목록을 정확히 한 번만 반영해야 한다.
4. 같은 token의 동시 수락으로 중복 일정·투두가 생성되면 안 된다.
5. 일부 item 저장 실패 시 허용된 정책에 따라 전체 rollback 또는 명시적 부분 성공이어야 한다.
6. 성공적으로 소비된 token은 다시 사용할 수 없어야 한다.
