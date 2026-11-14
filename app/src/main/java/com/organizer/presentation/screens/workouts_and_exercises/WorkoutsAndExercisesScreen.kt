package com.organizer.presentation.screens.workouts_and_exercises

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
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
    onBrowseStandardExercisesClick: () -> Unit = {},
    viewModel: OrganizerViewModel = hiltViewModel(),
) {
    var selectedTab by remember { mutableStateOf(ExercisesTab.CUSTOM) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Filled.FitnessCenter,
                    contentDescription = null,
                    tint = Color(0xFF1C1B1F),
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Exercises",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1C1B1F),
            )
        }

        Spacer(Modifier.height(8.dp))

        // Segmented tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(Color(0xFFF1ECF2))
                .padding(4.dp),
        ) {
            listOf(ExercisesTab.CUSTOM to "Custom", ExercisesTab.FAVORITES to "Favorites").forEach { (tab, label) ->
                val selected = selectedTab == tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) Color.White else Color.Transparent)
                        .clickable { selectedTab = tab }
                        .padding(vertical = 6.dp, horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selected) Color(0xFF4F378A) else Color(0xFF494551),
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            if (selectedTab == ExercisesTab.CUSTOM) {
                CustomsList(
                    tab = CustomsTab.EXERCISES,
                    onWorkoutClick = {},
                    onOpenExerciseClick = onExerciseClick,
                    onCreateWorkoutClick = {},
                    onCreateExerciseClick = onCreateExerciseClick,
                    onBrowseStandardExercisesClick = onBrowseStandardExercisesClick,
                    onTemplateClick = {},
                    onViewAllTemplatesClick = {},
                    viewModel = viewModel,
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyExercisesState(
                        title = "No Favorite Exercises Yet",
                        description = "Mark exercises as favorite to quickly access them here.",
                        showCreateButton = false,
                        onBrowseStandardExercisesClick = onBrowseStandardExercisesClick,
                    )
                }
            }
        }
    }
}
