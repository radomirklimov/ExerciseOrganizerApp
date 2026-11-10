package com.organizer.presentation.screens.workout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.local.db.entities.ExerciseProgressEntity
import com.organizer.presentation.OrganizerViewModel
import com.organizer.presentation.screens.general.DeleteConfirmationDialog
import kotlin.math.roundToInt

private const val TYPE_REPS = "REPS"
private const val TYPE_REPS_WEIGHT = "REPS_WEIGHT"
private const val TYPE_DURATION = "DURATION"

private val exerciseTypes = listOf(TYPE_REPS, TYPE_REPS_WEIGHT, TYPE_DURATION)

internal fun exerciseTypeLabel(exerciseType: String): String = when (exerciseType) {
    TYPE_DURATION -> "Duration"
    TYPE_REPS -> "Reps"
    else -> "Reps + Weight"
}

/**
 * A set does not store its own type. Infer it from the metrics that were
 * recorded so old sets keep their original presentation after the user
 * switches the tracking mode.
 */
internal fun ExerciseProgressEntity.inferredExerciseType(): String = when {
    durationSeconds != null -> TYPE_DURATION
    weight != null -> TYPE_REPS_WEIGHT
    else -> TYPE_REPS
}

/**
 * The type shown for an exercise in a workout follows the sets recorded there.
 * The stored exercise type is used when there are no sets. When the most common
 * set types are tied, the first recorded set among those types wins.
 */
internal fun effectiveExerciseType(
    exercise: ExerciseEntity,
    sets: List<ExerciseProgressEntity>,
): String {
    if (sets.isEmpty()) return exercise.exerciseType

    val counts = sets.groupingBy { it.inferredExerciseType() }.eachCount()
    val maxCount = counts.values.maxOrNull() ?: 0
    val mostCommonTypes = counts.filterValues { it == maxCount }.keys

    return if (mostCommonTypes.size == 1) {
        mostCommonTypes.first()
    } else {
        sets.asSequence()
            .filter { it.inferredExerciseType() in mostCommonTypes }
            .minWithOrNull(
                compareBy<ExerciseProgressEntity> { it.setNumber }
                    .thenBy { it.progressId }
            )
            ?.inferredExerciseType()
            ?: exercise.exerciseType
    }
}

internal fun formatWeight(weight: Double?): String {
    val value = weight ?: 0.0
    return if (value == value.toLong().toDouble()) {
        "${value.toLong()} kg"
    } else {
        "$value kg"
    }
}

