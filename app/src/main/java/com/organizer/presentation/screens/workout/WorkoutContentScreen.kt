package com.organizer.presentation.screens.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.local.db.entities.ExerciseProgressEntity
import com.organizer.presentation.OrganizerViewModel
import com.organizer.presentation.screens.exercises.ExerciseListItem
import com.organizer.presentation.screens.general.DeleteConfirmationDialog
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.roundToInt

@Composable
fun WorkoutContentScreen(
    workoutId: Long,
    onOpenExerciseClick: (ExerciseEntity) -> Unit,
    onBackClick: () -> Unit,
    viewModel: OrganizerViewModel,
) {
    val workout by viewModel.observeWorkout(workoutId).collectAsState(initial = null)
    val exercises by viewModel.observeWorkoutExercises(workoutId).collectAsState(initial = emptyList())
    var expandedExerciseId by remember { mutableStateOf<Long?>(null) }
    var trackedExerciseId by remember { mutableStateOf<Long?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    var exerciseList by remember {
        mutableStateOf(emptyList<ExerciseEntity>())
    }

    LaunchedEffect(exercises) {
        exerciseList = exercises
        viewModel.prioritizeWorkoutMedia(exercises)
    }

    val lazyListState = rememberLazyListState()

    val reorderableState = rememberReorderableLazyListState(
        lazyListState = lazyListState,
        onMove = { from, to ->
            exerciseList = exerciseList.toMutableList().apply {
                val item = removeAt(from.index)
                add(to.index, item)
            }

            viewModel.updateExerciseOrder(workoutId, exerciseList)
        }
    )

    val trackedSets by if (trackedExerciseId != null) {
        viewModel.observeExerciseSets(workoutId, trackedExerciseId!!).collectAsState(initial = emptyList())
    } else {
        remember { mutableStateOf(emptyList<com.organizer.data.local.db.entities.ExerciseProgressEntity>()) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 16.dp)
    ) {

        WorkoutHeader(
            workout = workout,
            onBackClick = onBackClick
        )

        Spacer(modifier = Modifier.height(28.dp))

        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(top = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            items(
                items = exerciseList,
                key = { it.exerciseId }
            ) { exercise ->
                ReorderableItem(
                    state = reorderableState,
                    key = exercise.exerciseId
                ) {
                    val sets by viewModel.observeExerciseSets(workoutId, exercise.exerciseId)
                        .collectAsState(initial = emptyList())

                    Column {
                        ExerciseListItem(
                            modifier = Modifier.longPressDraggableHandle(),
                            dragHandle = {
                                Icon(
                                    imageVector = Icons.Rounded.DragIndicator,
                                    contentDescription = "Reorder exercise"
                                )
                            },
                            exercise = exercise,
                            deletionInfo = Pair(
                                "Delete Exercise?",
                                "This exercise will be deleted from the workout."
                            ),
                            expanded = expandedExerciseId == exercise.exerciseId,
                            onClick = {
                                expandedExerciseId =
                                    if (expandedExerciseId == exercise.exerciseId)
                                        null
                                    else
                                        exercise.exerciseId
                            },
                            onOpenClick = { onOpenExerciseClick(exercise) },
                            onDeleteClick = {
                                viewModel.deleteExerciseFromWorkout(
                                    workoutId,
                                    exercise.exerciseId
                                )
                            },
                            onTrackClick = {
                                trackedExerciseId = exercise.exerciseId
                            },
                            expandedContent = {
                                if (sets.isNotEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        sets.forEach { set ->
                                            ExerciseSetRow(
                                                exerciseType = exercise.exerciseType,
                                                set = set,
                                            )
                                        }
                                    }
                                }
                            },
                            viewModel = viewModel
                        )
                    }
                }
            }
        }

        Button(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            ),
            onClick = { showDeleteDialog = true },
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Delete Workout")
        }

        if (showDeleteDialog) {
            DeleteConfirmationDialog(
                title = "Delete Workout?",
                message = "This workout will be permanently deleted.",
                onConfirm = {
                    viewModel.deleteWorkout(workoutId)
                    showDeleteDialog = false
                    onBackClick()
                },
                onDismiss = {
                    showDeleteDialog = false
                }
            )
        }

        val trackedExercise = exerciseList.find { it.exerciseId == trackedExerciseId }
        if (trackedExerciseId != null && trackedExercise != null) {
            TrackExerciseDialog(
                workoutId = workoutId,
                exercise = trackedExercise,
                sets = trackedSets,
                viewModel = viewModel,
                onDismiss = { trackedExerciseId = null },
            )
        }
    }
}

