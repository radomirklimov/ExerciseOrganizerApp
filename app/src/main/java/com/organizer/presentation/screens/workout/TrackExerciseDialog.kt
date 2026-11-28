package com.organizer.presentation.screens.workout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.organizer.data.local.db.entities.ExerciseEntity
import android.content.Context
import com.organizer.R
import androidx.compose.ui.res.stringResource
import com.organizer.data.local.db.entities.ExerciseProgressEntity
import com.organizer.data.local.db.entities.PlannedSetEntity
import com.organizer.data.preferences.UnitSystem
import com.organizer.presentation.OrganizerViewModel
import com.organizer.presentation.screens.general.DeleteConfirmationDialog
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

internal const val TYPE_REPS = "REPS"
internal const val TYPE_REPS_WEIGHT = "REPS_WEIGHT"
internal const val TYPE_DURATION = "DURATION"

private val exerciseTypes = listOf(TYPE_REPS, TYPE_REPS_WEIGHT, TYPE_DURATION)

@Composable
internal fun exerciseTypeLabel(exerciseType: String): String = when (exerciseType) {
    TYPE_DURATION -> stringResource(R.string.exercise_type_duration)
    TYPE_REPS -> stringResource(R.string.exercise_type_reps)
    else -> stringResource(R.string.exercise_type_reps_weight)
}

/**
 * A set does not store its own type. Infer it from the metrics that were
 * recorded so old sets keep their original presentation after the user
 * switches the tracking mode.
 */
internal fun ExerciseProgressEntity.inferredExerciseType(): String =
    inferSetType(durationSeconds, weight)

internal fun PlannedSetEntity.inferredExerciseType(): String =
    inferSetType(durationSeconds, weight)

private fun inferSetType(durationSeconds: Int?, weight: Double?): String = when {
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
): String = effectiveSetType(
    fallback = exercise.exerciseType,
    entries = sets.map {
        Triple(it.setNumber, it.progressId, it.inferredExerciseType())
    },
)

/** Same rule as [effectiveExerciseType], for the workout's planned sets. */
internal fun effectivePlannedSetType(
    exercise: ExerciseEntity,
    sets: List<PlannedSetEntity>,
): String = effectiveSetType(
    fallback = exercise.exerciseType,
    entries = sets.map {
        Triple(it.setNumber, it.setNumber.toLong(), it.inferredExerciseType())
    },
)

private fun effectiveSetType(
    fallback: String,
    entries: List<Triple<Int, Long, String>>,
): String {
    if (entries.isEmpty()) return fallback

    val counts = entries.groupingBy { it.third }.eachCount()
    val maxCount = counts.values.maxOrNull() ?: 0
    val mostCommonTypes = counts.filterValues { it == maxCount }.keys

    return if (mostCommonTypes.size == 1) {
        mostCommonTypes.first()
    } else {
        entries.asSequence()
            .filter { it.third in mostCommonTypes }
            .minWithOrNull(
                compareBy<Triple<Int, Long, String>> { it.first }
                    .thenBy { it.second }
            )
            ?.third
            ?: fallback
    }
}

/** Locale-aware weight formatting for UI call sites. */
internal fun formatWeight(
    context: Context,
    weight: Double?,
    unitSystem: UnitSystem = UnitSystem.METRIC,
): String = formatWeight(weight, context.getString(unitSystem.labelRes))

/** Pure formatting helper, also used by unit tests. */
internal fun formatWeight(
    weight: Double?,
    unitSystem: UnitSystem = UnitSystem.METRIC,
): String = formatWeight(weight, unitSystem.label)

private fun formatWeight(
    weight: Double?,
    unitLabel: String,
): String {
    val value = weight ?: 0.0
    return if (value == value.toLong().toDouble()) {
        "${value.toLong()} $unitLabel"
    } else {
        "$value $unitLabel"
    }
}

internal fun formatDuration(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    val seconds = safe % 60

    return buildList {
        if (hours > 0) add("${hours}h")
        if (hours > 0 || minutes > 0) add("${minutes}m")
        add("${seconds}s")
    }.joinToString(" ")
}

