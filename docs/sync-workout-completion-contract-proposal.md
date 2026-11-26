# Sync Contract Proposal — WorkoutCompletion (Milestone 4H-A5 candidate)

Status: **DESIGN PROPOSAL ONLY.** Not approved for implementation. No production
code, migration, or backend change is part of this document. Follows the
convention of `docs/sync-data-contract-proposal.md` and
`docs/sync-scheduled-workout-contract.md`.

## 1. Scope and meaning

`WorkoutCompletionEntity` is a historical per-workout, per-local-calendar-day
completion-count record. It is written once when a workout execution finishes
(`WorkoutExecutionScreen` → `OrganizerViewModel.recordWorkoutCompletion` →
`WorkoutCompletionRepository.recordCompletion`). The app stores only
`completedExercises`, `totalExercises`, and the local `dateKey`; no duration,
notes, set results, or session id are stored (verified in code).

The local PK is `(dateKey, workoutId)` — a natural logical key. Repeated
completion of the same workout on the same local date currently uses
`@Insert(REPLACE)`, overwriting counts and generating a fresh random `syncId`.

Non-goals (v1): no DELETE/RESTORE, no per-set results, no session records, no
recurrence of history, no backend entity-specific validation.

## 2. Identity and payload

Deterministic cloud identity (proposed):

```
UUID.nameUUIDFromBytes("WORKOUT_COMPLETION:v1|$ownerFirebaseUid|$workoutSyncId|$dateKey".toByteArray(UTF_8))
```

Payload v1 (exact fields):

```json
{
  "schemaVersion": 1,
  "workoutSyncId": "<workout sync UUID>",
  "dateKey": 20260101,
  "completedExercises": 8,
  "totalExercises": 10,
  "completedAt": "2026-01-01T07:00:00Z"
}
```

- `dateKey`: `yyyyMMdd`, preserved as the user's local calendar date; no UTC
  conversion.
- `completedAt`: ISO-8601 instant via `SyncTimestamps`; does not redefine
  `dateKey`.
- No Room IDs (`dateKey` is retained as a semantic field, not a local PK
  substitute), no `workoutId`, no Firebase UID, no session id, no `addedAt`.

## 3. Same logical completion from multiple devices

Because identity is deterministic from owner + workoutSyncId + dateKey, two
devices completing the same workout on the same date address the same cloud
record. Current local `REPLACE` would otherwise create duplicates with random
IDs.

**Recommended merge rule (requires product approval):** treat the record as a
snapshot of one completion event. On conflict, keep the snapshot with the
later `completedAt`; ties break by larger `completedExercises`, then larger
`totalExercises`. Do **not** merge fields independently (e.g., max completed
with max total), because that can produce an impossible combination that never
occurred on either device.

**Last-write-wins by server receipt order is rejected:** it can regress counts
when a device with an earlier completion syncs later. Timestamp-based selection
avoids that, but relies on reasonably synchronized device clocks.

**Backend impact:** the generic backend keys by `(user, entityType, syncId)`
and enforces `baseVersion`. A second initial add (`baseVersion=0`) would receive
`CONFLICT`. Minimal v1 therefore requires the client to resolve the conflict by
reading `serverPayload`, applying the snapshot rule above, and issuing a new
UPSERT with the acknowledged `baseVersion`. This needs **no backend change**.
If product instead wants server-side convergence/first-writer-wins, a narrow
backend rule (as in 4H-A3 favorites) would be required — a separate decision.

Open product decision: confirm the snapshot tie-break rule and clock-skew
acceptance. Until approved, do not implement merge behavior.

## 4. Workout definition changes

The stored counts are a snapshot at completion time. Do not recalculate old
completions from the current workout definition (exercise count may change).
If a workout's definition changes, future completions use new counts; prior
rows remain untouched. No product decision needed beyond preserving this
existing behavior.

## 5. Date and timestamp semantics

- `dateKey` is a local calendar date (`yyyyMMdd`) and is preserved verbatim.
- `completedAt` is an instant for ordering/conflict decisions; it does not
  redefine `dateKey`.
- Never convert `dateKey` through UTC.

## 6. Workout dependency and historical retention

- Push only after the referenced workout is acknowledged with a valid server
  version/identity; use existing `SyncPushCoordinator` dependency gating.
- Pull with a missing workout: keep the staged record durably
  `WAITING_DEPENDENCY`, retry when the workout arrives.
- A workout tombstone must not produce completion DELETE mutations, and must
  not erase completion history. Historical counts are shown by date, so the
  local completion row is retained even when the workout is unavailable.
- All production workout deletion paths are currently soft-delete-only
  (`WorkoutRepository.deleteWorkout`); the FK `onDelete=CASCADE` on
  `workout_completion.workoutId` does not fire. Prerequisite before
  implementation: verify no physical workout-delete path is introduced; if
  one is, make it sync-safe (retain completion rows/identity).

## 7. Owner and guest behavior

- Guest completions remain local-only (`LOCAL_ONLY`, no outbox mutation).
- Identity includes `ownerFirebaseUid`; stable across devices on the same
  account.
- Owner is captured from `OwnerProvider` at write time and re-checked inside
  the transaction; never accepted from payload.

## 8. Backend contract

- `WORKOUT_COMPLETION` is already in the Android and backend allowlists
  (`SyncEntityTypes` / `SyncEntityType`).
- Backend stores payloads as generic JSON maps with no entity-specific
  validation; version/conflict handling is generic.
- No backend change is required for the client-side merge strategy in §3.
  Backend change would only be needed if first-writer-wins convergence is
  chosen.

## 9. Implementation sequence (proposal only)

1. Add deterministic identity helper + payload factory method; preserve
   `syncId` on repeated local completion (replace `REPLACE` with explicit
   upsert semantics).
2. Make `recordCompletion` transactional + owner-scoped with outbox production.
3. Add push dependency on acknowledged workout + ack metadata.
4. Add staged apply: missing workout → `WAITING_DEPENDENCY`, version guards,
   no outbox on remote apply, conflict resolution by snapshot rule.
5. Add tests; run migration validation.

## 10. Tests and migration considerations

- Deterministic identity equality across devices; owner differentiation.
- Repeated completion preserves syncId and coalesces before transmission.
- Two-device conflict resolution by snapshot rule.
- Guest local-only; owner isolation/account switch.
- Payload field exactness and exclusion of local IDs/owner.
- Push dependency/conflict/rejected/deleted workout; ack metadata.
- Pull: missing workout wait + retry; version guards; conflict; no outbox.
- Migration: v13→v14 backfill owner/syncId/status/indexes for
  `workout_completion`; legacy rows keep guest owner and never upload.
- Cascade safety: verify all workout deletes are soft-delete-only.

## 11. Concrete blockers / decisions

- Product decision required: merge/tie-break rule and clock-skew acceptance.
- Implementation prerequisite: verify all workout deletion paths remain
  soft-delete-only.
- No schema change is required for v1 if the above hold; no backend change for
  client-side merge.
