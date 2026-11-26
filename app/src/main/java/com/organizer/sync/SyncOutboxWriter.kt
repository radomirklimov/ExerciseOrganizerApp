package com.organizer.sync

import com.organizer.auth.OwnerProvider
import com.organizer.data.local.dao.SyncOutboxDao
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.local.db.entities.SyncOutboxEntity
import com.organizer.data.local.db.entities.WorkoutEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes outbox mutations for local writes. Callers must invoke this inside a
 * Room transaction together with the entity write so the two persist atomically.
 */
@Singleton
class SyncOutboxWriter @Inject constructor(
    private val outboxDao: SyncOutboxDao,
    private val payloadFactory: SyncPayloadFactory,
) {
    suspend fun upsertCustomExercise(owner: String, exercise: ExerciseEntity) {
        require(exercise.isCustom) { "Only custom exercises can be synced as user data" }
        val syncId = requireNotNull(exercise.syncId) { "Custom exercise must have a stable syncId" }
        enqueue(owner, SyncEntityTypes.CUSTOM_EXERCISE, syncId, OPERATION_UPSERT, exercise.serverVersion) {
            payloadFactory.customExercise(exercise)
        }
    }

    suspend fun deleteCustomExercise(owner: String, exercise: ExerciseEntity) {
        require(exercise.isCustom) { "Only custom exercises can be synced as user data" }
        val syncId = requireNotNull(exercise.syncId) { "Custom exercise must have a stable syncId" }
        enqueue(owner, SyncEntityTypes.CUSTOM_EXERCISE, syncId, OPERATION_DELETE, exercise.serverVersion) {
            payloadFactory.customExercise(exercise)
        }
    }

    suspend fun upsertWorkout(owner: String, workout: WorkoutEntity) {
        enqueue(owner, SyncEntityTypes.WORKOUT, workout.syncId, OPERATION_UPSERT, workout.serverVersion) {
            payloadFactory.workout(workout)
        }
    }

    suspend fun deleteWorkout(owner: String, workout: WorkoutEntity) {
        enqueue(owner, SyncEntityTypes.WORKOUT, workout.syncId, OPERATION_DELETE, workout.serverVersion) {
            payloadFactory.workout(workout)
        }
    }

    suspend fun upsertWorkoutExercise(owner: String, syncId: String, baseVersion: Long, payload: String) {
        enqueue(owner, SyncEntityTypes.WORKOUT_EXERCISE, syncId, OPERATION_UPSERT, baseVersion) { payload }
    }

    suspend fun deleteWorkoutExercise(owner: String, syncId: String, baseVersion: Long, payload: String) {
        enqueue(owner, SyncEntityTypes.WORKOUT_EXERCISE, syncId, OPERATION_DELETE, baseVersion) { payload }
    }

    suspend fun restoreWorkoutExercise(owner: String, syncId: String, baseVersion: Long, payload: String) {
        enqueue(owner, SyncEntityTypes.WORKOUT_EXERCISE, syncId, OPERATION_RESTORE, baseVersion) { payload }
    }

    private suspend fun enqueue(
        owner: String,
        entityType: String,
        syncId: String,
        operation: String,
        baseVersion: Long,
        payload: () -> String,
    ) {
        if (owner == OwnerProvider.GUEST) return

        val now = System.currentTimeMillis()
        val open = outboxDao.findOpenMutation(owner, entityType, syncId, operation)
        if (open != null) {
            // Only pending, never-retried mutations are coalesced. mutationId and
            // baseVersion stay unchanged because the server state has not changed.
            outboxDao.updateOpenPayload(owner, open.mutationId, payload(), now)
            return
        }

        outboxDao.insert(
            SyncOutboxEntity(
                mutationId = UUID.randomUUID().toString(),
                ownerFirebaseUid = owner,
                entityType = entityType,
                recordSyncId = syncId,
                operation = operation,
                payload = payload(),
                baseVersion = baseVersion,
                createdAt = now,
                syncStatus = SyncOutboxStatus.PENDING_UPLOAD,
            )
        )
    }

    companion object {
        const val OPERATION_UPSERT = "UPSERT"
        const val OPERATION_DELETE = "DELETE"
        const val OPERATION_RESTORE = "RESTORE"
    }
}