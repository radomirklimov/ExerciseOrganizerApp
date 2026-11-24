package com.organizer.sync

/** Wire names shared with the backend sync entity-type allowlist. */
object SyncEntityTypes {
    const val WORKOUT = "WORKOUT"
    const val CUSTOM_EXERCISE = "CUSTOM_EXERCISE"
    const val WORKOUT_EXERCISE = "WORKOUT_EXERCISE"
    const val EXERCISE_PROGRESS = "EXERCISE_PROGRESS"
    const val PLANNED_SET = "PLANNED_SET"
    const val WORKOUT_COMPLETION = "WORKOUT_COMPLETION"
    const val SCHEDULED_WORKOUT = "SCHEDULED_WORKOUT"
    const val FAVORITE_EXERCISE = "FAVORITE_EXERCISE"

    val ALL: Set<String> = setOf(
        WORKOUT,
        CUSTOM_EXERCISE,
        WORKOUT_EXERCISE,
        EXERCISE_PROGRESS,
        PLANNED_SET,
        WORKOUT_COMPLETION,
        SCHEDULED_WORKOUT,
        FAVORITE_EXERCISE,
    )
}