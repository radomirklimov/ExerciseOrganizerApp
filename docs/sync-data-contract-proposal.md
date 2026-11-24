# Cloud Sync Data Contract Proposal (Milestone 4A — design only)

Status: **DESIGN ONLY**. No code, migrations, or backend API changes are part of this milestone.

Legend:
- **CONFIRMED** — established by current committed code, migrations, docs, or tests.
- **PROPOSED** — recommended design; not yet approved as product behavior.
- **APPROVED IN PRINCIPLE** — explicitly approved by the reviewer.
- **UNRESOLVED** — cannot be decided from the existing implementation; requires a product/backend decision.

---

## 1. Architecture overview

**CONFIRMED**
- Android Room is the local source of truth. User-owned tables contain `ownerFirebaseUid`, `syncId`, `updatedAt`, `deletedAt`, `syncStatus`, and `serverVersion`.
- Backend owns cloud records in `user_data_record`, keyed by authenticated application user plus `(entity_type, sync_id)`.
- The push API already supports `UPSERT`, `DELETE`, `RESTORE`, `baseVersion`, mutation receipts, tombstones, and an opaque cursor.
- The backend stores payloads as generic JSON maps. It does not validate entity-specific payload fields.
- Public exercises/categories/starter templates are application-owned reference data.

**APPROVED IN PRINCIPLE**
- Define a versioned payload contract for the eight user-owned entity types.
- Do not automatically cascade cloud deletion from parent records to children.
- Include `"schemaVersion": 1` inside every entity payload.

**PROPOSED**
- Keep the top-level push API unchanged.
- Use stable UUIDs for all cross-device references to user-owned records.
- Stage records with unresolved references instead of dropping or partially applying them.

---

## 2. Identity and reference model

### 2.1 Four distinct identifiers

| Identifier | Source | Scope | Notes |
|---|---|---|---|
| Backend public exercise ID (`exerciseId`) | `/api/v1/exercises` | application/catalog-wide | **CONFIRMED** Long stable server-side ID for public reference data. |
| Android local Room exercise ID (`exerciseId`) | Room autogenerate | device/database-local | **CONFIRMED** Long PK used by local FK relationships. Never a cross-device identity. |
| Custom exercise sync UUID (`syncId`) | Room `ExerciseEntity.syncId` for `isCustom = true` | user-owned record across devices | **CONFIRMED** stable UUID. This is the cloud identity for custom exercises. |
| Backend internal user-data record ID (`id`) | `UserDataRecordEntity.id` | backend internal | **CONFIRMED** UUID PK. Never exposed to Android and never used in payloads or references. |

### 2.2 Exercise reference structure

**PROPOSED** (refinement requested in this review; not yet implemented):

Public exercise:

```json
{
  "kind": "PUBLIC",
  "exerciseId": 42
}
```

Custom exercise:

```json
{
  "kind": "CUSTOM",
  "syncId": "550e8400-e29b-41d4-a716-446655440000"
}
```

Rules:
- `kind` is required and must be exactly `PUBLIC` or `CUSTOM`.
- `PUBLIC.exerciseId` must be a positive Long from the backend public exercise API. It is never a Room local ID.
- `CUSTOM.syncId` must be a valid UUID belonging to a user-owned custom exercise.
- Unknown `kind`, missing fields, wrong field types, negative public IDs, or malformed UUIDs must be rejected.
- A reference to a record whose UUID has not arrived yet must be staged, not dropped.

**UNRESOLVED**
- Whether the backend should validate the tagged reference shape.
- Exact behavior when a referenced custom exercise is permanently absent or tombstoned.

### 2.3 Other references

**PROPOSED**
- Workout references use `workoutSyncId` (UUID).
- User-owned record references always use `syncId` UUIDs.
- Local Room Long IDs never leave the device.

---

## 3. Entity catalog

