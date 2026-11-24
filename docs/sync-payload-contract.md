# Sync Payload Contract (unresolved)

The backend `POST /api/sync/push` currently accepts an opaque JSON object payload
(`Map<String, Any>`) and stores it verbatim. No entity-specific schema is defined
in backend code, documentation, or tests. Therefore **no Android payload schema
may be invented** until the backend contract is extended and agreed.

The Android sync-contract layer only transports whatever JSON object a future
outbox producer stores. It does not generate payloads.

## Entity inventory

### WORKOUT
- Purpose: user-created workout routine.
- Room entity: `WorkoutEntity`.
- Sync identity: `syncId` UUID; existing `workoutId` Long remains local-only.
- Owner: `ownerFirebaseUid`; legacy rows are `guest`.
- Relationships: workout exercises, planned sets, progress, schedule, completion.
- Identifiers: local Long IDs must not be used as cross-device references.
- Required decisions: payload fields, how exercise references are encoded, naming rules.

### CUSTOM_EXERCISE
- Purpose: user-created exercise, owner-scoped.
- Room entity: `ExerciseEntity` rows where `isCustom = true`.
- Sync identity: `syncId` UUID.
- Owner: `ownerFirebaseUid`.
- Relationships: workout exercises, planned sets, progress, favorites.
- Public/custom distinction: public exercises are application-owned reference data
  and must never be uploaded as custom exercises.
- Required decisions: payload fields, image handling, exercise type representation,
  how custom references are distinguished from public backend exercise IDs.

### WORKOUT_EXERCISE
- Purpose: ordered link between a workout and an exercise.
- Room entity: `WorkoutExerciseEntity`.
- Sync identity: link `syncId` UUID.
- Owner: `ownerFirebaseUid`.
- Relationships: workout + exercise.
- Required decisions: whether references use sync UUIDs, public backend IDs, or a
  tagged union; exact representation is unresolved.

### EXERCISE_PROGRESS
- Purpose: recorded training sets / sessions.
- Room entity: `ExerciseProgressEntity`.
- Sync identity: `progress.syncId` UUID.
- Owner: `ownerFirebaseUid`.
- Relationships: workout + exercise; `sessionId` groups sets of one execution.
- Required decisions: payload fields, session identity across devices, unit representation.

### PLANNED_SET
- Purpose: target sets in a workout routine.
- Room entity: `PlannedSetEntity`.
- Sync identity: `plannedSet.syncId` UUID.
- Owner: `ownerFirebaseUid`.
- Relationships: workout + exercise.
- Required decisions: payload fields and exercise reference rules.

### WORKOUT_COMPLETION
- Purpose: per-day workout completion counts.
- Room entity: `WorkoutCompletionEntity`.
- Sync identity: `completion.syncId` UUID.
- Owner: `ownerFirebaseUid`.
- Relationships: workout; `dateKey` is a local calendar date.
- Required decisions: timezone/date semantics across devices, payload fields.

### SCHEDULED_WORKOUT
- Purpose: recurring or one-time scheduled workout slot.
- Room entity: `ScheduledWorkoutEntity`.
- Sync identity: `schedule.syncId` UUID.
- Owner: `ownerFirebaseUid`.
- Relationships: workout.
- Required decisions: payload fields, reminder representation, local time semantics.

### FAVORITE_EXERCISE
- Purpose: user exercise favorites.
- Room entity: `FavoriteExerciseEntity`.
- Sync identity: `favorite.syncId` UUID.
- Owner: `ownerFirebaseUid`.
- Relationships: exercise (public or custom).
- Required decisions: reference representation for public vs custom exercises.

## Reference rules (known)

- Public exercises are application-owned reference data from the backend public API.
- Custom exercises are user-owned synchronized records with stable sync UUIDs.
- References to public exercises must use the established backend public exercise identifier.
- References to custom exercises must use their stable sync UUID.
- The exact JSON representation of these references is **unresolved**.

## Producer requirements (for a future milestone)

Every future outbox producer must:
1. Capture `ownerFirebaseUid` from the authenticated identity at creation time.
2. Never create guest mutations.
3. Use a fresh stable `mutationId` UUID for each logical mutation.
4. Reuse the record's stable `syncId` UUID across retries and devices.
5. Store payload JSON only after the entity payload contract is agreed.
6. Never copy a public exercise into the custom-exercise upload path.
7. Keep owner, mutation ID, and record sync ID immutable once written.
8. Scope all DAO reads/updates/deletes by owner.

## Decision checklist before the sync engine

- [ ] Define payload fields for all eight entity types.
- [ ] Define nested object/array shapes and null rules.
- [ ] Define public vs custom exercise reference representation.
- [ ] Define workout/exercise relationship payload format.
- [ ] Define session identity for exercise progress.
- [ ] Define date/time and timezone semantics for completions and schedules.
- [ ] Define image handling for custom exercises.
- [ ] Add backend validation/tests for each payload shape.
