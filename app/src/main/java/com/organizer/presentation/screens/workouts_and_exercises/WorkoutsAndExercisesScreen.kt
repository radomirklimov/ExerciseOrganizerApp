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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.local.db.entities.WorkoutEntity
import com.organizer.presentation.OrganizerViewModel
import com.organizer.ui.theme.AppTheme
import com.organizer.R

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
    var selectedTab by rememberSaveable { mutableStateOf(ExercisesTab.FAVORITES) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(12.dp))

        Spacer(Modifier.height(8.dp))

        // Segmented tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(AppTheme.colors.surfaceMuted)
                .padding(4.dp),
        ) {
            listOf(ExercisesTab.FAVORITES to stringResource(R.string.exercises_tab_favorites), ExercisesTab.CUSTOM to stringResource(R.string.exercises_tab_custom)).forEach { (tab, label) ->
                val selected = selectedTab == tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) AppTheme.colors.surface else Color.Transparent)
                        .clickable { selectedTab = tab }
                        .padding(vertical = 6.dp, horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selected) AppTheme.colors.primaryDark else AppTheme.colors.textSecondary,
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
                FavoriteExercisesList(
                    onExerciseClick = onExerciseClick,
                    onBrowseStandardExercisesClick = onBrowseStandardExercisesClick,
                    viewModel = viewModel,
                )
            }
        }
    }
}
