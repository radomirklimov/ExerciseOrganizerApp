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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
                            displayExerciseType = effectiveExerciseType(exercise, sets),
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
                                                exerciseType = set.inferredExerciseType(),
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
private fun ExerciseSetRow(
    exerciseType: String,
    set: ExerciseProgressEntity,
) {
    val textStyle = MaterialTheme.typography.bodyMedium.copy(
        fontWeight = FontWeight.Normal,
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
    }
}