### 3.1 WORKOUT
- Purpose: reusable user workout definition/routine.
- Room entity: `WorkoutEntity`.
- Ownership: user-owned.
- Stable identity: `syncId` UUID.
- Can exist independently: yes.
- Required payload fields: `schemaVersion`, `name`.
- Optional: `notes`.
- Relationships: workout exercises, planned sets, progress, completions, schedules.
- Validation: `name` non-blank.
- Deletion: reusable definition tombstone; no child cascade.

### 3.2 CUSTOM_EXERCISE
- Purpose: user-created exercise.
- Room entity: `ExerciseEntity` where `isCustom = true`.
- Ownership: user-owned.
- Stable identity: `syncId` UUID.
- Can exist independently: yes.
- Required: `schemaVersion`, `name`, `exerciseType`, `isCustom = true`.
- Optional: `instructions`, `imageUrls`.
- Public/custom distinction: public rows have `ownerFirebaseUid = "public"` and no sync UUID.
- Validation: `name` non-blank; `isCustom` must be true.
- Unresolved: cross-device image semantics.

### 3.3 WORKOUT_EXERCISE
- Purpose: ordered workout/exercise link.
- Room entity: `WorkoutExerciseEntity`.
- Ownership: user-owned.
- Stable identity: link `syncId` UUID; local composite PK remains.
- Required: `schemaVersion`, `workoutSyncId`, `exerciseRef`, `orderIndex`.
- Validation: `orderIndex >= 0`; references must resolve or be staged.
- Cannot exist independently.

### 3.4 EXERCISE_PROGRESS
- Purpose: logged sets / training history.
- Room entity: `ExerciseProgressEntity`.
- Ownership: user-owned.
- Stable identity: record `syncId` UUID.
- Required: `schemaVersion`, `workoutSyncId`, `exerciseRef`, `setNumber`, `recordedAt`.
- Optional: `reps`, `weight`, `durationSeconds`, `sessionId`, `unitSystem`.
- Validation: `setNumber >= 1`; nullable measurement fields allowed for legacy data.
- `sessionId` is a **CONFIRMED** local Long grouping value; its cross-device identity is unresolved.

### 3.5 PLANNED_SET
- Purpose: target sets for a routine.
- Room entity: `PlannedSetEntity`.
- Ownership: user-owned.
- Stable identity: record `syncId` UUID.
- Local PK: `(workoutId, exerciseId, setNumber)`; preserved locally.
- Required: `schemaVersion`, `workoutSyncId`, `exerciseRef`, `setNumber`.
- Optional: `reps`, `weight`, `durationSeconds`.
- **CONFIRMED** current app only reads planned sets; no write path exists. Source of truth for new planned sets is **UNRESOLVED**.
- Independence: no; depends on workout and exercise reference.

### 3.6 WORKOUT_COMPLETION
- Purpose: daily completion counts shown in schedule calendar.
- Room entity: `WorkoutCompletionEntity`.
- Ownership: user-owned.
- Stable identity: record `syncId` UUID.
- Required: `schemaVersion`, `workoutSyncId`, `date`, `completedExercises`, `totalExercises`, `completedAt`.
- Optional: none.
- Validation: `0 <= completedExercises <= totalExercises`; `totalExercises > 0`.
- Historical counts must survive deletion of the workout definition.
- **CONFIRMED** current local field is `dateKey: Int` (`yyyyMMdd`); see timestamp section for proposed normalization.

### 3.7 SCHEDULED_WORKOUT
- Purpose: recurring or one-time schedule slot.
- Room entity: `ScheduledWorkoutEntity`.
- Ownership: user-owned.
- Stable identity: record `syncId` UUID.
- Required: `schemaVersion`, `workoutSyncId`, `dayOfWeek`, `startMinutes`, `timeZone`.
- Optional: `scheduledDate`, `reminderEnabled`, `reminderMinutesBefore`, `reminderNote`.
- Validation: `dayOfWeek` 1..7; `startMinutes` 0..1439; valid IANA timezone.
- Schedule entries referencing deleted workouts are retained but hidden.
- **CONFIRMED** current local fields are `scheduledDateKey: Int?` and `startMinutes: Int`; timezone is not stored.