private data class DurationParts(
    val hours: String = "",
    val minutes: String = "",
    val seconds: String = "",
) {
    val totalSeconds: Int
        get() = (hours.toIntOrNull() ?: 0) * 3600 +
            (minutes.toIntOrNull() ?: 0) * 60 +
            (seconds.toIntOrNull() ?: 0)

    companion object {
        fun fromSeconds(totalSeconds: Int): DurationParts {
            val safe = totalSeconds.coerceAtLeast(0)
            return DurationParts(
                hours = (safe / 3600).takeIf { it > 0 }?.toString().orEmpty(),
                minutes = ((safe % 3600) / 60).takeIf { it > 0 }?.toString().orEmpty(),
                seconds = (safe % 60).takeIf { it > 0 }?.toString().orEmpty(),
            )
        }
    }
}

/**
 * One editable row of [SetEditorDialog]: storage rows are mapped to this
 * shape so the set list, steppers and delete flow are shared.
 */
internal data class SetEditorEntry(
    val id: Long,
    val setNumber: Int,
    val reps: Int?,
    val weight: Double?,
    val durationSeconds: Int?,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
)

internal fun SetEditorEntry.inferredExerciseType(): String = when {
    durationSeconds != null -> TYPE_DURATION
    weight != null -> TYPE_REPS_WEIGHT
    else -> TYPE_REPS
}

/**
 * Records real sets for one exercise during a workout. The UI lives in
 * [SetEditorDialog]; this wrapper only maps recorded sets to editor rows and
 * writes the edits to the progress table.
 */
@Composable
fun TrackExerciseDialog(
    workoutId: Long,
    exercise: ExerciseEntity,
    sets: List<ExerciseProgressEntity>,
    viewModel: OrganizerViewModel,
    onDismiss: () -> Unit,
    startAddingSet: Boolean = false,
    sessionId: Long = 0L,
    initialReps: Int? = null,
    initialWeight: Double? = null,
    initialDurationSeconds: Int? = null,
    unitSystem: UnitSystem = UnitSystem.METRIC,
) {
    SetEditorDialog(
        stateKey = exercise.exerciseId,
        title = stringResource(R.string.track_exercise_title, exercise.name),
        exerciseName = exercise.name,
        initialType = effectiveExerciseType(exercise, sets),
        sets = sets.map { it.toEditorEntry() },
        emptyText = stringResource(R.string.track_no_sets),
        startAddingSet = startAddingSet,
        initialReps = initialReps,
        initialWeight = initialWeight,
        initialDurationSeconds = initialDurationSeconds,
        unitSystem = unitSystem,
        onAddSet = { reps, weight, duration, unit ->
            viewModel.addExerciseSet(
                workoutId = workoutId,
                exerciseId = exercise.exerciseId,
                setNumber = (sets.maxOfOrNull { it.setNumber } ?: 0) + 1,
                reps = reps,
                weight = weight,
                durationSeconds = duration,
                sessionId = sessionId,
                unitSystem = unit.name,
            )
        },
        onUpdateSet = { id, reps, weight, duration ->
            viewModel.updateExerciseSet(
                progressId = id,
                reps = reps,
                weight = weight,
                durationSeconds = duration,
            )
        },
        onDeleteSet = { entry -> viewModel.deleteExerciseSet(entry.id) },
        onSave = onDismiss,
        onClose = onDismiss,
    )
}

/**
 * The workout editor's "Track" popup. It edits the routine's planned sets,
 * which are persisted through [com.organizer.data.repo.PlannedSetRepository]
 * and the PLANNED_SET sync outbox, so they survive a reinstall. Logged
 * execution progress stays local-only and is not touched here.
 */
