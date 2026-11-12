package com.organizer.presentation.screens.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.organizer.presentation.OrganizerViewModel
import com.organizer.presentation.screens.workout.WorkoutMarkableListItem

@Composable
fun SaveExercisePopup(
    exerciseId: Long,
    onDismiss: () -> Unit,
    onCreateWorkoutClick: () -> Unit,
    viewModel: OrganizerViewModel,
) {
    val workouts by viewModel.workoutsUiState.collectAsState()
    var selectedWorkoutIds by remember { mutableStateOf(setOf<Long>()) }

    Dialog(
        onDismissRequest = onDismiss
    ) {
        Card(
            shape = RoundedCornerShape(24.dp)
        ) {

            Column(
                modifier = Modifier
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = onDismiss
                    ) {
                        Text("✕")
                    }
                }

                Text(
                    text = "Save Exercise",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Select one or more workouts.",
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (workouts.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "You don't have any workouts yet.",
                            modifier = Modifier.padding(24.dp),
                            fontSize = 16.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        contentPadding = PaddingValues(top = 20.dp, bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = workouts,
                            key = { it.workoutId }
                        ) { workout ->
                            WorkoutMarkableListItem(
                                title = workout.name,
                                selected = workout.workoutId in selectedWorkoutIds,
                                onSelectedChange = { isSelected ->
                                    selectedWorkoutIds =
                                        if (isSelected) {
                                            selectedWorkoutIds + workout.workoutId
                                        } else {
                                            selectedWorkoutIds - workout.workoutId
                                        }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        selectedWorkoutIds.forEach { workoutId ->
                            viewModel.addExerciseToWorkout(
                                workoutId = workoutId,
                                exerciseId = exerciseId
                            )
                        }

                        onDismiss()
                    },
                    enabled = selectedWorkoutIds.isNotEmpty()
                ) {
                    Text(
                        text = "Save",
                        fontSize = 18.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                HorizontalDivider()

                Spacer(modifier = Modifier.height(20.dp))

                TextButton(
                    onClick = {
                        onDismiss()
                        onCreateWorkoutClick()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Create New Workout")
                }
            }
        }
    }
}