### 3.8 FAVORITE_EXERCISE
- Purpose: user's liked exercise.
- Room entity: `FavoriteExerciseEntity`.
- Ownership: user-owned.
- Stable identity: record `syncId` UUID.
- Required: `schemaVersion`, `exerciseRef`, `addedAt`.
- Optional: none.
- Cannot exist independently.
- Behavior when target custom exercise is deleted: unresolved.

---

## 4. Timestamp and date semantics

Three concepts must not be conflated.

### 4.1 Date-only values
- Used for calendar/completion dates and one-time schedule dates.
- Proposed payload representation: ISO local date string, e.g. `"2026-11-24"`.
- No timezone or time-of-day.
- **CONFIRMED** current Room uses `Int dateKey` / `Int? scheduledDateKey` in local device timezone.
- **PROPOSED** contract uses ISO local date strings; conversion from current Int fields is unresolved.

### 4.2 Absolute timestamps
- Used for `clientUpdatedAt`, `recordedAt`, `completedAt`, `addedAt`, `serverUpdatedAt`, `serverTime`.
- Format: ISO-8601 UTC instant, e.g. `"2026-11-24T10:00:00.123Z"`.
- **CONFIRMED** backend parses/stores `java.time.Instant`.
- **PROPOSED** Android emits millisecond precision.

### 4.3 Scheduled local date/time
- Recurring schedules must preserve the user's intended wall-clock time across DST.
- Proposed representation:
  - `dayOfWeek`, `startMinutes`, and optional `scheduledDate`
  - plus `timeZone`, an IANA identifier such as `"Europe/Berlin"`.
- A recurring 09:00 meeting remains 09:00 local time before and after a DST shift because the timezone rules, not a fixed UTC offset, are applied.
- **UNRESOLVED** current app does not store a timezone; adding it is a product decision and schema change.

---

## 5. Deletion, restoration, and historical integrity

**APPROVED IN PRINCIPLE**
- Do not automatically cascade cloud deletion from parent records to children.

**CONFIRMED backend semantics**
- Soft deletion / tombstone: `deleted_at` plus `deleted = true` in change log.
- `RESTORE` is an explicit operation requiring payload and version match.
- `UPSERT` on a tombstoned record is rejected with `RECORD_DELETED`.
- Stale `baseVersion` yields `CONFLICT`.

**PROPOSED distinctions**
- **Soft deletion (Android):** set local `deletedAt`; row remains, UI filters it.
- **Tombstone (backend/cloud):** `DELETE` mutation marks record deleted and emits a change-log entry.
- **Explicit restoration:** `RESTORE` mutation with correct `baseVersion`; clears tombstone.
- **Historical snapshot:** independent preserved data needed to display old names/details. The current model has no snapshot fields.

**Workout definitions vs history**
- Workout definitions (`WORKOUT`, `WORKOUT_EXERCISE`, `PLANNED_SET`, `SCHEDULED_WORKOUT`) are reusable configuration.
- `EXERCISE_PROGRESS` and `WORKOUT_COMPLETION` are history.
- Deleting a definition must not delete history.
- Historical completion records keep their own `syncId` and `workoutSyncId` reference.

**Exercise names in history**
- Current progress rows reference an exercise by local ID, not by embedded name.
- To keep names available after custom exercise deletion, either:
  - retain tombstoned custom exercise records so references still resolve (**PROPOSED**), or
  - add snapshot fields (**UNRESOLVED**, not currently supported by the app model).

**Favorites referencing deleted custom exercises**
- **UNRESOLVED** product decision.
- Recommendation: retain the favorite record but treat its target as unavailable; do not silently rewrite history.

**Scheduled workouts referencing deleted workouts**
- **PROPOSED:** retain schedule record in cloud, hide locally when workout is tombstoned; require explicit user action to delete the schedule.

**Parent relationship deletion**
- `WORKOUT_EXERCISE` / `PLANNED_SET` rows referencing a deleted workout are retained but hidden; no automatic cloud deletion.

