package com.organizer.sync

import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.local.db.entities.WorkoutEntity
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

    companion object {
        const val SCHEMA_VERSION = 1
    }
}