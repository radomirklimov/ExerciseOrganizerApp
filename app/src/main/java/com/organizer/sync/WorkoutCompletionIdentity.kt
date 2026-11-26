package com.organizer.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

/** Stable identity and conflict resolution for WORKOUT_COMPLETION records. */
object WorkoutCompletionIdentity {
    private val json = Json { ignoreUnknownKeys = true }

    fun syncId(owner: String, workoutSyncId: String, dateKey: Int): String =
        UUID.nameUUIDFromBytes(
            "WORKOUT_COMPLETION:v1|$owner|$workoutSyncId|$dateKey".toByteArray(Charsets.UTF_8)
        ).toString()

    data class Snapshot(
        val completedExercises: Int,
        val totalExercises: Int,
        val completedAtMillis: Long,
    )

    /** Selects one complete snapshot; never merges fields independently. */
    fun selectWinner(local: Snapshot, server: Snapshot): Snapshot =
        if (server.completedAtMillis != local.completedAtMillis) {
            if (server.completedAtMillis > local.completedAtMillis) server else local
        } else if (server.completedExercises != local.completedExercises) {
            if (server.completedExercises > local.completedExercises) server else local
        } else if (server.totalExercises != local.totalExercises) {
            if (server.totalExercises > local.totalExercises) server else local
        } else local

    fun snapshotFromPayload(payload: JsonObject?): Snapshot? {
        if (payload == null) return null
        val completed = payload["completedExercises"]?.jsonPrimitive?.content?.toIntOrNull() ?: return null
        val total = payload["totalExercises"]?.jsonPrimitive?.content?.toIntOrNull() ?: return null
        val at = payload["completedAt"]?.jsonPrimitive?.content?.let { SyncTimestamps.parse(it) } ?: return null
        if (total <= 0 || completed !in 0..total || at <= 0) return null
        return Snapshot(completed, total, at)
    }
}
