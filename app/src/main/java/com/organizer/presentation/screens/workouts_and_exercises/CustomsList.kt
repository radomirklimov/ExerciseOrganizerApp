package com.organizer.presentation.screens.workouts_and_exercises

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.local.db.entities.WorkoutEntity
import com.organizer.presentation.OrganizerViewModel
import com.organizer.presentation.screens.exercises.ExerciseListItem
import com.organizer.presentation.screens.general.DeletionInfo
import com.organizer.presentation.screens.general.LoadingState
import com.organizer.presentation.screens.workout.WorkoutListItem
import com.organizer.ui.theme.darkerShade

// Same edge on every floating bottom action button, coloured with the screen
// background so it reads as a clean cut-out on all screens.
private val ButtonBorderWidth = 2.dp

@Composable
fun CustomsList(
    tab: CustomsTab,
    onWorkoutClick: (WorkoutEntity) -> Unit,
    onOpenExerciseClick: (ExerciseEntity) -> Unit,
    onCreateWorkoutClick: () -> Unit,
    onCreateExerciseClick: () -> Unit,
    onBrowseStandardExercisesClick: () -> Unit = {},
    onTemplateClick: (Long) -> Unit,
    onViewAllTemplatesClick: () -> Unit,
    viewModel: OrganizerViewModel = hiltViewModel(),
) {
    val workoutsState by viewModel.workoutsUiState.collectAsState()
    val workoutCategories by viewModel.workoutCategoryNamesUiState.collectAsState()
    val starterTemplatesState by viewModel.starterTemplatesUiState.collectAsState()
    val customExercisesState by viewModel.customExercisesUiState.collectAsState()
    var expandedExerciseId by remember { mutableStateOf<Long?>(null) }

    val workouts = workoutsState.orEmpty()
    val starterTemplates = starterTemplatesState.orEmpty()
    val customExercises = customExercisesState.orEmpty()

    // Between opening this screen and the first database emission the lists
    // are empty. Show a neutral loader instead of the "nothing here yet"
    // state, which would otherwise flash before the stored data appears.
    val isLoading = when (tab) {
        CustomsTab.WORKOUTS ->
            workoutsState == null ||
                (workouts.isEmpty() && starterTemplatesState == null)
        CustomsTab.EXERCISES -> customExercisesState == null
    }

    val isEmpty = when (tab) {
        CustomsTab.WORKOUTS -> workouts.isEmpty()
        CustomsTab.EXERCISES -> customExercises.isEmpty()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        if (isLoading) {
            LoadingState()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = if (isEmpty) 40.dp else 20.dp,
                    // Leaves room for the floating button so the last row can
                    // scroll clear of it. While scrolling, the list content
                    // passes underneath the button.
                    bottom = if (isEmpty) 20.dp else 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                when (tab) {
                    CustomsTab.WORKOUTS -> {
                        if (workouts.isEmpty()) {
                            item {
                                EmptyWorkoutsState(
                                    templates = starterTemplates,
                                    onCreateWorkoutClick = onCreateWorkoutClick,
                                    onTemplateClick = onTemplateClick,
                                    onViewAllTemplatesClick = onViewAllTemplatesClick,
                                    viewModel = viewModel,
                                )
                            }
                        } else {
                            if (starterTemplates.isNotEmpty()) {
                                item {
                                    StarterTemplatesSection(
                                        templates = starterTemplates,
                                        onTemplateClick = onTemplateClick,
                                        onViewAllClick = onViewAllTemplatesClick,
                                        viewModel = viewModel,
                                    )
                                }
                            }
                            items(
                                items = workouts,
                                key = { it.workoutId }
                            ) { workout ->
                                WorkoutListItem(
                                    title = workout.name,
                                    categories = workoutCategories
                                        .orEmpty()[workout.workoutId]
                                        .orEmpty(),
                                    onClick = { onWorkoutClick(workout) }
                                )
                            }
                        }
                    }

                    CustomsTab.EXERCISES -> {
                        if (customExercises.isEmpty()) {
                            item {
                                EmptyExercisesState(
                                    onCreateExerciseClick = onCreateExerciseClick,
                                    onBrowseStandardExercisesClick = onBrowseStandardExercisesClick,
                                )
                            }
                        } else {
                            items(
                                items = customExercises,
                                key = { it.exerciseId }
                            ) { exercise ->
                                ExerciseListItem(
                                    exercise = exercise,
                                    deletionInfo = DeletionInfo(
                                        title = "Delete Exercise?",
                                        location = "your custom exercises",
                                        isPermanent = true,
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
                                        viewModel.deleteCustomExercise(exercise.exerciseId)
                                    },
                                    expandedButtonsSpacing = 8.dp,
                                    viewModel = viewModel
                                )
                            }
                        }
                    }
                }
            }
        }

        // Floats above the list so the rows stay visible around the button
        // instead of it sitting on its own strip of background.
        when (tab) {
            CustomsTab.WORKOUTS -> {
                if (!isEmpty) {
                    CreateButton(
                        text = "Create New Workout",
                        onClick = onCreateWorkoutClick,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp),
                        border = BorderStroke(ButtonBorderWidth, darkerShade(MaterialTheme.colorScheme.primary)),
                    )
                }
            }

            CustomsTab.EXERCISES -> {
                if (!isEmpty) {
                    CreateButton(
                        text = "Create New Exercise",
                        onClick = onCreateExerciseClick,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp),
                        border = BorderStroke(ButtonBorderWidth, darkerShade(MaterialTheme.colorScheme.primary)),
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    border: BorderStroke? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(25.dp),
        colors = colors,
        border = border,
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
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
