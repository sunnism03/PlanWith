# Group Feature Overview

## Target flow

P2에서는 그룹 전체 기능보다 초대와 멤버 등록의 상태 전이를 우선한다.

```text
Leader sends invitation
→ group validation
→ leader authorization
→ target user lookup
→ pending invitation duplicate check
→ existing member check
→ GroupInvitation save
→ Alarm create
```

수락 흐름 후보:

```text
Invitee accepts invitation
→ invitation lookup
→ user authority check
→ invitation status ACCEPTED
→ invitation save
→ GroupSetting member save
→ invitation alarm delete
```

거절 흐름 후보:

```text
Invitee declines invitation
→ invitation lookup
→ user authority check
→ invitation status DECLINED
→ invitation save
→ invitation alarm delete
```

실제 로컬 코드와 transaction 경계를 다시 확인한다.

## Primary entities

- `Group`
- `GroupInvitation`
- `GroupSetting`
- `Alarm`
- `User`

## Primary repositories

- `GroupRepository`
- `GroupInvitationRepository`
- `GroupSettingRepository`
- `AlarmRepository`
- `UserRepository`

## Important invariants

1. 같은 그룹과 같은 사용자에게 활성 PENDING 초대는 최대 1개다.
2. 같은 그룹과 같은 사용자의 GroupSetting은 최대 1개다.
3. ACCEPTED 초대는 정확히 한 번만 멤버 생성으로 이어진다.
4. DECLINED 초대는 멤버를 생성하지 않는다.
5. 한 초대의 최종 상태는 ACCEPTED 또는 DECLINED 중 하나다.
6. ACCEPTED라면 멤버가 존재하고 초대 알림은 정책대로 제거된다.
7. DECLINED라면 멤버가 존재하지 않고 초대 알림은 정책대로 제거된다.
8. 같은 요청의 재시도 결과가 예측 가능해야 한다.

## Endpoint mismatch warning

첨부 API 명세와 공개 저장소 코드의 그룹 초대 prefix가 다를 수 있다.
JMeter 작성 전에 로컬 Controller의 `@RequestMapping`과 method mapping을 확인한다.