@Composable
fun TrackExerciseDialog(
    workoutId: Long,
    exercise: ExerciseEntity,
    sets: List<ExerciseProgressEntity>,
    viewModel: OrganizerViewModel,
    onDismiss: () -> Unit,
) {
    val effectiveType = effectiveExerciseType(exercise, sets)
    // The workout's current effective type is the default tracking mode. The
    // user can switch modes for this dialog without changing the exercise.
    var selectedType by remember(exercise.exerciseId) { mutableStateOf(effectiveType) }
    var typeTouched by remember(exercise.exerciseId) { mutableStateOf(false) }
    var repsValue by remember(exercise.exerciseId) { mutableStateOf(0) }
    var weightTenths by remember(exercise.exerciseId) { mutableStateOf(0) }
    var durationValue by remember(exercise.exerciseId) { mutableStateOf(0) }
    var editingSetId by remember(exercise.exerciseId) { mutableStateOf<Long?>(null) }
    var isAddingSet by remember(exercise.exerciseId) { mutableStateOf(false) }
    var setToDelete by remember(exercise.exerciseId) { mutableStateOf<ExerciseProgressEntity?>(null) }

    LaunchedEffect(effectiveType) {
        if (!typeTouched) {
            selectedType = effectiveType
        }
    }

    val editingSet = sets.find { it.progressId == editingSetId }
    val nextSetNumber = (sets.maxOfOrNull { it.setNumber } ?: 0) + 1
    val editorVisible = isAddingSet || editingSet != null
    val hasRequiredValues = when (selectedType) {
        TYPE_DURATION -> durationValue > 0
        TYPE_REPS -> repsValue > 0
        else -> repsValue > 0
    }

    fun resetInputs() {
        repsValue = 0
        weightTenths = 0
        durationValue = 0
        editingSetId = null
        isAddingSet = false
    }

    fun startAddingSet() {
        resetInputs()
        isAddingSet = true
    }

    fun cancelEditing() {
        resetInputs()
        typeTouched = false
        selectedType = effectiveType
    }

    fun loadSet(set: ExerciseProgressEntity) {
        typeTouched = true
        selectedType = set.inferredExerciseType()
        repsValue = set.reps ?: 0
        weightTenths = ((set.weight ?: 0.0) * 10).roundToInt()
        durationValue = set.durationSeconds ?: 0
        editingSetId = set.progressId
        isAddingSet = false
    }

    fun saveSet() {
        val reps = if (selectedType == TYPE_DURATION) null else repsValue.takeIf { it > 0 }
        val weight = if (selectedType == TYPE_REPS_WEIGHT) weightTenths / 10.0 else null
        val duration = if (selectedType == TYPE_DURATION) durationValue.takeIf { it > 0 } else null

        if (editingSet != null) {
            viewModel.updateExerciseSet(
                progressId = editingSet.progressId,
                reps = reps,
                weight = weight,
                durationSeconds = duration,
            )
        } else {
            viewModel.addExerciseSet(
                workoutId = workoutId,
                exerciseId = exercise.exerciseId,
                setNumber = nextSetNumber,
                reps = reps,
                weight = weight,
                durationSeconds = duration,
            )
        }
        resetInputs()
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
                    TrackingHeader(
                        exerciseName = exercise.name,
                        selectedType = selectedType,
                        onTypeSelected = {
                            typeTouched = true
                            selectedType = it
                        },
                        onClose = onDismiss,
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (sets.isEmpty() && !editorVisible) {
                            Text(
                                text = "No sets recorded yet.",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        sets.forEach { set ->
                            if (set.progressId == editingSetId) {
                                ActiveSetEditor(
                                    setNumber = set.setNumber,
                                    selectedType = selectedType,
                                    repsValue = repsValue,
                                    onRepsChange = { repsValue = it },
                                    weightTenths = weightTenths,
                                    onWeightChange = { weightTenths = it },
                                    durationValue = durationValue,
                                    onDurationChange = { durationValue = it },
                                    actionLabel = "Update Set ${set.setNumber}",
                                    actionEnabled = hasRequiredValues,
                                    onAction = ::saveSet,
                                    onCancel = ::cancelEditing,
                                )
                            } else {
                                RecordedSetCard(
                                    set = set,
                                    onEdit = { loadSet(set) },
                                    onDelete = { setToDelete = set },
                                )
                            }
                        }

                        if (isAddingSet) {
                            ActiveSetEditor(
                                setNumber = nextSetNumber,
                                selectedType = selectedType,
                                repsValue = repsValue,
                                onRepsChange = { repsValue = it },
                                weightTenths = weightTenths,
                                onWeightChange = { weightTenths = it },
                                durationValue = durationValue,
                                onDurationChange = { durationValue = it },
                                actionLabel = "Add Set $nextSetNumber",
                                actionEnabled = hasRequiredValues,
                                onAction = ::saveSet,
                                onCancel = ::cancelEditing,
                            )
                        } else {
                            AddSetCard(
                                setNumber = nextSetNumber,
                                onClick = ::startAddingSet,
                            )
                        }

                        if (editorVisible && !hasRequiredValues) {
                            Text(
                                text = if (selectedType == TYPE_DURATION) {
                                    "Duration must be at least 1 second."
                                } else {
                                    "Reps must be at least 1."
                                },
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
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
                        TextButton(onClick = onDismiss) {
                            Text("Close")
                        }
                        Button(
                            onClick = {
                                if (editorVisible) {
                                    saveSet()
                                }
                                onDismiss()
                            },
                            enabled = !editorVisible || hasRequiredValues,
                            modifier = Modifier.height(48.dp),
                            shape = RoundedCornerShape(50),
                        ) {
                            Text("Save")
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.DoneAll,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    setToDelete?.let { set ->
        DeleteConfirmationDialog(
            title = "Delete Set?",
            message = "Set ${set.setNumber} will be permanently deleted.",
            onConfirm = {
                viewModel.deleteExerciseSet(set.progressId)
                if (editingSetId == set.progressId) {
                    cancelEditing()
                }
                setToDelete = null
            },
            onDismiss = { setToDelete = null },
        )
    }
}

@Composable
private fun TrackingHeader(
    exerciseName: String,
    selectedType: String,
    onTypeSelected: (String) -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f))
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                ExerciseTypePill(selectedType)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Track $exerciseName",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close tracking dialog",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        ExerciseTypeTabs(
            selectedType = selectedType,
            onTypeSelected = onTypeSelected,
        )
    }
}

@Composable
private fun ExerciseTypePill(exerciseType: String) {
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
                text = exerciseTypeLabel(exerciseType),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun ExerciseTypeTabs(
    selectedType: String,
    onTypeSelected: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        exerciseTypes.forEach { type ->
            val selected = type == selectedType
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                    )
                    .selectable(
                        selected = selected,
                        onClick = { onTypeSelected(type) },
                        role = Role.Tab,
                    )
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = exerciseTypeLabel(type),
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun RecordedSetCard(
    set: ExerciseProgressEntity,
    onEdit: () -> Unit,
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
            modifier = Modifier.padding(start = 14.dp, top = 10.dp, end = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Set ${set.setNumber}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = setSummary(set),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit set ${set.setNumber}",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.outline,
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete set ${set.setNumber}",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun setSummary(set: ExerciseProgressEntity): AnnotatedString {
    val strongColor = MaterialTheme.colorScheme.onSurface
    return when (set.inferredExerciseType()) {
        TYPE_DURATION -> buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = strongColor)) {
                append("${set.durationSeconds ?: 0}s")
            }
        }

        TYPE_REPS -> buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = strongColor)) {
                append("${set.reps ?: 0} reps")
            }
        }

        else -> buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = strongColor)) {
                append("${set.reps ?: 0} reps")
            }
            append(" • ")
            append(formatWeight(set.weight))
        }
    }
}

