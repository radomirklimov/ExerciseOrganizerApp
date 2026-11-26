package com.organizer.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

/** Stable cloud identity of an exercise reference, never a local Room PK. */
sealed interface ExerciseReference {
    data class Public(val catalogId: Long) : ExerciseReference
    data class Custom(val syncId: String) : ExerciseReference
}

/**
 * Typed identity extracted from a WORKOUT_EXERCISE payload or staged tombstone.
 * Every field is nullable because a tombstone may arrive without a payload.
 */
data class WorkoutExerciseIdentity(
    val associationSyncId: String?,
    val workoutSyncId: String?,
    val exerciseRef: ExerciseReference?,
) {
    val hasUsablePair: Boolean get() = workoutSyncId != null && exerciseRef != null
}

object WorkoutExerciseIdentityParser {
    private val json = Json { ignoreUnknownKeys = true }

    /** Parses structured fields; tolerates whitespace, key order and escaping differences. */
    fun parse(payload: String?, associationSyncId: String? = null): WorkoutExerciseIdentity {
        val obj = payload?.let { runCatching { json.parseToJsonElement(it.trim()) }.getOrNull() } as? JsonObject
        val workoutSyncId = obj?.string("workoutSyncId")?.trim()?.takeIf(::isValidUuid)
        val ref = obj?.get("exerciseRef") as? JsonObject
        val kind = ref?.string("kind")?.trim()?.uppercase()
        val exerciseRef = when (kind) {
            "PUBLIC" -> ref.long("exerciseId")?.takeIf { it > 0 }?.let(ExerciseReference::Public)
            "CUSTOM" -> ref.string("syncId")?.trim()?.takeIf(::isValidUuid)?.let(ExerciseReference::Custom)
            else -> null
        }
        return WorkoutExerciseIdentity(associationSyncId, workoutSyncId, exerciseRef)
    }

    private fun JsonObject.string(key: String): String? =
        runCatching { this[key]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() } }.getOrNull()

    private fun JsonObject.long(key: String): Long? =
        runCatching {
            val content = this[key]?.jsonPrimitive?.content?.trim() ?: return null
            content.toLongOrNull() ?: content.toDoubleOrNull()?.takeIf { it == it.toLong().toDouble() }?.toLong()
        }.getOrNull()

    private fun isValidUuid(value: String): Boolean = runCatching { UUID.fromString(value) }.isSuccess
}