---

## 6. Custom exercise images

**RECOMMENDATION (PROPOSED): defer image binary synchronization.**

- **CONFIRMED** current `ExerciseEntity.imageUrls: List<String>` stores file names/URLs. Custom images are local files; public images are downloaded from backend storage.
- Metadata that could be synchronized: existing `imageUrls` strings only.
- Do not embed image binaries in JSON payloads: payload size limits, base64 overhead, idempotency/hashing cost, and no backend image-upload API exists.
- Future object-storage approach: upload binary separately, then store an object identifier or URL in `imageUrls`. This requires new backend/storage work.
- Missing images on another device: show a placeholder and keep metadata; do not treat missing binary as data loss.
- No image service or backend change is introduced in this milestone.

---

## 7. Workout session identity and history

**CONFIRMED**
- `ExerciseProgressEntity.sessionId: Long` groups sets from one execution locally.
- `WorkoutCompletionEntity` records per-day counts, not a full session record.
- There is no explicit session table or stable session UUID in the current app model.

**PROPOSED / UNRESOLVED**
- A completed workout session should have a stable identity independent of local PKs, workout definition, and device.
- The current model cannot express this fully without a schema addition.
- Options requiring approval:
  - treat each `WORKOUT_COMPLETION.syncId` as the session identity and add an explicit session reference to progress rows (schema change), or
  - keep local `sessionId` grouping and accept that cross-device session grouping is unresolved.
- Offline-created sessions synchronize when their records are uploaded with stable `syncId` values. Until session identity is resolved, progress sets may upload individually without a reliable cross-device session grouping.

**Do not invent fields in implementation.** The examples in this document do not add a new session field.

---

## 8. Planned sets

**CONFIRMED**
- Parent relationship: `planned_set` has FKs to `workout` and `exercise`.
- Local PK: `(workoutId, exerciseId, setNumber)`.
- A read-only DAO/repository exists; no write path in current app.
- `WorkoutExecutionScreen` reads planned sets to prefill target rows.

**PROPOSED**
- Treat each planned set as an independent synchronized record with its own `syncId`.
- Reference parent workout by `workoutSyncId` and exercise by `exerciseRef`.
- Identity survives local relationship changes because the cloud identity is `syncId`, not the composite local PK.

**UNRESOLVED**
- Source of truth for creating/updating planned sets.
- Whether a workout-exercise relationship delete should delete planned sets.
- Whether planned sets are meaningful for custom exercises on other devices.

---

## 9. Payload schema versioning

**APPROVED IN PRINCIPLE**
- Include `"schemaVersion": 1` inside every payload.

**Why this preserves the API**
- The top-level push shape remains unchanged: `mutationId`, `entityType`, `syncId`, `operation`, `baseVersion`, `clientUpdatedAt`, `payload`.
- The backend already treats `payload` as an opaque object.

**Validation**
- Backend can validate `(entityType, schemaVersion)` when payload validation is added.
- Unknown versions can be rejected or staged; never silently applied.

**Migration/rejection**
- Future versions add fields additively where possible.
- Breaking changes require a new `schemaVersion`.
- Milestone 4A does not implement migration logic.

**Separation from `serverVersion`**
- `schemaVersion` describes payload shape.
- `serverVersion` is backend record concurrency/version state.
- They are unrelated.

---

## 10. Relationship graph

```mermaid
graph TD
    W[WORKOUT] --> WE[WORKOUT_EXERCISE]
    W --> PS[PLANNED_SET]
    W --> EP[EXERCISE_PROGRESS]
    W --> WC[WORKOUT_COMPLETION]
    W --> SW[SCHEDULED_WORKOUT]
    WE --> ER{exerciseRef}
    PS --> ER
    EP --> ER
    FAV[FAVORITE_EXERCISE] --> ER
    CE[CUSTOM_EXERCISE] --> ER
    PUB[PUBLIC_EXERCISE (application-owned)] --> ER
```

---

## 11. Upload/download ordering and unresolved references

