# Sync Contract — ScheduledWorkout (Milestone 4H-A4)

Status: **DESIGN CONTRACT (documentation only).** No implementation, migration,
or backend change is part of this document.

## 1. Purpose and explicit non-goals

**Purpose:** Define the cloud synchronization contract for `ScheduledWorkoutEntity`
so that schedule definitions can be synchronized across devices signed into the
same account, consistent with the existing sync architecture (4H-A1…4H-A3).

**Non-goals (v1):**
- No recurrence exceptions, skip/snooze state, or schedule completion state.
- No synchronization of alarm instances or notification delivery state.
- No time-zone conversion or storage of offsets/zone IDs.
- No new backend entity-specific validation beyond the generic sync contract.
- No changes to workout cascade behavior beyond what is stated as a prerequisite.

## 2. Settled product semantics

1. **Account-wide schedules:** schedules sync across devices signed into the same
   account.
2. **Wall-clock semantics:** preserve the schedule's local calendar/time fields
   exactly as entered. A 7:30 AM schedule remains 7:30 AM local time on each
   device, even across different time zones. No UTC conversion, no offset/zone ID
   stored in v1.
3. **Reminder preferences sync; alarm instances do not.** Each device
   independently schedules/cancels its own local notifications from the synced
   schedule definition.
4. **Minimal schedule scope:** sync the existing recurring weekly or one-time
   schedule definition only.
5. **Workout dependency:** references use the workout's stable `workoutSyncId`,
   never Room's local `workoutId`.
6. **Workout deletion safety:** do not allow the existing Room cascade to
   silently destroy pending schedule mutations or stable sync identity. Prefer
   retaining the schedule's sync record/tombstone and hiding it when its parent
   is deleted. (See §11 for the implementation prerequisite.)
7. **Guest mode:** schedules remain local-only for guest users; no mutations.
8. **Owner isolation:** all cloud records and outbox mutations are bound to the
   authenticated Firebase owner; never trust a client-supplied owner ID.

## 3. Entity identity and payload schema

- Entity type (wire name): `SCHEDULED_WORKOUT` (already in `SyncEntityTypes` and
  backend `SyncEntityType`).
- Stable cloud identity: `schedule.syncId` UUID (already present, unique index).
- Local primary key: `scheduleId` (Room autoincrement, local-only, never in payload).
- Owner: `ownerFirebaseUid`.
- Schema version: `1` (consistent with `SyncPayloadFactory.SCHEMA_VERSION`).

Payload (JSON object):

```json
{
  "schemaVersion": 1,
  "workoutSyncId": "<workout sync UUID>",
  "dayOfWeek": 1,
  "scheduledDateKey": 20260101,
  "startMinutes": 450,
  "reminderEnabled": true,
  "reminderMinutesBefore": 30,
  "reminderNote": "Bring towel"
}
```

Note: there is no `addedAt` field. `ScheduledWorkoutEntity` has no persisted creation timestamp; the mutation's `clientUpdatedAt` carries the write time. Do not substitute `updatedAt` as `addedAt` without an explicit contract change.

- `workoutSyncId`: required, valid UUID, stable workout reference.
- `dayOfWeek`: required integer 1–7 (ISO: Monday = 1).
- `scheduledDateKey`: optional integer `yyyyMMdd` (local calendar date) or null.
- `startMinutes`: required integer 0–1439.
- `reminderEnabled`: required boolean.
- `reminderMinutesBefore`: required integer >= 0.
- `reminderNote`: required string (may be empty).

No Room primary keys (`scheduleId`, `workoutId`) and no owner UID may appear.

## 4. Field semantics

- `dayOfWeek`: ISO day of week. For a recurring weekly schedule this is the
  target day. For a one-time schedule (`scheduledDateKey != null`) this is the
  ISO day of that date and must be consistent with it.
