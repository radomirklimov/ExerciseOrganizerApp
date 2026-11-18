package com.organizer.presentation.screens.workout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.local.db.entities.PlannedSetEntity
import com.organizer.presentation.OrganizerViewModel

/** A single editable target row inside [PlannedSetsDialog]. */
private data class PlannedDraft(
    val reps: Int,
    val weight: Double,
    val durationSeconds: Int,
)

private const val DEFAULT_PLANNED_SETS = 3
private const val DEFAULT_REPS = 10
private const val DEFAULT_DURATION_SECONDS = 45
private const val WEIGHT_STEP_KG = 2.5
private const val DURATION_STEP_SECONDS = 15
private const val MAX_PLANNED_SETS = 12

/**
 * Lets the user describe what the routine asks for ("3 sets, 10 reps at 60 kg")
 * before the workout starts. The workout player renders these rows as
 * done / current / upcoming.
 */
@Composable
internal fun PlannedSetsDialog(
    workoutId: Long,
    exercise: ExerciseEntity,
    exerciseType: String,
    plannedSets: List<PlannedSetEntity>,
    viewModel: OrganizerViewModel,
    onDismiss: () -> Unit,
) {
    var drafts by remember(exercise.exerciseId) {
        mutableStateOf(
            if (plannedSets.isEmpty()) {
                List(DEFAULT_PLANNED_SETS) { defaultDraft(exerciseType) }
            } else {
                plannedSets
                    .sortedBy { it.setNumber }
                    .map { plannedDraft(it, exerciseType) }
            }
        )
    }
    // Always keep the tracking type of the exercise, so a plan never mixes
    // distance/reps/weight in a way the player cannot display.
    val type = exerciseType

    fun updateDraft(index: Int, transform: (PlannedDraft) -> PlannedDraft) {
        drafts = drafts.toMutableList().also { it[index] = transform(it[index]) }
    }

    fun save() {
        val entities = drafts.mapIndexed { index, draft ->
            PlannedSetEntity(
                workoutId = workoutId,
                exerciseId = exercise.exerciseId,
                setNumber = index + 1,
                reps = if (type == TYPE_DURATION) null else draft.reps.coerceAtLeast(1),
                weight = if (type == TYPE_REPS_WEIGHT) draft.weight.coerceAtLeast(0.0) else null,
                durationSeconds = if (type == TYPE_DURATION) {
                    draft.durationSeconds.coerceAtLeast(DURATION_STEP_SECONDS)
                } else {
                    null
                },
            )
        }
        viewModel.replacePlannedSets(workoutId, exercise.exerciseId, entities)
        onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
                    .heightIn(max = maxHeight * 0.92f),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 20.dp,
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                ),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f)
                            )
                            .padding(start = 20.dp, top = 20.dp, end = 8.dp, bottom = 16.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FitnessCenter,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                    Text(
                                        text = exerciseTypeLabel(type),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Plan sets",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = exercise.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close plan editor",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (drafts.isEmpty()) {
                            Text(
                                text = "No planned sets. Add a set to start a plan.",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        drafts.forEachIndexed { index, draft ->
                            PlannedSetEditorRow(
                                setNumber = index + 1,
                                type = type,
                                draft = draft,
                                canDelete = drafts.size > 1,
                                onRepsChange = { newReps ->
                                    updateDraft(index) { it.copy(reps = newReps) }
                                },
                                onWeightChange = { newWeight ->
                                    updateDraft(index) { it.copy(weight = newWeight) }
                                },
                                onDurationChange = { newDuration ->
                                    updateDraft(index) { it.copy(durationSeconds = newDuration) }
                                },
                                onDelete = {
                                    drafts = drafts.toMutableList().also { it.removeAt(index) }
                                },
                            )
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = {
                                    val template = drafts.lastOrNull() ?: defaultDraft(type)
                                    drafts = drafts + template
                                },
                                enabled = drafts.size < MAX_PLANNED_SETS,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add set")
                            }
                            if (plannedSets.isNotEmpty()) {
                                TextButton(
                                    onClick = {
                                        viewModel.replacePlannedSets(
                                            workoutId,
                                            exercise.exerciseId,
                                            emptyList(),
                                        )
                                        onDismiss()
                                    }
                                ) {
                                    Text(
                                        text = "Clear",
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                        Button(
                            onClick = ::save,
                            enabled = drafts.isNotEmpty(),
                            modifier = Modifier.height(48.dp),
                            shape = RoundedCornerShape(50),
                        ) {
                            Text("Save plan")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlannedSetEditorRow(
    setNumber: Int,
    type: String,
    draft: PlannedDraft,
    canDelete: Boolean,
    onRepsChange: (Int) -> Unit,
    onWeightChange: (Double) -> Unit,
    onDurationChange: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, top = 10.dp, end = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryFixed),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = setNumber.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryFixed,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            when (type) {
                TYPE_DURATION -> CompactTargetStepper(
                    modifier = Modifier.weight(1f),
                    label = "DURATION",
                    valueText = formatDuration(draft.durationSeconds),
                    onDecrease = {
                        onDurationChange((draft.durationSeconds - DURATION_STEP_SECONDS).coerceAtLeast(0))
                    },
                    onIncrease = {
                        onDurationChange(draft.durationSeconds + DURATION_STEP_SECONDS)
                    },
                )

                TYPE_REPS -> CompactTargetStepper(
                    modifier = Modifier.weight(1f),
                    label = "REPETITIONS",
                    valueText = draft.reps.toString(),
                    onDecrease = { onRepsChange((draft.reps - 1).coerceAtLeast(1)) },
                    onIncrease = { onRepsChange(draft.reps + 1) },
                )

                else -> Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CompactTargetStepper(
                        modifier = Modifier.weight(1f),
                        label = "REPS",
                        valueText = draft.reps.toString(),
                        onDecrease = { onRepsChange((draft.reps - 1).coerceAtLeast(1)) },
                        onIncrease = { onRepsChange(draft.reps + 1) },
                    )
                    CompactTargetStepper(
                        modifier = Modifier.weight(1f),
                        label = "LOAD (KG)",
                        valueText = formatWeight(draft.weight).removeSuffix(" kg"),
                        onDecrease = {
                            onWeightChange((draft.weight - WEIGHT_STEP_KG).coerceAtLeast(0.0))
                        },
                        onIncrease = { onWeightChange(draft.weight + WEIGHT_STEP_KG) },
                    )
                }
            }

            IconButton(
                onClick = onDelete,
                enabled = canDelete,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove planned set $setNumber",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun CompactTargetStepper(
    label: String,
    valueText: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            RoundStepButton(
                icon = Icons.Default.Remove,
                contentDescription = "Decrease $label",
                onClick = onDecrease,
            )
            Text(
                text = valueText,
                modifier = Modifier.widthIn(min = 40.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            RoundStepButton(
                icon = Icons.Default.Add,
                contentDescription = "Increase $label",
                onClick = onIncrease,
            )
        }
    }
}

@Composable
private fun RoundStepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(28.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun defaultDraft(type: String): PlannedDraft = PlannedDraft(
    reps = DEFAULT_REPS,
    weight = 0.0,
    durationSeconds = if (type == TYPE_DURATION) DEFAULT_DURATION_SECONDS else 0,
)

private fun plannedDraft(set: PlannedSetEntity, type: String): PlannedDraft = PlannedDraft(
    reps = set.reps ?: DEFAULT_REPS,
    weight = set.weight ?: 0.0,
    durationSeconds = set.durationSeconds ?: DEFAULT_DURATION_SECONDS,
)
