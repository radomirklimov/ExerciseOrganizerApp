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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.unit.dp
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.local.db.entities.ExerciseProgressEntity
import com.organizer.presentation.OrganizerViewModel
import com.organizer.presentation.screens.exercises.ExerciseListItem
import com.organizer.presentation.screens.general.DeleteConfirmationDialog
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

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
    var repsValue by remember { mutableStateOf(0) }
    var weightTenths by remember { mutableStateOf(0) }
    var durationValue by remember { mutableStateOf(0) }
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
    val trackedHistory by if (trackedExerciseId != null) {
        viewModel.observeExerciseHistory(trackedExerciseId!!).collectAsState(initial = emptyList())
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
                                            val label = when (exercise.exerciseType) {
                                                "DURATION" -> "Set ${set.setNumber}: ${set.durationSeconds ?: 0}s"
                                                "REPS" -> "Set ${set.setNumber}: ${set.reps ?: 0} reps"
                                                else -> "Set ${set.setNumber}: ${set.reps ?: 0} reps x ${set.weight ?: 0.0} kg"
                                            }
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.bodyMedium,
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
            val nextSetNumber = (trackedSets.maxOfOrNull { it.setNumber } ?: 0) + 1
            AlertDialog(
                onDismissRequest = { trackedExerciseId = null },
                title = { Text("Track ${trackedExercise.name}") },
                text = {
                    Column {
                        Text("Sets this workout")
                        if (trackedSets.isEmpty()) {
                            Text("No sets recorded yet.")
                        } else {
                            trackedSets.forEach { set ->
                                val label = when (trackedExercise.exerciseType) {
                                    "DURATION" -> "Set ${set.setNumber}: ${set.durationSeconds ?: 0}s"
                                    "REPS" -> "Set ${set.setNumber}: ${set.reps ?: 0} reps"
                                    else -> "Set ${set.setNumber}: ${set.reps ?: 0} reps x ${set.weight ?: 0.0} kg"
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = label,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    IconButton(onClick = { viewModel.deleteExerciseSet(set.progressId) }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete set",
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        when (trackedExercise.exerciseType) {
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
                                    label = "Weight (0.1 kg)",
                                    value = weightTenths,
                                    onValueChange = { weightTenths = it },
                                    step = 1,
                                    max = 5000,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Recent history")
                        if (trackedHistory.isEmpty()) {
                            Text("No previous records.")
                        } else {
                            trackedHistory.take(5).forEach { set ->
                                val label = when (trackedExercise.exerciseType) {
                                    "DURATION" -> "${set.durationSeconds ?: 0}s"
                                    "REPS" -> "${set.reps ?: 0} reps"
                                    else -> "${set.reps ?: 0} reps x ${set.weight ?: 0.0} kg"
                                }
                                Text("Set ${set.setNumber}: $label")
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.addExerciseSet(
                            workoutId = workoutId,
                            exerciseId = trackedExercise.exerciseId,
                            setNumber = nextSetNumber,
                            reps = if (trackedExercise.exerciseType == "DURATION") null else repsValue.takeIf { it > 0 },
                            weight = if (trackedExercise.exerciseType == "REPS_WEIGHT") weightTenths / 10.0 else null,
                            durationSeconds = if (trackedExercise.exerciseType == "DURATION") durationValue.takeIf { it > 0 } else null,
                        )
                        repsValue = 0
                        weightTenths = 0
                        durationValue = 0
                    }) {
                        Text("Add Set")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { trackedExerciseId = null }) {
                        Text("Close")
                    }
                },
            )
        }
    }
}

@Composable
private fun Stepper(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    step: Int,
    max: Int,
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
            Text(text = value.toString(), modifier = Modifier.padding(horizontal = 8.dp))
            IconButton(onClick = { if (value + step <= max) onValueChange(value + step) }) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Increase")
            }
        }
    }
}