- `scheduledDateKey`: when non-null, the schedule fires only on that local
  calendar date. When null, the schedule recurs weekly on `dayOfWeek`.
- `startMinutes`: minutes since local midnight (0–1439) for the workout start.
- `reminderEnabled`: whether a local notification should be scheduled.
- `reminderMinutesBefore`: minutes before `startMinutes` to trigger the reminder.
- `reminderNote`: text shown in the reminder notification.

## 5. Validation rules (recurring vs one-time)

- `dayOfWeek` must be 1–7.
- `startMinutes` must be 0–1439.
- `reminderMinutesBefore` must be >= 0.
- If `scheduledDateKey` is non-null, it must be a valid `yyyyMMdd` local date
  and its ISO day-of-week must equal `dayOfWeek`. Contradictory combinations
  are invalid.
- If `scheduledDateKey` is null, the schedule is recurring weekly.
- A schedule must reference a resolvable workout (`workoutSyncId`).

## 6. Create / update / delete / restore and stable syncId

- `syncId` is a stable random UUID generated **once at row creation** by the
  Room entity default (`UUID.randomUUID()`). It is never derived from fields and
  is never regenerated on update. There is no natural logical identity for
  independently created schedules; each instance keeps its own `syncId`.
- When syncId is generated: at `INSERT` time (entity default).
- When it is preserved: across retries, updates, and device sync (the same row's
  syncId is reused). It is immutable once written.
- Delete and re-add: `ScheduleRepository.deleteEntry` soft-deletes the row;
  `addEntry` inserts a **new** row with a **new** syncId. There is no automatic
  restore of the original identity. A re-added schedule is a new logical
  record. (This differs from 4H-A2 association restore; schedules are
  lower-risk and re-creation is acceptable.)
- Concurrent creation on different devices produces **separate** schedule records
  (different syncIds). They are not deduplicated merely because their fields
  match.
- Create: insert local row with `syncStatus = PENDING_UPLOAD`, produce UPSERT.
- Update: mutate the same row, set `PENDING_UPLOAD`, produce UPSERT (reuse
  `syncId`; `baseVersion` is the last acknowledged server version, not a
  locally invented or incremented value).
- Delete: soft-delete (`deletedAt` set, `PENDING_DELETE`), produce DELETE.
- Guest: all writes remain local-only (`LOCAL_ONLY`, no outbox mutation).

## 7. Owner isolation and guest-local-only

- All DAO reads/writes scoped by `ownerFirebaseUid`.
- Outbox mutations only for authenticated owners; guest schedules never upload.
- Owner is captured from `OwnerProvider.currentOwner()` at write time and
  re-checked inside the transaction (account-switch protection).

## 8. Outbox atomicity, coalescing, and ordering

- Schedule write and outbox mutation are written in the same transaction under
  `SyncOperationLocks`.
- Coalescing/superseding obey `everTransmitted`, mutationId, baseVersion, and
  owner constraints (as in 4H-A2).
- Upload order: `SCHEDULED_WORKOUT` after `WORKOUT` (per existing proposal §11).
- A schedule mutation must not be sent until its referenced workout has been
  successfully synchronized and has a valid acknowledged server version/identity.
  The client must resolve the workout's syncId to an acknowledged server version
  before including the schedule in a push batch. This follows the existing
  dependency handling in `SyncPushCoordinator` (unresolved parents are skipped,
  not sent).
- Retry behavior when the workout is not ready:
  - pending (not yet synced): retain schedule mutation as `PENDING_UPLOAD`,
    retry on next sync cycle.
  - conflicted: block the schedule mutation (`LOCAL_PENDING`/conflict-blocked)
    until the workout conflict is resolved.
  - rejected: mark the schedule mutation blocked/failed; do not auto-retry as
    transient.
  - deleted (workout tombstone): the schedule's parent is unavailable; retain
    the schedule sync record/tombstone (see §11) and do not upload the schedule
    as an active record.
