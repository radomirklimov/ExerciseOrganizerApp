package com.organizer.presentation.screens.workout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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
    onAddStandardExercisesClick: () -> Unit,
    onAddCustomExercisesClick: () -> Unit,
    viewModel: OrganizerViewModel,
) {
    val workout by viewModel.observeWorkout(workoutId).collectAsState(initial = null)
    val exercises by viewModel.observeWorkoutExercises(workoutId).collectAsState(initial = emptyList())
    var expandedExerciseId by remember { mutableStateOf<Long?>(null) }
    var trackedExerciseId by remember { mutableStateOf<Long?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAddExerciseDialog by remember { mutableStateOf(false) }

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
        remember { mutableStateOf(emptyList<ExerciseProgressEntity>()) }
    }

    // Session notes: keep a local buffer so typing does not fight Room re-emissions.
    var notesText by remember { mutableStateOf("") }
    var notesInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(workout) {
        val currentWorkout = workout
        if (!notesInitialized && currentWorkout != null) {
            notesText = currentWorkout.notes
            notesInitialized = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp)
    ) {

        // Top app bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onBackClick,
                shape = RoundedCornerShape(50),
                color = Color.Transparent
            ) {
                Text(
                    text = "←",
                    fontSize = 28.sp,
                    modifier = Modifier.padding(8.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = workout?.name ?: "",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1C1B1F)
                )
                Text(
                    text = "${exerciseList.size} exercises",
                    fontSize = 12.sp,
                    color = Color(0xFF7A7582)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
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

            // Add exercise button + session notes + delete
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Dashed "Add Exercise to Routine" button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .drawBehind {
                                val stroke = 2.dp.toPx()
                                drawRoundRect(
                                    color = Color(0xFF4F378A).copy(alpha = 0.4f),
                                    topLeft = Offset(stroke / 2, stroke / 2),
                                    size = Size(size.width - stroke, size.height - stroke),
                                    cornerRadius = CornerRadius(24.dp.toPx()),
                                    style = Stroke(
                                        width = stroke,
                                        pathEffect = PathEffect.dashPathEffect(
                                            floatArrayOf(12.dp.toPx(), 8.dp.toPx())
                                        )
                                    )
                                )
                            }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        TextButton(
                            onClick = { showAddExerciseDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "+ Add Exercise to Routine",
                                color = Color(0xFF4F378A),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Session notes card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.EditNote,
                                    contentDescription = null,
                                    tint = Color(0xFF4F378A)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Session Notes",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1C1B1F)
                                )
                            }
                            OutlinedTextField(
                                value = notesText,
                                onValueChange = {
                                    notesText = it
                                    viewModel.updateWorkoutNotes(workoutId, it)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp),
                                placeholder = { Text("Add your thoughts, energy levels, or notes for this workout...") },
                                minLines = 3
                            )
                        }
                    }

                    // Delete workout
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        TextButton(
                            onClick = { showDeleteDialog = true }
                        ) {
                            Text(
                                text = "Delete Workout",
                                color = Color(0xFFB3261E),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
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

        if (showAddExerciseDialog) {
            AddExerciseChoiceDialog(
                onStandard = {
                    showAddExerciseDialog = false
                    onAddStandardExercisesClick()
                },
                onCustom = {
                    showAddExerciseDialog = false
                    onAddCustomExercisesClick()
                },
                onDismiss = { showAddExerciseDialog = false }
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
private fun AddExerciseChoiceDialog(
    onStandard: () -> Unit,
    onCustom: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Add Exercise",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Which exercises do you want to choose?",
                    fontSize = 14.sp
                )
                Button(
                    onClick = onStandard,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Standard exercises")
                }
                Button(
                    onClick = onCustom,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("My own exercises")
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun ExerciseSetRow(
    exerciseType: String,
    set: ExerciseProgressEntity,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Light-purple circular badge with set number
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryFixed
            ) {
                Box(
                    modifier = Modifier.size(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = set.setNumber.toString(),
                        color = MaterialTheme.colorScheme.onPrimaryFixed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Set",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            when (exerciseType) {
                "DURATION" -> Text(
                    text = formatDuration(set.durationSeconds ?: 0),
                    textAlign = TextAlign.End,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f)
                )
                "REPS" -> Text(
                    text = "${set.reps ?: 0} reps",
                    textAlign = TextAlign.End,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f)
                )
                else -> {
                    // Weight is centered in the space between "Set" and reps.
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = formatWeightNumber(set.weight ?: 0.0),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "kg",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    // Reps stays clearly visible and right-aligned.
                    Row(
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${set.reps ?: 0}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "reps",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
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
