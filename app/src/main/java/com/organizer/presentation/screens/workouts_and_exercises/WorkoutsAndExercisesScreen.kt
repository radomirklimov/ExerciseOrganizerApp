package com.organizer.presentation.screens.workouts_and_exercises

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.local.db.entities.WorkoutEntity
import com.organizer.presentation.OrganizerViewModel

@Composable
fun CustomWorkoutsAndExercisesScreen(
    onWorkoutClick: (WorkoutEntity) -> Unit,
    onExerciseClick: (ExerciseEntity) -> Unit,
    onCreateWorkoutClick: () -> Unit,
    onCreateExerciseClick: () -> Unit,
    onTemplateClick: (Long) -> Unit,
    onViewAllTemplatesClick: () -> Unit,
    viewModel: OrganizerViewModel = hiltViewModel(),
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {

        Spacer(Modifier.height(32.dp))

        TabSwitcher()

        val selectedTab by viewModel.selectedTab.collectAsState()
        CustomsList(
            tab = selectedTab,
            onWorkoutClick = onWorkoutClick,
            onOpenExerciseClick = onExerciseClick,
            onCreateWorkoutClick = onCreateWorkoutClick,
            onCreateExerciseClick = onCreateExerciseClick,
            onTemplateClick = onTemplateClick,
            onViewAllTemplatesClick = onViewAllTemplatesClick,
            viewModel = viewModel,
        )
    }
}

@Composable
fun WorkoutsScreen(
    onWorkoutClick: (WorkoutEntity) -> Unit,
    onCreateWorkoutClick: () -> Unit,
    onTemplateClick: (Long) -> Unit,
    onViewAllTemplatesClick: () -> Unit,
    viewModel: OrganizerViewModel = hiltViewModel(),
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Workouts",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1C1B1F),
            )
        }

//        Spacer(Modifier.height(8.dp))

        CustomsList(
            tab = CustomsTab.WORKOUTS,
            onWorkoutClick = onWorkoutClick,
            onOpenExerciseClick = {},
            onCreateWorkoutClick = onCreateWorkoutClick,
            onCreateExerciseClick = {},
            onTemplateClick = onTemplateClick,
            onViewAllTemplatesClick = onViewAllTemplatesClick,
            viewModel = viewModel,
        )
    }
}

@Composable
fun ExercisesScreen(
    onExerciseClick: (ExerciseEntity) -> Unit,
    onCreateExerciseClick: () -> Unit,
    viewModel: OrganizerViewModel = hiltViewModel(),
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(32.dp))

        CustomsList(
            tab = CustomsTab.EXERCISES,
            onWorkoutClick = {},
            onOpenExerciseClick = onExerciseClick,
            onCreateWorkoutClick = {},
            onCreateExerciseClick = onCreateExerciseClick,
            onTemplateClick = {},
            onViewAllTemplatesClick = {},
            viewModel = viewModel,
        )
    }
}
