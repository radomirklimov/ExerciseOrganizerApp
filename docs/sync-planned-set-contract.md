# Sync Contract — PlannedSet (Milestone: planned-set configuration sync)

**PlannedSet** = saved workout configuration (e.g. 3 sets of 10 reps at 20 kg).
It is synchronized as an independent user-owned record with stable `syncId`.
Local composite PK `(workoutId, exerciseId, setNumber)` is never sent; `setNumber`
is local ordering and may be changed by reorder while preserving `syncId`.

Payload v1: `schemaVersion`, `workoutSyncId`, tagged `exerciseRef`
(PUBLIC catalogId / CUSTOM syncId), `setNumber`, optional `reps`, `weight`,
`durationSeconds`. No Room IDs or owner UID.

Dependencies: workout and its workout-exercise association must exist before
apply; otherwise staged `WAITING_DEPENDENCY`. Soft delete/tombstone, version
guards, conflict blocking, owner scoping, guest local-only per existing pipeline.

**Distinction:**
- PlannedSet: saved configuration, synchronized.
- WorkoutCompletion: completed workout record, synchronized.
- In-progress execution state, ExerciseProgress and actual performed set
  results: local-only, never synchronized.
