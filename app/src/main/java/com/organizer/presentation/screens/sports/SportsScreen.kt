package com.organizer.presentation.screens.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.organizer.data.local.db.entities.CategoryEntity
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.presentation.OrganizerViewModel
import com.organizer.presentation.screens.categories.CategoryListItem
import com.organizer.presentation.screens.general.SearchBar
import com.organizer.presentation.screens.workout.ExerciseFilter
import com.organizer.presentation.screens.workout.ExerciseOptionRow
import com.organizer.presentation.screens.workout.ExerciseSearchEmptyMessage
import com.organizer.presentation.screens.workout.ExerciseSearchLimitHint
import com.organizer.presentation.screens.workout.ExerciseSectionHeading
import com.organizer.presentation.screens.workout.MAX_EXERCISE_SEARCH_RESULTS
import com.organizer.presentation.screens.workout.emptyExerciseSearchMessage
import com.organizer.presentation.screens.workout.filterExerciseEntries

@Composable
fun SportsScreen(
    onSportClick: (CategoryEntity) -> Unit,
    onExerciseClick: (ExerciseEntity) -> Unit,
    viewModel: OrganizerViewModel,
) {
    val sportsState by viewModel.sportsUiState.collectAsState()
    val catalogState by viewModel.exerciseCatalogUiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val errorMessage = viewModel.errorMessage
    val isLoading by viewModel.isSyncing.collectAsState()

    val sports = sportsState.orEmpty()
    val isSearching = searchQuery.isNotBlank()

    // Home search matches exactly like the Create Workout search with the
    // Standard scope: exercise names first, then category-name matches.
    val searchResults = remember(catalogState, searchQuery, isSearching) {
        if (isSearching && catalogState != null) {
            filterExerciseEntries(
                entries = catalogState.orEmpty(),
                query = searchQuery,
                filter = ExerciseFilter.Standard,
            )
        } else {
            emptyList()
        }
    }

    // Visible sport icons download first; the rest of the library media keeps
    // downloading in the background.
    LaunchedEffect(sportsState, isSearching) {
        if (!isSearching) {
            viewModel.prioritizeSportsIcons(sports)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFEFBFE))
            .padding(horizontal = 24.dp)
            .padding(top = 16.dp)
    ) {

        HeaderText()

        SearchBar(
            query = searchQuery,
            onQueryChange = {
                viewModel.searchQuery.value = it
            }
        )

        val isLoadingContent = if (isSearching) {
            catalogState == null
        } else {
            sportsState == null
        }

        if (isLoadingContent) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LoadingErrorView(
                    isLoading = true,
                    errorMessage = null,
                    onRetry = {}
                )
            }
        } else if (!isSearching && sports.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LoadingErrorView(
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    onRetry = { viewModel.syncDb() }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isSearching) {
                    item {
                        ExerciseSectionHeading("Search Results")
                    }

                    if (searchResults.isEmpty()) {
                        item {
                            ExerciseSearchEmptyMessage(
                                text = emptyExerciseSearchMessage(
                                    query = searchQuery,
                                    filter = ExerciseFilter.Standard,
                                )
                            )
                        }
                    } else {
                        val visibleResults =
                            searchResults.take(MAX_EXERCISE_SEARCH_RESULTS)

                        items(
                            items = visibleResults,
                            key = { it.exercise.exerciseId },
                        ) { entry ->
                            ExerciseOptionRow(
                                entry = entry,
                                onClick = { onExerciseClick(entry.exercise) },
                                viewModel = viewModel,
                                selectable = false,
                            )
                        }

                        if (searchResults.size > MAX_EXERCISE_SEARCH_RESULTS) {
                            item {
                                ExerciseSearchLimitHint()
                            }
                        }
                    }
                } else {
                    items(
                        items = sports,
                        key = { it.categoryId },
                    ) { sport ->
                        CategoryListItem(
                            category = sport,
                            onClick = { onSportClick(sport) },
                            viewModel = viewModel,
                            subtitle = sportSubtitle(sport.name)
                        )
                    }
                }
            }
        }
    }
}

private fun sportSubtitle(name: String): String? = when (name) {
    "Gym" -> "Weights & Machines"
    "Calisthenics" -> "Bodyweight Strength & Skills"
    "Street Workout" -> "Bars, Tricks & Freestyle"
    "Home Workout" -> "Home Equipment & Bodyweight"
    else -> null
}
