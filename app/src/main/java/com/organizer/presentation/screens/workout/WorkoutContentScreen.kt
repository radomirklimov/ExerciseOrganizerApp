package com.organizer.presentation.screens.workout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    onCreateCustomExerciseClick: () -> Unit,
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
                            .height(52.dp)
                            .clip(RoundedCornerShape(50))
                            .drawBehind {
                                val stroke = 2.dp.toPx()
                                drawRoundRect(
                                    color = Color(0xFFCBC4D2).copy(alpha = 0.8f),
                                    topLeft = Offset(stroke / 2, stroke / 2),
                                    size = Size(size.width - stroke, size.height - stroke),
                                    cornerRadius = CornerRadius(50.dp.toPx()),
                                    style = Stroke(
                                        width = stroke,
                                        pathEffect = PathEffect.dashPathEffect(
                                            floatArrayOf(12.dp.toPx(), 8.dp.toPx())
                                        )
                                    )
                                )
                            }
                            .background(Color.White.copy(alpha = 0.5f))
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        TextButton(
                            onClick = { showAddExerciseDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                tint = Color(0xFF4F378A),
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "+ Add Exercise to Routine",
                                color = Color(0xFF4F378A),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
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
                onCreateCustomExercise = {
                    showAddExerciseDialog = false
                    onCreateCustomExerciseClick()
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
    onCreateCustomExercise: () -> Unit,
    onDismiss: () -> Unit,
) {
    val primary = Color(0xFF4F378A)
    val secondary = Color(0xFF684EA7)
    val primaryContainer = Color(0xFF6750A4)
    val onSurface = Color(0xFF1C1B1F)
    val onSurfaceVariant = Color(0xFF494551)
    val surfaceContainerLow = Color(0xFFF7F2F8)
    val surfaceContainer = Color(0xFFF1ECF2)
    val outlineVariant = Color(0xFFCBC4D2)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color.White,
            shadowElevation = 12.dp,
            border = BorderStroke(1.dp, outlineVariant.copy(alpha = 0.4f)),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Add Exercise",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = onSurface,
                            lineHeight = 28.sp,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Choose how you want to add exercises to this routine",
                            fontSize = 12.sp,
                            color = onSurfaceVariant,
                            lineHeight = 16.sp,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close dialog",
                            tint = onSurfaceVariant,
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AddExerciseOptionCard(
                        icon = Icons.Filled.FitnessCenter,
                        iconContainerColor = primaryContainer,
                        title = "Standard exercises",
                        subtitle = "Browse pre-built exercises",
                        onClick = onStandard,
                    )
                    AddExerciseOptionCard(
                        icon = Icons.Filled.Person,
                        iconContainerColor = secondary,
                        title = "My own exercises",
                        subtitle = "Select from custom exercises",
                        onClick = onCustom,
                    )

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        TextButton(
                            onClick = onCreateCustomExercise,
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.textButtonColors(contentColor = primary),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AddCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Create brand new exercise",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                Column {
                    HorizontalDivider(
                        color = outlineVariant.copy(alpha = 0.4f),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = surfaceContainer,
                            contentColor = onSurface,
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                        contentPadding = PaddingValues(vertical = 14.dp),
                    ) {
                        Text(
                            text = "Cancel",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

}

@Composable
private fun AddExerciseOptionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconContainerColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val onSurface = Color(0xFF1C1B1F)
    val onSurfaceVariant = Color(0xFF494551)
    val outlineVariant = Color(0xFFCBC4D2)
    val surfaceContainer = Color(0xFFF1ECF2)

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF7F2F8),
        border = BorderStroke(1.dp, outlineVariant.copy(alpha = 0.6f)),
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = iconContainerColor,
                shadowElevation = 1.dp,
            ) {
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = onSurface,
                    lineHeight = 20.sp,
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp,
                )
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = surfaceContainer,
            ) {
                Box(
                    modifier = Modifier.size(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
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
                    text = styledDuration(formatDuration(set.durationSeconds ?: 0), MaterialTheme.colorScheme.onSurfaceVariant),
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
                "REPS" -> Row(
                    modifier = Modifier.weight(1f),
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


private fun styledDuration(value: String, unitColor: androidx.compose.ui.graphics.Color): AnnotatedString {
    return buildAnnotatedString {
        value.forEach { ch ->
            if (ch.isDigit()) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp)) {
                    append(ch)
                }
            } else {
                withStyle(
                    SpanStyle(
                        fontWeight = FontWeight.Normal,
                        fontSize = 12.sp,
                        color = unitColor
                    )
                ) {
                    append(ch)
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
