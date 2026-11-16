package com.organizer.presentation.screens.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.organizer.presentation.OrganizerViewModel

@Composable
fun CreateWorkoutScreen(
    onBackClick: () -> Unit = {},
    pendingExerciseId: Long? = null,
    viewModel: OrganizerViewModel,
) {
    var workoutName by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var showDuplicateNameError by remember { mutableStateOf(false) }
    var selectedExerciseIds by remember(pendingExerciseId) {
        mutableStateOf(listOfNotNull(pendingExerciseId))
    }

    val workouts by viewModel.workoutsUiState.collectAsState()
    val catalog by viewModel.exerciseCatalogUiState.collectAsState()
    val suggestedExercises by viewModel.suggestedExercisesUiState.collectAsState()

    val catalogById = remember(catalog) {
        catalog.associateBy { it.exercise.exerciseId }
    }
    val suggestedEntries = remember(suggestedExercises, catalogById) {
        suggestedExercises.mapNotNull { catalogById[it.exerciseId] }
    }

    val trimmedName = workoutName.trim()
    val canSave = trimmedName.isNotBlank() &&
            !showDuplicateNameError
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 24.dp)
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back"
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Create New Workout",
                fontSize = 26.sp
            )

            Spacer(modifier = Modifier.weight(1f))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Workout Name
            OutlinedTextField(
                value = workoutName,
                onValueChange = {
                    workoutName = it
                    showDuplicateNameError = false
                },
                isError = showDuplicateNameError,
                placeholder = {
                    Text("Name Of Workout")
                },
                // Only reserve the supporting-text area while an error is shown,
                // otherwise the field keeps ~20dp of empty bottom padding.
                supportingText = if (showDuplicateNameError) {
                    {
                        Text("A workout with this name already exists. Please choose a different name.")
                    }
                } else {
                    null
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                placeholder = {
                    Text("Add your thoughts, energy levels, or notes for this workout...")
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp),
                minLines = 3
            )

            Spacer(modifier = Modifier.height(28.dp))

            ExercisePickerSection(
                entries = catalog,
                suggestedEntries = suggestedEntries,
                selectedExerciseIds = selectedExerciseIds,
                onToggleExercise = { exerciseId ->
                    selectedExerciseIds = if (exerciseId in selectedExerciseIds) {
                        selectedExerciseIds - exerciseId
                    } else {
                        selectedExerciseIds + exerciseId
                    }
                },
                viewModel = viewModel,
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        CreateWorkoutFooter(
            enabled = canSave,
            exerciseCount = selectedExerciseIds.size,
            onCreate = {
                if (workouts.any {
                        it.name.equals(trimmedName, ignoreCase = true)
                    }
                ) {
                    showDuplicateNameError = true
                } else {
                    viewModel.createWorkout(
                        name = trimmedName,
                        notes = notes.trim(),
                        exerciseIds = selectedExerciseIds,
                    ) {
                        onBackClick()
                    }
                }
            }
        )
    }
}

@Composable
private fun CreateWorkoutFooter(
    enabled: Boolean,
    exerciseCount: Int,
    onCreate: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White),
    ) {
        HorizontalDivider(color = DividerColor)

        Button(
            onClick = onCreate,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 14.dp, bottom = 18.dp)
                .height(58.dp)
                .shadow(
                    elevation = if (enabled) 14.dp else 0.dp,
                    shape = RoundedCornerShape(20.dp),
                    ambientColor = Brand.copy(alpha = 0.45f),
                    spotColor = Brand.copy(alpha = 0.45f),
                ),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Brand,
                contentColor = Color.White,
                disabledContainerColor = DisabledBackground,
                disabledContentColor = DisabledText,
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                disabledElevation = 0.dp,
            ),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            Text(
                text = createWorkoutLabel(exerciseCount),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.2.sp,
            )
        }
    }
}

private fun createWorkoutLabel(exerciseCount: Int): String = when (exerciseCount) {
    0 -> "Create Workout"
    1 -> "Create Workout (1 exercise)"
    else -> "Create Workout ($exerciseCount exercises)"
}