@Composable
private fun TrackExerciseDialog(
    workoutId: Long,
    exercise: ExerciseEntity,
    sets: List<ExerciseProgressEntity>,
    viewModel: OrganizerViewModel,
    onDismiss: () -> Unit,
) {
    var repsValue by remember(exercise.exerciseId) { mutableStateOf(0) }
    var weightTenths by remember(exercise.exerciseId) { mutableStateOf(0) }
    var durationValue by remember(exercise.exerciseId) { mutableStateOf(0) }
    var editingSetId by remember(exercise.exerciseId) { mutableStateOf<Long?>(null) }
    var setToDelete by remember(exercise.exerciseId) { mutableStateOf<ExerciseProgressEntity?>(null) }

    val editingSet = sets.find { it.progressId == editingSetId }
    val nextSetNumber = (sets.maxOfOrNull { it.setNumber } ?: 0) + 1
    val hasRequiredValues = when (exercise.exerciseType) {
        "DURATION" -> durationValue > 0
        "REPS" -> repsValue > 0
        else -> repsValue > 0 && weightTenths > 0
    }

    fun resetInputs() {
        repsValue = 0
        weightTenths = 0
        durationValue = 0
        editingSetId = null
    }

    fun loadSet(set: ExerciseProgressEntity) {
        when (exercise.exerciseType) {
            "DURATION" -> durationValue = set.durationSeconds ?: 0
            "REPS" -> repsValue = set.reps ?: 0
            else -> {
                repsValue = set.reps ?: 0
                weightTenths = ((set.weight ?: 0.0) * 10).roundToInt()
            }
        }
        editingSetId = set.progressId
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Track ${exercise.name}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text("Sets this workout")
                if (sets.isEmpty()) {
                    Text("No sets recorded yet.")
                } else {
                    sets.forEach { set ->
                        ExerciseSetRow(
                            exerciseType = exercise.exerciseType,
                            set = set,
                            highlighted = set.progressId == editingSetId,
                            trailing = {
                                Row {
                                    IconButton(onClick = { loadSet(set) }) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit set",
                                        )
                                    }
                                    IconButton(onClick = { setToDelete = set }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete set",
                                        )
                                    }
                                }
                            },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = editingSet?.let { "Edit set ${it.setNumber}" } ?: "Add a new set",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (editingSet != null) {
                        TextButton(onClick = { resetInputs() }) {
                            Text("Cancel")
                        }
                    }
                }

                when (exercise.exerciseType) {
                    "DURATION" -> {
                        Stepper(
                            label = "Duration (s)",
                            value = durationValue,
                            onValueChange = { durationValue = it },
                            step = 5,
                            max = 3600,
                        )
                    }
                    "REPS" -> {
                        Stepper(
                            label = "Reps",
                            value = repsValue,
                            onValueChange = { repsValue = it },
                            step = 1,
                            max = 100,
                        )
                    }
                    else -> {
                        Stepper(
                            label = "Reps",
                            value = repsValue,
                            onValueChange = { repsValue = it },
                            step = 1,
                            max = 100,
                        )
                        Stepper(
                            label = "Weight",
                            value = weightTenths,
                            onValueChange = { weightTenths = it },
                            step = 5,
                            max = 5000,
                            displayValue = formatWeight(weightTenths / 10.0),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val reps = if (exercise.exerciseType == "DURATION") null else repsValue.takeIf { it > 0 }
                    val weight = if (exercise.exerciseType == "REPS_WEIGHT") weightTenths / 10.0 else null
                    val duration = if (exercise.exerciseType == "DURATION") durationValue.takeIf { it > 0 } else null

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
                },
                enabled = hasRequiredValues,
            ) {
                Text(if (editingSet != null) "Update Set" else "Add Set")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )

    setToDelete?.let { set ->
        DeleteConfirmationDialog(
            title = "Delete Set?",
            message = "Set ${set.setNumber} will be permanently deleted.",
            onConfirm = {
                viewModel.deleteExerciseSet(set.progressId)
                if (editingSetId == set.progressId) resetInputs()
                setToDelete = null
            },
            onDismiss = { setToDelete = null },
        )
    }
}

@Composable
private fun ExerciseSetRow(
    exerciseType: String,
    set: ExerciseProgressEntity,
    highlighted: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val textStyle = MaterialTheme.typography.bodyMedium.copy(
        fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Set ${set.setNumber}",
            modifier = Modifier.weight(1f),
            style = textStyle,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        when (exerciseType) {
            "DURATION" -> Text(
                text = "${set.durationSeconds ?: 0}s",
                modifier = Modifier.weight(1f),
                style = textStyle,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            "REPS" -> Text(
                text = "${set.reps ?: 0} reps",
                modifier = Modifier.weight(1f),
                style = textStyle,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            else -> {
                Text(
                    text = "${set.reps ?: 0} reps",
                    modifier = Modifier.weight(1f),
                    style = textStyle,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatWeight(set.weight),
                    modifier = Modifier.weight(1f),
                    style = textStyle,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

private fun formatWeight(weight: Double?): String {
    val value = weight ?: 0.0
    return if (value == value.toLong().toDouble()) {
        "${value.toLong()} kg"
    } else {
        "$value kg"
    }
}

@Composable
private fun Stepper(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    step: Int,
    max: Int,
    displayValue: String = value.toString(),
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (value - step >= 0) onValueChange(value - step) }) {
                Icon(imageVector = Icons.Default.Remove, contentDescription = "Decrease")
            }
            Text(text = displayValue, modifier = Modifier.padding(horizontal = 8.dp))
            IconButton(onClick = { if (value + step <= max) onValueChange(value + step) }) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Increase")
            }
        }
    }
}