- On pull, a missing workout keeps the staged record `WAITING_DEPENDENCY`
  (durable, retried when the workout arrives).

## 9. Pull / apply behavior

- Stage pulled records by `syncId`.
- Resolve `workoutSyncId` to the local workout; if missing, mark
  `WAITING_DEPENDENCY` (durable, retried when workout arrives).
- Apply remote UPSERT by stable syncId with server-version-monotonic writes.
- Apply remote DELETE by syncId even if local row absent (create tombstone row
  or mark staged `TOMBSTONE_ONLY`).
- Preserve durable tombstone state so an older UPSERT cannot resurrect.
- Remote apply must not create an outbox mutation.
- Conflict blocking for genuine identity mismatches (same syncId, different
  workout reference).
- No RESTORE path is defined for schedules: deleting and re-adding creates a
  new schedule with a new syncId (see §6). Workout restore behavior does not
  imply schedule restore.

## 10. Device-local reminder lifecycle

- `WorkoutReminderScheduler` is device-local. Sync transfers the schedule
  definition only.
- On local create/update: call `schedule(entry)` (idempotent replacement of the
  same alarm identified by `scheduleId`).
- On delete: `cancel(scheduleId)`.
- On remote apply (upsert): after the local row is updated, call `schedule(entry)`
  to replace the alarm; if `reminderEnabled=false`, `schedule` cancels.
- On remote delete: `cancel(scheduleId)`.
- On restore: `schedule(entry)`.
- `rescheduleAll` remains the recovery path after exact-alarm permission grants.
- Because alarms are keyed by `scheduleId`, re-scheduling is idempotent: cancel
  then schedule, or schedule replacing the same PendingIntent.
- Parent deletion: on local workout soft-delete, cancel local reminders for all
  of its schedules while retaining the schedule rows and sync identities. On
  remote workout tombstone, cancel reminders for schedules whose parent is now
  unavailable. On workout restore, reschedule reminders for still-active
  schedules. Alarms remain device-local throughout.

## 11. Workout deletion and cascade safety

**Current behavior:** `WorkoutRepository.deleteWorkout` soft-deletes the workout
(`deletedAt` set, row retained) and produces a DELETE mutation. Because the
workout row is retained, the `CASCADE` FK on `scheduled_workout.workoutId` does
not fire during a normal delete, so schedules are retained locally. The UI hides
schedules whose workout is unavailable (`entriesUiState` skips entries with no
matching workout).

**Preferred contract:** retain the schedule's sync record/tombstone and hide it
when its parent workout is deleted. Do not automatically cloud-delete schedules
because their workout was deleted. A schedule whose workout is soft-deleted
remains in the local table with its `workoutSyncId` intact (resolved through the
retained workout row); on pull of a workout tombstone, the local workout row is
soft-deleted but present, so the schedule is hidden by UI filtering and its
sync identity is preserved.

**Missing vs deleted workout (apply-time distinction):**
- Missing workout (no local row for `workoutSyncId`): the schedule remains
  durably staged as `WAITING_DEPENDENCY` and is retried when the workout arrives.
  Do not mark it applied or discarded.
- Locally present but tombstoned workout (soft-deleted row exists): the schedule
  is retained and hidden (UI filters by workout availability). It is not left
  waiting forever; apply proceeds and reminders are cancelled. No new status enum
  is required; reuse existing `APPLIED` plus local hidden state.
- Remote workout tombstone known but local row absent: keep the schedule staged
  as `WAITING_DEPENDENCY` only if the workout may still arrive; if the workout's
  tombstone is already known locally, retain the schedule as a hidden tombstone
  (follow existing tombstone retention) rather than retrying indefinitely.

**Remote workout tombstone:** reminders for a hidden/unavailable schedule must be
cancelled (device-local).

**Workout restore:** if a soft-deleted workout is restored, its schedules
become visible again and reminders can be rescheduled.

