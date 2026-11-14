package com.organizer.data.local.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A starter workout template downloaded from the API. Templates live in their
 * own tables so they never mix with the user's own workouts.
 */
@Entity("starter_template")
data class StarterTemplateEntity(
    /** Server workout id; stable key for refreshes. */
    @PrimaryKey
    val templateId: Long,
    val name: String,
    val notes: String = "",
    /** Category whose exercises dominate the template (e.g. "Chest"). */
    val categoryName: String? = null,
    /**
     * All categories the template's exercises belong to (e.g. ["Chest", "Arms"]),
     * ordered by how many exercises use them.
     */
    val categoryNames: List<String> = emptyList(),
    /** Icon file of the dominant category (or its closest ancestor with one). */
    val iconUrl: String? = null,
    val exerciseCount: Int = 0,
) {
    /** Human-readable category summary shown under the template name. */
    val categoryLabel: String
        get() = when {
            categoryNames.isEmpty() -> categoryName?.takeIf { it.isNotBlank() } ?: "Workout"
            categoryNames.size == 1 -> categoryNames.first()
            categoryNames.size == 2 -> categoryNames.joinToString(" & ")
            else -> categoryNames.joinToString(" · ")
        }
}


@Entity(
    tableName = "starter_template_exercise",
    primaryKeys = ["templateId", "exerciseId"],
    foreignKeys = [
        ForeignKey(
            entity = StarterTemplateEntity::class,
            parentColumns = ["templateId"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["exerciseId"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("templateId"),
        Index("exerciseId")
    ]
)
data class StarterTemplateExerciseEntity(
    val templateId: Long,
    val exerciseId: Long,
    val orderIndex: Int,
)
