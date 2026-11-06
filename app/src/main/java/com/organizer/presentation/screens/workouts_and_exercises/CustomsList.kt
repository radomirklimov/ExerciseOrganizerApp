package com.organizer.presentation.screens.workouts_and_exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.local.db.entities.WorkoutEntity
import com.organizer.presentation.OrganizerViewModel
import com.organizer.presentation.screens.exercises.ExerciseListItem
import com.organizer.presentation.screens.workout.WorkoutListItem

@Composable
fun CustomsList(
    tab: CustomsTab,
    onWorkoutClick: (WorkoutEntity) -> Unit,
    onOpenExerciseClick: (ExerciseEntity) -> Unit,
    onCreateWorkoutClick: () -> Unit,
    onCreateExerciseClick: () -> Unit,
    viewModel: OrganizerViewModel = hiltViewModel(),
) {
    val workouts by viewModel.workoutsUiState.collectAsState()
    val customExercises by viewModel.customExercisesUiState.collectAsState()
    var expandedExerciseId by remember { mutableStateOf<Long?>(null) }

    val isEmpty = when (tab) {
        CustomsTab.WORKOUTS -> workouts.isEmpty()
        CustomsTab.EXERCISES -> customExercises.isEmpty()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(top = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            when (tab) {
                CustomsTab.WORKOUTS -> {
                    if (workouts.isEmpty()) {
                        item {
                            EmptyStateWithButton(
                                message = "No workouts created yet",
                                buttonText = "Create New Workout",
                                onButtonClick = onCreateWorkoutClick
                            )
                        }
                    } else {
                        items(
                            items = workouts,
                            key = { it.workoutId }
                        ) { workout ->
                            WorkoutListItem(
                                title = workout.name,
                                onClick = { onWorkoutClick(workout) }
                            )
                        }
                    }
                }

                CustomsTab.EXERCISES -> {
                    if (customExercises.isEmpty()) {
                        item {
                            EmptyStateWithButton(
                                message = "No exercises created yet",
                                buttonText = "Create New Exercise",
                                onButtonClick = onCreateExerciseClick
                            )
                        }
                    } else {
                        items(
                            items = customExercises,
                            key = { it.exerciseId }
                        ) { exercise ->
                            ExerciseListItem(
                                exercise = exercise,
                                deletionInfo = Pair("Delete Exercise?", "This exercise will be permanently deleted."),
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
                                    viewModel.deleteCustomExercise(exercise.exerciseId)
                                },
                                viewModel = viewModel
                            )
                        }
                    }
                }
            }
        }

        if (!isEmpty) {
            when (tab) {
                CustomsTab.WORKOUTS -> {
                    CreateButton(
                        text = "Create New Workout",
                        onClick = onCreateWorkoutClick
                    )
                }

                CustomsTab.EXERCISES -> {
                    CreateButton(
                        text = "Create New Exercise",
                        onClick = onCreateExerciseClick
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyStateWithButton(
    message: String,
    buttonText: String,
    onButtonClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        EmptyState(message)

        Spacer(modifier = Modifier.height(16.dp))

        CreateButton(
            text = buttonText,
            onClick = onButtonClick
        )
    }
}

@Composable
private fun CreateButton(
    text: String,
    onClick: () -> Unit
) {
    Button(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        onClick = onClick,
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(text)
    }
}

@Composable
private fun EmptyState(
    message: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
