package com.organizer.presentation.screens.workout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.organizer.presentation.OrganizerViewModel
import com.organizer.presentation.screens.general.SaveButton

@Composable
fun CreateWorkoutScreen(
    onBackClick: () -> Unit = {},
    viewModel: OrganizerViewModel,
) {
    var workoutName by remember { mutableStateOf("") }
    var showDuplicateNameError by remember { mutableStateOf(false) }
    val workouts by viewModel.workoutsUiState.collectAsState()
    val trimmedName = workoutName.trim()
    val canSave = trimmedName.isNotBlank() &&
            !showDuplicateNameError

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {

        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back"
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Create Your Workout",
                fontSize = 26.sp
            )

            Spacer(modifier = Modifier.weight(1f))
        }

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
            supportingText = {
                if (showDuplicateNameError) {
                    Text("A workout with this name already exists. Please choose a different name.")
                }
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.weight(1f))

        SaveButton(
            enabled = canSave,
            onClick = {
                if (workouts.any {
                        it.name.equals(trimmedName, ignoreCase = true)
                    }
                ) {
                    showDuplicateNameError = true
                    return@SaveButton
                }

                viewModel.createWorkout(trimmedName)
                onBackClick()
            }
        )
    }
}