@Composable
internal fun PlannedSetEditorDialog(
    workoutId: Long,
    exercise: ExerciseEntity,
    sets: List<PlannedSetEntity>,
    viewModel: OrganizerViewModel,
    onDismiss: () -> Unit,
    unitSystem: UnitSystem = UnitSystem.METRIC,
) {
    SetEditorDialog(
        stateKey = exercise.exerciseId,
        title = stringResource(R.string.track_exercise_title, exercise.name),
        exerciseName = exercise.name,
        initialType = effectivePlannedSetType(exercise, sets),
        sets = sets.map { it.toEditorEntry(unitSystem) },
        emptyText = stringResource(R.string.track_no_sets),
        unitSystem = unitSystem,
        onAddSet = { reps, weight, duration, _ ->
            viewModel.addPlannedSet(
                workoutId = workoutId,
                exerciseId = exercise.exerciseId,
                setNumber = nextPlannedSetNumber(sets),
                reps = reps,
                weight = weight,
                durationSeconds = duration,
            )
        },
        onUpdateSet = { id, reps, weight, duration ->
            viewModel.updatePlannedSet(
                workoutId = workoutId,
                exerciseId = exercise.exerciseId,
                setNumber = id.toInt(),
                reps = reps,
                weight = weight,
                durationSeconds = duration,
            )
        },
        onDeleteSet = { entry ->
            viewModel.deletePlannedSet(workoutId, exercise.exerciseId, entry.setNumber)
        },
        onSave = onDismiss,
        onClose = onDismiss,
        deletePermanently = false,
    )
}

/** The repository keys planned sets by set number, so the editor row id is it. */
internal fun PlannedSetEntity.toEditorEntry(
    unitSystem: UnitSystem = UnitSystem.METRIC,
): SetEditorEntry = SetEditorEntry(
    id = setNumber.toLong(),
    setNumber = setNumber,
    reps = reps,
    weight = weight,
    durationSeconds = durationSeconds,
    unitSystem = unitSystem,
)

/** Maps a locally recorded execution set to a shared editor row. */
internal fun ExerciseProgressEntity.toEditorEntry(): SetEditorEntry = SetEditorEntry(
    id = progressId,
    setNumber = setNumber,
    reps = reps,
    weight = weight,
    durationSeconds = durationSeconds,
    unitSystem = UnitSystem.entries.firstOrNull { it.name == unitSystem }
        ?: UnitSystem.METRIC,
)

internal fun nextPlannedSetNumber(sets: List<PlannedSetEntity>): Int =
    (sets.maxOfOrNull { it.setNumber } ?: 0) + 1

/**
 * Shared set editor: set list, inline add/edit sheet with drag steppers, type
 * tabs and the delete confirmation. Callers decide how the edits are stored.
 */