**Prerequisite if physical deletion is introduced:** the current `CASCADE` FK
would destroy schedule rows and identities if a workout is ever physically
deleted. Before sync, either keep workouts soft-delete-only (current behavior)
or change the FK to `NO ACTION` and manage schedule retention explicitly in
`WorkoutRepository`. Do not use `SET NULL` unless `workoutId` becomes nullable,
which would lose the stable local reference; the schedule can retain
`workoutSyncId` in its payload regardless.

## 12. Compatibility, migration, and legacy records

- v13→v14 foundation migration already backfills `scheduled_workout` with
  owner='guest', new syncId, `LOCAL_ONLY`. Guest schedules never upload.
- Legacy rows with `scheduledDateKey = null` are recurring weekly.
- No payload contract exists yet; first upload-capable version defines v1.
- Existing local schedules keep their `syncId` and are normalized to guest owner.

## 13. Backend contract

- Backend already accepts `SCHEDULED_WORKOUT` in `SyncEntityType` and the generic
  sync contract (no entity-specific validation).
- No backend changes required for a basic implementation.
- Backend stores payload as a generic JSON map; validation of field ranges
  (dayOfWeek, startMinutes, etc.) is not performed server-side. Client must
  validate before upload.
- There is no convergence/deduplication by field equality: each schedule keeps
  its own random syncId, and independently created schedules remain separate
  records. The backend keys records by `(user, entityType, syncId)` only.

## 14. Test plan

Android unit:
- stable random syncId generated once at creation and preserved across edits,
  retries, and device sync.
- independent and concurrent creations produce distinct syncIds (no
  deduplication by field equality).
- delete creates a new syncId on re-add (no restore of original identity).
- add/update/delete/re-add lifecycle.
- guest local-only (no outbox).
- owner isolation and account-switch.
- payload excludes Room PKs and owner UID.
- validation of recurring vs one-time combinations.

Android Room migration:
- v13→v14 (and relevant earlier versions) backfill owner/syncId/status/indexes
  for `scheduled_workout`.

Android repository/outbox:
- atomic write + outbox production.
- coalescing/superseding under `everTransmitted`.
- no duplicate mutations on repeated toggles.

Push/ack:
- dependency ordering (workout before schedule).
- ack metadata updates.

Pull/dependency:
- missing workout → `WAITING_DEPENDENCY`; retry after workout arrives.
- remote delete by syncId even if local row absent.
- tombstone durability and version guards.
- conflict blocking.

Reminder:
- schedule/cancel/reschedule idempotency keyed by `scheduleId`.
- remote apply triggers reschedule; delete triggers cancel.

Backend (optional):
- no convergence tests required; schedules use random syncIds and are not
  deduplicated by field equality. Standard version/conflict tests apply.

## 15. Open implementation risks (not settled decisions)

1. **Cascade safety (prerequisite, not a blocker under current delete path):**
   Normal `WorkoutRepository.deleteWorkout` soft-deletes the workout and retains
   the row, so the `CASCADE` FK does not fire and schedule rows/identities are
   preserved. The remaining prerequisite is to verify that *every* workout
   deletion path used by the app is soft-delete-only. Any path that physically
   deletes a workout row must be changed or made sync-safe before schedule sync
   is implemented. Do not use `SET NULL` without a supported schema/lifecycle
   design.
2. **Time-zone ambiguity** is settled as wall-clock for v1, but cross-device
   users in different zones will see the same wall-clock time; product must
   accept this or revisit.
3. **Reminder permission/delivery** varies by device (exact-alarm access); sync
   cannot guarantee notification delivery.
4. **Payload size / field validation** is client-side only; malformed clients
   could store invalid data.
5. **No recurrence engine** beyond weekly day-of-week; advanced recurrence is out
   of scope.

Settled decisions (§2) are not open questions. No further product decisions are
required to begin a minimal v1 implementation once the cascade prerequisite (risk 1)
is resolved.