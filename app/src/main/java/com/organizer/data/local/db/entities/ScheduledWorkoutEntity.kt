package com.organizer.data.local.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One weekly recurring workout slot.
 *
 * Only the assignment is stored here; the workout itself stays in [WorkoutEntity]
 * and is referenced by [workoutId]. Deleting the workout removes its schedule
 * slots through the cascade below without touching any other workout data.
 *
 * [dayOfWeek] uses ISO numbering (1 = Monday ... 7 = Sunday) so the weekly
 * strip shown by the Schedule page can use it directly.
 */
@Entity(
    tableName = "scheduled_workout",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["workoutId"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("workoutId")],
)
data class ScheduledWorkoutEntity(
    @PrimaryKey(autoGenerate = true)
    val scheduleId: Long = 0L,
    val workoutId: Long,
    val dayOfWeek: Int,
    /** Workout start time as minutes since midnight (0..1439). */
    val startMinutes: Int,
    val reminderEnabled: Boolean = true,
    /** How long before the workout the reminder fires. */
    val reminderMinutesBefore: Int = 60,
    /** Optional text included in the reminder notification. */
    val reminderNote: String = "",
)