**Upload order (PROPOSED)**
1. CUSTOM_EXERCISE
2. WORKOUT
3. WORKOUT_EXERCISE
4. PLANNED_SET
5. EXERCISE_PROGRESS
6. WORKOUT_COMPLETION
7. SCHEDULED_WORKOUT
8. FAVORITE_EXERCISE

**Download/application**
1. Stage pulled records by `syncId`.
2. Apply parent records (workout, custom exercise).
3. Resolve `exerciseRef` and `workoutSyncId`.
4. Apply child records.
5. Retry unresolved records after each page.
6. Advance cursor only after records are safely persisted.

**Dependency handling**
- Parent/child validation occurs locally before outbox creation and after pull.
- Backend mutations remain independent; no atomic batch is assumed.
- Tombstones are staged/applied like other records.
- Version checks remain per record.
- Historical records are independent of parent tombstones.

---

## 12. Conflict considerations

**CONFIRMED**
- Backend uses `baseVersion`, mutation receipts, per-user sequence locks, and tombstones.

**PROPOSED**
- Parent and child edits are separate mutations; partial application is possible.
- A stale response must not overwrite newer local state.
- A delete/edit conflict yields `CONFLICT` or `RECORD_DELETED`; no automatic resolution.
- Unresolved references are retried; they are not conflicts by themselves.
- Historical snapshots, once defined, are immutable and not merged.

**UNRESOLVED**
- Coordinated parent-child grouping / change-set identity.
- Automatic conflict UI behavior.

---

## 13. Decision register

| Decision | Recommendation | Status | Evidence | Android consequence | Backend consequence |
|---|---|---|---|---|---|
| `schemaVersion` in payload | Add to all payloads | APPROVED IN PRINCIPLE | User review; backend opaque payload | Add field to producers | Can validate `(entityType, schemaVersion)` later |
| No automatic child cascade on parent delete | Keep children/history | APPROVED IN PRINCIPLE | User requirement; Room soft-delete parent only | Hide children locally if parent tombstoned | Do not synthesize child DELETE mutations |
| Tagged `exerciseRef` | PUBLIC ID vs CUSTOM UUID | PROPOSED | Public API uses `exerciseId`; custom rows have `syncId` | Resolve refs before apply | Validate later if desired |
| Backend internal record ID unused | Never send/store it | CONFIRMED | Backend never exposes it | Use `syncId` only | Keep internal ID internal |
| Date-only ISO strings | Use for dates | PROPOSED | Current uses Int `dateKey` | Conversion decision needed | Store string in payload |
| UTC absolute instants | Use ISO-8601 UTC | PROPOSED / consistent with backend | Backend uses `Instant` | Emit millisecond precision | Existing parser accepts |
| IANA timezone for schedules | Add `timeZone` | UNRESOLVED | Current app has no timezone field | Schema/product decision | Payload validation later |
| Defer image binaries | Sync metadata only | PROPOSED | No backend upload API | Placeholder for missing images | No change now |
| Session UUID for history | Needed but unsupported today | UNRESOLVED | `sessionId` is local Long | Schema decision before implementation | Payload decision |
| Planned-set producer | Required before sync | UNRESOLVED | Read-only DAO/repository | Define source of truth | No backend impact yet |
| Favorite target deleted | Retain favorite, hide target | UNRESOLVED | No current rule | UI behavior decision | Possible validation later |
| Schedule target deleted | Retain schedule, hide locally | PROPOSED | No cascade requirement | UI filter needed | No automatic delete |

---

## 14. Final review and open questions

The document has been reviewed for contradictions and identifier consistency:
- `exerciseId` always means backend public catalog ID unless explicitly described as local Room ID.
- `syncId` always means stable user-owned UUID.
- Backend internal record IDs are never used in payloads.
- Date-only, UTC instant, and scheduled-local-time concepts are separated.
- Deletion does not cascade to children.
- No field is presented as implemented unless confirmed by code.

Open product decisions are listed in the decision register and must be resolved before implementation.
