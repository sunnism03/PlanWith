# Group Concurrency Scenarios

## G1 — Duplicate invitations

### Race

같은 leader가 같은 group과 같은 target user에 대해
동시에 여러 초대 요청을 보낸다.

### Suspected pattern

```text
Request A: exists=false
Request B: exists=false
Request A: save
Request B: save
```

### Load

- same group
- same target user
- 2 / 5 / 10 concurrent requests

### Invariants

- PENDING invitation row <= 1
- invitation alarm <= 1
- 성공 또는 충돌 응답이 일관적
- 5xx 없음

### Verify

- DB unique constraint
- transaction isolation
- duplicate key handling
- application-level pre-check only인지

---

## G2 — Double accept

### Race

같은 `groupInvitationId`에 대해 같은 사용자가 동시에 여러 수락 요청을 보낸다.

### Possible failure

- GroupSetting 중복 row
- 동일 알림 중복 삭제 오류
- 모든 요청이 200이지만 데이터 중복
- invitation status 검증 없이 재수락

### Invariants

- ACCEPTED invitation = 1
- `(group_id, user_id)` membership = 1
- invitation alarm = 0 또는 정책상 완료 상태 1
- unexpected 5xx = 0

---

## G3 — Accept versus decline

### Race

동일 초대에 accept와 decline 요청을 동시에 보낸다.

### Invariants

허용할 정책을 먼저 확정한다.

권장 최소 불변식:

- 최종 status는 하나
- status=ACCEPTED이면 membership=1
- status=DECLINED이면 membership=0
- alarm 상태는 최종 status와 일치
- status와 membership이 모순되면 실패

### Observe

- 마지막 저장이 덮어쓰는지
- dirty checking과 explicit save 순서
- lock/version 여부
- alarm delete의 중복 호출
- transaction rollback

---

## G4 — Invitation and membership race

### Race

한 요청은 초대를 생성하고,
다른 흐름은 같은 사용자를 그룹 멤버로 등록한다.

### Invariants

- 이미 멤버라면 새 PENDING invitation 없음
- membership <= 1
- 초대와 멤버 상태가 모순되지 않음

---

## Candidate solution comparison

문제를 재현한 뒤 다음 대안을 비교한다.

### Database unique constraint

장점:

- 모든 애플리케이션 인스턴스에서 최종 방어선
- race를 DB에서 차단

검토:

- 기존 중복 데이터
- constraint column 조합
- 예외 매핑

### Optimistic locking

장점:

- 동일 invitation status 경합 감지

검토:

- `@Version`
- retry 정책
- 충돌 응답

### Pessimistic locking

장점:

- 중요한 상태 전이를 직렬화

검토:

- lock 범위
- timeout
- throughput
- deadlock

### Redis/distributed lock

초기 선택으로 가정하지 않는다.

검토:

- 다중 인스턴스
- lock expiry
- fencing
- DB constraint 병행 여부
