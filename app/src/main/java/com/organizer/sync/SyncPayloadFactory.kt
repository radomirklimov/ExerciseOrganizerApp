package com.organizer.sync

import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.local.db.entities.FavoriteExerciseEntity
import com.organizer.data.local.db.entities.WorkoutEntity
import com.organizer.data.local.db.entities.WorkoutExerciseEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import javax.inject.Inject
import javax.inject.Singleton

/** Builds deterministic payload JSON for the supported user-owned entities. */
@Singleton
class SyncPayloadFactory @Inject constructor(
    private val json: Json,
) {
    fun customExercise(exercise: ExerciseEntity): String = json.encodeToString(
        buildJsonObject {
            put("schemaVersion", SCHEMA_VERSION)
            put("name", exercise.name)
            putJsonArray("instructions") { exercise.instructions.forEach { add(JsonPrimitive(it)) } }
            putJsonArray("imageUrls") { exercise.imageUrls.forEach { add(JsonPrimitive(it)) } }
            put("exerciseType", exercise.exerciseType)
            put("isCustom", true)
        }
    )

    fun workout(workout: WorkoutEntity): String = json.encodeToString(
        buildJsonObject {
            put("schemaVersion", SCHEMA_VERSION)
            put("name", workout.name)
            put("notes", workout.notes)
        }
    )

    fun workoutExercise(
        link: WorkoutExerciseEntity,
        workoutSyncId: String,
        exercise: ExerciseEntity,
    ): String = json.encodeToString(
        buildJsonObject {
            put("schemaVersion", SCHEMA_VERSION)
            put("workoutSyncId", workoutSyncId)
            put("exerciseRef", exerciseRef(exercise))
            put("orderIndex", link.orderIndex)
            put("updatedAt", SyncTimestamps.format(link.updatedAt))
            link.deletedAt?.let { put("deletedAt", SyncTimestamps.format(it)) }
        }
    )

    fun favoriteExercise(favorite: FavoriteExerciseEntity, exercise: ExerciseEntity): String = json.encodeToString(
        buildJsonObject {
            put("schemaVersion", SCHEMA_VERSION)
            put("exerciseRef", exerciseRef(exercise))
            put("addedAt", SyncTimestamps.format(favorite.addedAt))
        }
    )

    private fun exerciseRef(exercise: ExerciseEntity) = buildJsonObject {
        if (exercise.isCustom) {
            val syncId = requireNotNull(exercise.syncId) { "Custom exercise must have a syncId" }
            put("kind", "CUSTOM")
            put("syncId", syncId)
        } else {
            val catalogId = requireNotNull(exercise.publicExerciseId) { "Public exercise must have a catalog ID" }
            put("kind", "PUBLIC")
            put("exerciseId", catalogId)
        }
    }

    companion object {
        const val SCHEMA_VERSION = 1
    }
}