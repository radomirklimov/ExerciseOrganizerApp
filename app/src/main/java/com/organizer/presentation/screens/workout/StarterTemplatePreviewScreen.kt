package com.organizer.presentation.screens.workout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.presentation.OrganizerViewModel

/**
 * Opens a starter template in the regular workout screen. The screen is shown
 * read-only until the user taps "Add to My Workouts"; [onWorkoutCreated] then
 * receives the freshly copied local workout id.
 */
@Composable
fun StarterTemplatePreviewScreen(
    templateId: Long,
    onOpenExerciseClick: (ExerciseEntity) -> Unit,
    onBackClick: () -> Unit,
    onWorkoutCreated: (Long) -> Unit,
    viewModel: OrganizerViewModel,
) {
    val template by viewModel.observeStarterTemplate(templateId)
        .collectAsState(initial = null)
    val exercises by viewModel.observeStarterTemplateExercises(templateId)
        .collectAsState(initial = emptyList())

    WorkoutContentScreen(
        workoutId = 0L,
        onOpenExerciseClick = onOpenExerciseClick,
        onBackClick = onBackClick,
        onAddStandardExercisesClick = {},
        onAddCustomExercisesClick = {},
        onCreateCustomExerciseClick = {},
        viewModel = viewModel,
        template = template,
        templateExercises = exercises,
        isTemplatePreview = true,
        onAddToMyWorkouts = {
            viewModel.addStarterTemplateToWorkouts(templateId, onWorkoutCreated)
        },
    )
}