@Composable
internal fun SetEditorDialog(
    stateKey: Long,
    title: String,
    exerciseName: String,
    initialType: String,
    sets: List<SetEditorEntry>,
    emptyText: String,
    onAddSet: (reps: Int?, weight: Double?, durationSeconds: Int?, unitSystem: UnitSystem) -> Unit,
    onUpdateSet: (id: Long, reps: Int?, weight: Double?, durationSeconds: Int?) -> Unit,
    onDeleteSet: (SetEditorEntry) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    startAddingSet: Boolean = false,
    initialReps: Int? = null,
    initialWeight: Double? = null,
    initialDurationSeconds: Int? = null,
    deletePermanently: Boolean = true,
    unitSystem: UnitSystem = UnitSystem.METRIC,
) {
    // The workout's current effective type is the default tracking mode. The
    // user can switch modes for this dialog without changing the exercise.
    var selectedType by remember(stateKey) { mutableStateOf(initialType) }
    var typeTouched by remember(stateKey) { mutableStateOf(false) }
    var repsValue by remember(stateKey) { mutableStateOf(initialReps ?: 0) }
    var weightTenths by remember(stateKey) {
        mutableStateOf(((initialWeight ?: 0.0) * 10).roundToInt())
    }
    var durationParts by remember(stateKey) {
        mutableStateOf(
            initialDurationSeconds
                ?.takeIf { it > 0 }
                ?.let { DurationParts.fromSeconds(it) }
                ?: DurationParts()
        )
    }
    var editingSetId by remember(stateKey) { mutableStateOf<Long?>(null) }
    var isAddingSet by remember(stateKey) { mutableStateOf(startAddingSet) }
    var setToDelete by remember(stateKey) { mutableStateOf<SetEditorEntry?>(null) }
    // The unit a set is tracked in: the selected unit for new sets, the unit
    // the edited set was recorded with for existing ones.
    var editorUnit by remember(stateKey) { mutableStateOf(unitSystem) }

    LaunchedEffect(initialType) {
        if (!typeTouched) {
            selectedType = initialType
        }
    }

    val editingSet = sets.find { it.id == editingSetId }
    val nextSetNumber = (sets.maxOfOrNull { it.setNumber } ?: 0) + 1
    val editorVisible = isAddingSet || editingSet != null
    val hasRequiredValues = when (selectedType) {
        TYPE_DURATION -> durationParts.totalSeconds > 0
        TYPE_REPS -> repsValue > 0
        else -> repsValue > 0
    }

    fun resetInputs() {
        repsValue = 0
        weightTenths = 0
        durationParts = DurationParts()
        editingSetId = null
        isAddingSet = false
        editorUnit = unitSystem
    }

    fun startAddingSet() {
        resetInputs()
        isAddingSet = true
    }

    fun cancelEditing() {
        resetInputs()
        typeTouched = false
        selectedType = initialType
    }

    fun loadSet(entry: SetEditorEntry) {
        typeTouched = true
        selectedType = entry.inferredExerciseType()
        editorUnit = entry.unitSystem
        repsValue = entry.reps ?: 0
        weightTenths = ((entry.weight ?: 0.0) * 10).roundToInt()
        durationParts = DurationParts.fromSeconds(entry.durationSeconds ?: 0)
        editingSetId = entry.id
        isAddingSet = false
    }

    fun saveSet() {
        val reps = if (selectedType == TYPE_DURATION) null else repsValue.takeIf { it > 0 }
        val weight = if (selectedType == TYPE_REPS_WEIGHT) weightTenths / 10.0 else null
        val duration = if (selectedType == TYPE_DURATION) {
            durationParts.totalSeconds.takeIf { it > 0 }
        } else {
            null
        }

        if (editingSet != null) {
            onUpdateSet(editingSet.id, reps, weight, duration)
        } else {
            onAddSet(reps, weight, duration, editorUnit)
        }
        resetInputs()
    }

    Dialog(
        onDismissRequest = onClose,
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
                    SetEditorHeader(
                        title = title,
                        selectedType = selectedType,
                        onTypeSelected = {
                            typeTouched = true
                            selectedType = it
                        },
                        onClose = onClose,
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
                                text = emptyText,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        sets.forEach { set ->
                            if (set.id == editingSetId) {
                                ActiveSetEditor(
                                    setNumber = set.setNumber,
                                    selectedType = selectedType,
                                    repsValue = repsValue,
                                    onRepsChange = { repsValue = it },
                                    weightTenths = weightTenths,
                                    onWeightChange = { weightTenths = it },
                                    durationParts = durationParts,
                                    onDurationPartsChange = { durationParts = it },
                                    actionLabel = stringResource(R.string.track_update_set, set.setNumber),
                                    actionEnabled = hasRequiredValues,
                                    onAction = ::saveSet,
                                    onCancel = ::cancelEditing,
                                    unitSystem = editorUnit,
                                )
                            } else {
                                RecordedSetCard(
                                    entry = set,
                                    onEdit = { loadSet(set) },
                                    onDelete = { setToDelete = set },
                                    unitSystem = unitSystem,
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
                                durationParts = durationParts,
                                onDurationPartsChange = { durationParts = it },
                                actionLabel = stringResource(R.string.track_add_set, nextSetNumber),
                                actionEnabled = hasRequiredValues,
                                onAction = ::saveSet,
                                onCancel = ::cancelEditing,
                                unitSystem = editorUnit,
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
                                    stringResource(R.string.track_duration_min)
                                } else {
                                    stringResource(R.string.track_reps_min)
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
                        TextButton(onClick = onClose) {
                            Text(stringResource(R.string.profile_close))
                        }
                        Button(
                            onClick = {
                                if (editorVisible) {
                                    saveSet()
                                }
                                onSave()
                            },
                            enabled = !editorVisible || hasRequiredValues,
                            modifier = Modifier.height(48.dp),
                            shape = RoundedCornerShape(50),
                        ) {
                            Text(stringResource(R.string.profile_save))
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

    setToDelete?.let { entry ->
        DeleteConfirmationDialog(
            title = stringResource(R.string.track_delete_set_title),
            itemName = stringResource(R.string.track_set_number, entry.setNumber),
            location = exerciseName,
            isPermanent = deletePermanently,
            onConfirm = {
                onDeleteSet(entry)
                if (editingSetId == entry.id) {
                    cancelEditing()
                }
                setToDelete = null
            },
            onDismiss = { setToDelete = null },
        )
    }
}

@Composable
private fun SetEditorHeader(
    title: String,
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
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.track_close_editor),
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
    entry: SetEditorEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    unitSystem: UnitSystem = UnitSystem.METRIC,
) {
    val context = LocalContext.current
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
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryFixed),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = entry.setNumber.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryFixed,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
                Text(
                    text = stringResource(R.string.track_set_label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (entry.inferredExerciseType()) {
                    TYPE_DURATION -> RecordedSetValue(
                        text = formatDuration(entry.durationSeconds ?: 0),
                    )

                    TYPE_REPS -> RecordedSetValue(
                        text = stringResource(R.string.track_reps_value, entry.reps ?: 0),
                    )

                    else -> {
                        RecordedSetValue(
                            text = stringResource(R.string.track_reps_value, entry.reps ?: 0),
                        )
                        RecordedSetValue(
                            text = formatWeight(context, entry.weight, entry.unitSystem),
                        )
                    }
                }
            }

            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(R.string.track_edit_set, entry.setNumber),
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
                    contentDescription = stringResource(R.string.track_delete_set, entry.setNumber),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun RecordedSetValue(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ActiveSetEditor(
    setNumber: Int,
    selectedType: String,
    repsValue: Int,
    onRepsChange: (Int) -> Unit,
    weightTenths: Int,
    onWeightChange: (Int) -> Unit,
    durationParts: DurationParts,
    onDurationPartsChange: (DurationParts) -> Unit,
    actionLabel: String,
    actionEnabled: Boolean,
    onAction: () -> Unit,
    onCancel: () -> Unit,
    unitSystem: UnitSystem = UnitSystem.METRIC,
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
                    text = stringResource(R.string.track_set_number, setNumber),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.profile_cancel))
                }
            }

            when (selectedType) {
                TYPE_DURATION -> DurationInput(
                    value = durationParts,
                    onValueChange = onDurationPartsChange,
                )

                TYPE_REPS -> RepsStepper(
                    repsValue = repsValue,
                    onRepsChange = onRepsChange,
                )

                else -> Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    RepsStepper(
                        modifier = Modifier.fillMaxWidth(),
                        repsValue = repsValue,
                        onRepsChange = onRepsChange,
                    )
                    WeightStepper(
                        modifier = Modifier.fillMaxWidth(),
                        weightTenths = weightTenths,
                        onWeightChange = onWeightChange,
                        unitSystem = unitSystem,
                    )
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
private fun DurationInput(
    value: DurationParts,
    onValueChange: (DurationParts) -> Unit,
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
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.track_duration_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                DurationPartField(
                    value = value.hours,
                    onValueChange = { onValueChange(value.copy(hours = it)) },
                    max = 99,
                )
                DurationSeparator()
                DurationPartField(
                    value = value.minutes,
                    onValueChange = { onValueChange(value.copy(minutes = it)) },
                    max = 59,
                )
                DurationSeparator()
                DurationPartField(
                    value = value.seconds,
                    onValueChange = { onValueChange(value.copy(seconds = it)) },
                    max = 59,
                )
            }
        }
    }
}

@Composable
private fun DurationSeparator() {
    Text(
        text = ":",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DurationPartField(
    value: String,
    onValueChange: (String) -> Unit,
    max: Int,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(sanitizeTimePart(it, max)) },
        modifier = Modifier.width(60.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.titleLarge.copy(
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold,
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(12.dp),
        placeholder = {
            Text(
                text = "00",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.outline,
            )
        },
    )
}

private fun sanitizeTimePart(input: String, max: Int): String {
    val digits = input.filter { it.isDigit() }.take(2)
    if (digits.isEmpty()) return ""

    return if (digits.toInt() > max) {
        max.toString().padStart(digits.length, '0')
    } else {
        digits
    }
}

internal fun dragStepsForDistance(dragPx: Float, density: Float): Int {
    if (dragPx == 0f) return 0

    val dragDp = abs(dragPx) / density
    val magnitude = (dragDp / 8f).pow(1.8f).toInt()
    return if (dragPx > 0) magnitude else -magnitude
}

internal fun adjustedDragValue(
    startValue: Int,
    steps: Int,
    stepSize: Int,
    range: IntRange,
): Int {
    val rawValue = startValue.toLong() + steps.toLong() * stepSize.toLong()
    return rawValue.coerceIn(range.first.toLong(), range.last.toLong()).toInt()
}

@Composable
private fun RepsStepper(
    modifier: Modifier = Modifier,
    repsValue: Int,
    onRepsChange: (Int) -> Unit,
) {
    MetricStepper(
        modifier = modifier,
        label = stringResource(R.string.track_repetitions_label),
        valueText = repsValue.toString(),
        unit = stringResource(R.string.track_reps_unit),
        dragValue = repsValue,
        dragValueRange = 0..Int.MAX_VALUE,
        dragStepSize = 1,
        onDragValueChange = onRepsChange,
        onDecrease = {
            if (repsValue > 0) onRepsChange(repsValue - 1)
        },
        onIncrease = {
            if (repsValue < Int.MAX_VALUE) onRepsChange(repsValue + 1)
        },
    )
}

@Composable
private fun WeightStepper(
    modifier: Modifier = Modifier,
    weightTenths: Int,
    onWeightChange: (Int) -> Unit,
    unitSystem: UnitSystem = UnitSystem.METRIC,
) {
    MetricStepper(
        modifier = modifier,
        label = stringResource(R.string.track_load_label, stringResource(unitSystem.labelRes)),
        valueText = formatWeightNumber(weightTenths / 10.0),
        unit = stringResource(unitSystem.labelRes),
        valueColor = MaterialTheme.colorScheme.onSurface,
        dragValue = weightTenths,
        dragValueRange = 0..Int.MAX_VALUE,
        dragStepSize = 5,
        onDragValueChange = onWeightChange,
        onDecrease = {
            if (weightTenths >= 5) onWeightChange(weightTenths - 5)
        },
        onIncrease = {
            if (weightTenths <= Int.MAX_VALUE - 5) onWeightChange(weightTenths + 5)
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
    dragValue: Int,
    dragValueRange: IntRange,
    dragStepSize: Int,
    onDragValueChange: (Int) -> Unit,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    val currentDragValue by rememberUpdatedState(dragValue)
    val currentOnDragValueChange by rememberUpdatedState(onDragValueChange)

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
            modifier = Modifier
                .pointerInput(Unit) {
                    var dragPx = 0f
                    var startValue = 0
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dragPx = 0f
                            startValue = currentDragValue
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            dragPx += dragAmount
                            val steps = dragStepsForDistance(dragPx, density)
                            val newValue = adjustedDragValue(
                                startValue = startValue,
                                steps = steps,
                                stepSize = dragStepSize,
                                range = dragValueRange,
                            )
                            if (newValue != currentDragValue) {
                                currentOnDragValueChange(newValue)
                            }
                        },
                        onDragEnd = { dragPx = 0f },
                        onDragCancel = { dragPx = 0f },
                    )
                }
                .padding(8.dp),
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
                    contentDescription = stringResource(R.string.track_decrease, label),
                    onClick = onDecrease,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
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
                    contentDescription = stringResource(R.string.track_increase, label),
                    onClick = onIncrease,
                )
            }
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            ) {
                DragHintArrow(icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft)
                Text(
                    text = stringResource(R.string.track_drag),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                )
                DragHintArrow(icon = Icons.AutoMirrored.Filled.KeyboardArrowRight)
            }
        }
    }
}

@Composable
private fun DragHintArrow(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(
        modifier = Modifier.size(18.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                text = stringResource(R.string.track_add_set, setNumber),
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
