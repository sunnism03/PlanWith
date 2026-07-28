# Phase 4A — Group Event CRUD Code Map

## Input files

- `performance/plans/context-verification.md`
- `GroupEventController`, `GroupEventServiceImpl`, `GroupEventConverter`
- `GroupEvent`, `GroupEventMapping`, their repositories
- `GroupSettingServiceImpl`, `GroupSettingRepository`
- `AlarmServiceImpl`, `Alarm`, `AlarmRepository`

## Actual CRUD surface

| Operation | Endpoint | Controller method | Service method | Classification |
|---|---|---|---|---|
| Group month list | `GET /groupEvents/{group_id}/events` | `getGroupEvents` | `getGroupEvents` | **FACT** |
| Assigned-user month list | `GET /groupEvents/{group_id}/events/{user_id}` | `getSomeOneGroupEvents` | `getGroupSomeOneEvents` | **FACT** |
| Current user's all-group month list | `GET /groupEvents` | `getGroupMyEvents` | `getGroupMyEvents` | **FACT** |
| Create | `POST /groupEvents/{group_id}/events` | `createGroupEvents` | `createGroupEvent` | **FACT** |
| Update | `PATCH /groupEvents/{group_id}/events/{group_event_id}` | `updateGroupEvents` | `updateGroupEvent` | **FACT** |
| Delete | `DELETE /groupEvents/{group_id}/events/{group_event_id}` | `deleteGroupEvents` | `deleteGroupEvent` | **FACT** |
| Single-event read | 없음 | 없음 | 없음 | **FACT** |

## DTO contract

- **FACT**: create body requires `title`, `startDate`, `endDate`, `isAlarmed`, `groupId`, `assignedMemberList`; times are optional.
- **FACT**: update body requires `groupEventId`; other fields are optional.
- **FACT**: response contains event fields, `groupId`, `categoryId`, `assignedUserIds`.
- **FACT**: list response wraps group list, group-category list and group-event list.

## Create flow

1. `JWTFilter` loads User by email.
2. Controller binds path group ID and create body.
3. `isGroupMemberExisted(groupId, requester)` executes membership exists query.
4. Body `groupId` must equal path group ID.
5. converter creates one `GroupEvent`; group entity is loaded and attached.
6. `groupEventRepository.save` persists the shared event row.
7. Each assigned user triggers GroupSetting lookup and individual GroupEventMapping save.
8. assigned users are queried again.
9. if alarmed, each assigned user triggers User lookup, mapping lookup and Alarm save.

- **FACT**: service method has no `@Transactional`.
- **FACT**: one GroupEvent row is shared; assignees are represented by separate mapping rows.
- **HYPOTHESIS**: mapping/alarm count grows linearly and partial create is possible if a later assignee fails.

## Read flows

- **FACT**: all reads first derive a month range.
- **FACT**: group list read checks requester membership, loads group category and group, queries event list, then queries assigned users once per event.
- **FACT**: specific-user read validates both target and requester membership.
- **FACT**: current-user all-group read loads groups, categories and assigned events.
- **HYPOTHESIS**: per-event `getGroupEventRelatedUsers` is an N+1 query candidate.
- **UNKNOWN**: actual SQL count and latency require trace or datasource observation.

## Update flow

1. Requester membership exists check.
2. path `groupEventId` entity lookup.
3. converter builds a new entity using body `groupEventId` and old/default fields.
4. path group entity is assigned and repository `save` is called.
5. existing assigned users are queried.
6. removed users call `deleteByGroupSettingId`.
7. added users cause individual GroupSetting lookup and mapping save.
8. assigned users are queried again.
9. alarmed result causes alarm lookup/update or insert per assigned user.

- **FACT**: no check requires body `groupEventId` to equal path `group_event_id`.
- **FACT**: no check requires the loaded event to belong to path `group_id`.
- **FACT**: any current group member can call update; author/leader role is not checked.
- **FACT**: `deleteByGroupSettingId` is not constrained by group event ID and can delete that member's mappings for other events.
- **FACT**: service update has no encompassing transaction.
- **FACT**: turning `isAlarmed` false does not explicitly delete existing alarms.

## Delete flow

1. Requester membership exists check for path group.
2. `groupEventRepository.deleteById(groupEventId)`.

- **FACT**: event existence/group ownership is not explicitly verified before delete.
- **FACT**: any member of the path group may request delete.
- **FACT**: `GroupEvent.groupEventMappings` has cascade ALL; Mapping alarm has cascade ALL and orphan removal.
- **UNKNOWN**: actual DB foreign keys and cascade behavior are unavailable because no migration/schema file exists and live DB was not queried.

## Data relationships and constraints

- **FACT**: `GroupEvent` → `GroupEventMapping` is one-to-many.
- **FACT**: `GroupEventMapping` → `GroupSetting` is many-to-one.
- **FACT**: `GroupEventMapping` → `Alarm` is one-to-one mapped side.
- **FACT**: entity join annotations use `ConstraintMode.NO_CONSTRAINT`.
- **FACT**: there is no entity `@Version`, repository lock or declared unique constraint on mapping.
- **UNKNOWN**: live MySQL may contain constraints/indexes not represented in source.

## Transaction and exception map

- **FACT**: Group Event service CRUD methods have no `@Transactional`.
- **FACT**: mapping bulk delete has its own repository transaction.
- **FACT**: alarm create/update methods for Group Event have no transaction annotation.
- **FACT**: not-found and group-membership failures use custom exceptions.
- **HYPOTHESIS**: event, mappings and alarms can be partially updated across independent repository transactions.

## Scope conclusion

- Group Event CRUD is the analysis scope.
- Group Invitation code was not analyzed except where a shared Alarm type made a source file contain unrelated methods.
- Single-event read is `NOT_APPLICABLE` to JMeter until an actual endpoint exists.

## Completion check

- CRUD endpoints and missing single read recorded: **PASS**
- GroupEvent/Mapping/Alarm relationships recorded: **PASS**
- Permission and transaction structure recorded: **PASS**
- FACT/HYPOTHESIS/UNKNOWN classifications: **PASS**