@Composable
private fun ActiveSetEditor(
    setNumber: Int,
    selectedType: String,
    repsValue: Int,
    onRepsChange: (Int) -> Unit,
    weightTenths: Int,
    onWeightChange: (Int) -> Unit,
    durationValue: Int,
    onDurationChange: (Int) -> Unit,
    actionLabel: String,
    actionEnabled: Boolean,
    onAction: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 2.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
        ),
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Set $setNumber",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onCancel) {
                    Text("Cancel")
                }
            }

            when (selectedType) {
                TYPE_DURATION -> MetricStepper(
                    label = "DURATION",
                    valueText = durationValue.toString(),
                    unit = "sec",
                    onDecrease = {
                        if (durationValue - 5 >= 0) onDurationChange(durationValue - 5)
                    },
                    onIncrease = {
                        if (durationValue + 5 <= 3600) onDurationChange(durationValue + 5)
                    },
                )

                TYPE_REPS -> MetricStepper(
                    label = "REPETITIONS",
                    valueText = repsValue.toString(),
                    unit = "reps",
                    onDecrease = {
                        if (repsValue - 1 >= 0) onRepsChange(repsValue - 1)
                    },
                    onIncrease = {
                        if (repsValue + 1 <= 100) onRepsChange(repsValue + 1)
                    },
                )

                else -> BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    if (maxWidth < 300.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            RepsStepper(
                                modifier = Modifier.fillMaxWidth(),
                                repsValue = repsValue,
                                onRepsChange = onRepsChange,
                            )
                            WeightStepper(
                                modifier = Modifier.fillMaxWidth(),
                                weightTenths = weightTenths,
                                onWeightChange = onWeightChange,
                            )
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            RepsStepper(
                                modifier = Modifier.weight(1f),
                                repsValue = repsValue,
                                onRepsChange = onRepsChange,
                            )
                            WeightStepper(
                                modifier = Modifier.weight(1f),
                                weightTenths = weightTenths,
                                onWeightChange = onWeightChange,
                            )
                        }
                    }
                }
            }

            Button(
                onClick = onAction,
                enabled = actionEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(50),
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun RepsStepper(
    modifier: Modifier = Modifier,
    repsValue: Int,
    onRepsChange: (Int) -> Unit,
) {
    MetricStepper(
        modifier = modifier,
        label = "REPETITIONS",
        valueText = repsValue.toString(),
        unit = "reps",
        onDecrease = {
            if (repsValue - 1 >= 0) onRepsChange(repsValue - 1)
        },
        onIncrease = {
            if (repsValue + 1 <= 100) onRepsChange(repsValue + 1)
        },
    )
}

@Composable
private fun WeightStepper(
    modifier: Modifier = Modifier,
    weightTenths: Int,
    onWeightChange: (Int) -> Unit,
) {
    MetricStepper(
        modifier = modifier,
        label = "LOAD (KG)",
        valueText = formatWeightNumber(weightTenths / 10.0),
        unit = "kg",
        valueColor = MaterialTheme.colorScheme.onSurface,
        onDecrease = {
            if (weightTenths - 5 >= 0) onWeightChange(weightTenths - 5)
        },
        onIncrease = {
            if (weightTenths + 5 <= 5000) onWeightChange(weightTenths + 5)
        },
    )
}

@Composable
private fun MetricStepper(
    modifier: Modifier = Modifier,
    label: String,
    valueText: String,
    unit: String,
    valueColor: Color = MaterialTheme.colorScheme.primary,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StepperButton(
                    icon = Icons.Default.Remove,
                    contentDescription = "Decrease $label",
                    onClick = onDecrease,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = valueText,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = valueColor,
                        maxLines = 1,
                    )
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.offset(y = (-2).dp),
                    )
                }
                StepperButton(
                    icon = Icons.Default.Add,
                    contentDescription = "Increase $label",
                    onClick = onIncrease,
                )
            }
        }
    }
}

@Composable
private fun StepperButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun AddSetCard(
    setNumber: Int,
    onClick: () -> Unit,
) {
    val borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    val cornerRadius = 16.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.4f))
            .clickable(onClick = onClick)
            .drawBehind {
                val strokeWidth = 2.dp.toPx()
                drawRoundRect(
                    color = borderColor,
                    topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                    size = Size(size.width - strokeWidth, size.height - strokeWidth),
                    cornerRadius = CornerRadius(cornerRadius.toPx()),
                    style = Stroke(
                        width = strokeWidth,
                        pathEffect = PathEffect.dashPathEffect(
                            intervals = floatArrayOf(12.dp.toPx(), 8.dp.toPx())
                        ),
                    ),
                )
            }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Default.AddCircle,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Add Set $setNumber",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun formatWeightNumber(weight: Double): String {
    return if (weight == weight.toLong().toDouble()) {
        weight.toLong().toString()
    } else {
        weight.toString()
    }
}
