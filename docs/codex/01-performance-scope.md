# Performance Scope and Priorities

## P1 — Chatbot

### Target endpoints

로컬 Controller에서 최종 확정한다.

현재 후보:

- `POST /chatbot/messages`
- `POST /chatbot/message?accept={accept}&isAlarmed={isAlarmed}`

### Goals

1. OpenAI 호출을 포함한 전체 응답 시간 구성 파악
2. OpenAI를 mock했을 때 서버 자체 처리 한계 측정
3. Redis 접근 횟수와 read-modify-write 비용 파악
4. 일정·투두 개수 증가에 따른 DB 처리 비용 파악
5. 동일 token 중복 수락 시 idempotency 검증
6. 부분 실패 시 DB·Redis 일관성 검증
7. 확인된 병목 하나를 개선하고 동일 조건으로 재측정

### Out of scope at first

- 실제 OpenAI API 대규모 부하
- 모든 서비스 API의 일괄 최적화
- UI 렌더링
- 운영 환경 테스트
- 측정 근거 없는 비동기 전환

---

## P2 — Group concurrency

### Target operations

- 그룹 초대 생성
- 그룹 초대 수락
- 그룹 초대 거절
- 그룹 멤버 생성
- 초대 알림 생성·삭제

### Goals

1. 중복 초대 방지 로직의 동시성 안전성 검증
2. 동일 초대의 중복 수락 시 그룹 멤버 중복 여부 검증
3. 수락·거절 경합 시 최종 상태 검증
4. 초대 상태, 그룹 멤버, 알림 사이의 일관성 검증
5. DB unique constraint, optimistic/pessimistic lock 필요성 비교
6. 확인된 동시성 문제 하나를 해결하고 재현 테스트 통과

### Secondary load candidates

동시성 정확성 검증 이후 필요하면 다음을 측정한다.

- 그룹 목록 조회
- 그룹 멤버 조회
- 그룹 생성
- 그룹 일정·투두 조회

---

## Completion criteria

작업 완료는 다음을 모두 만족해야 한다.

- 재현 가능한 JMeter 시나리오 존재
- 개선 전 baseline 존재
- Actuator 또는 Pinpoint 근거 존재
- 기능 정확성 assertion 통과
- 개선 전후 조건 동일
- p95·p99·throughput·error rate 비교
- 동시성 작업은 최종 DB invariant 검증
- 변경 이유와 한계 문서화
