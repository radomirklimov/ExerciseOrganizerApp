package com.organizer.presentation.screens.sports

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.organizer.R
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
import com.organizer.ui.theme.AppTheme

@Composable
fun SportsScreen(
    onSportClick: (CategoryEntity) -> Unit,
    onExerciseClick: (ExerciseEntity) -> Unit,
    onProfileClick: () -> Unit,
    avatarPath: String?,
    initials: String,
    viewModel: OrganizerViewModel,
) {
    val sportsState by viewModel.sportsUiState.collectAsState()
    val catalogState by viewModel.exerciseCatalogUiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val errorMessage = viewModel.errorMessage
    val isLoading by viewModel.isSyncing.collectAsState()

    val sports = sportsState.orEmpty()
    val isSearching = searchQuery.isNotBlank()
    val profileLabel = stringResource(R.string.profile_title)

    // Keep the last query while its results fade into the sports grid. Without
    // it, clearing the field would briefly render an empty result list and the
    // home screen would flash instead of transitioning.
    var fadingQuery by remember { mutableStateOf("") }
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) fadingQuery = searchQuery
    }
    val displayQuery = if (isSearching) searchQuery else fadingQuery

    // Home search matches exactly like the Create Workout search with the
    // Standard scope: exercise names first, then category-name matches.
    val searchResults = remember(catalogState, displayQuery) {
        if (catalogState != null) {
            filterExerciseEntries(
                entries = catalogState.orEmpty(),
                query = displayQuery,
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
            .background(AppTheme.colors.background)
            .padding(horizontal = 24.dp)
            .padding(top = 16.dp)
    ) {

        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                HeaderText()
            }
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(AppTheme.colors.surfaceMuted)
                    .clickable(onClick = onProfileClick)
                    .semantics { contentDescription = profileLabel },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = profileLabel,
                    tint = AppTheme.colors.textSecondary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        SearchBar(
            query = searchQuery,
            onQueryChange = {
                viewModel.searchQuery.value = it
            }
        )

        // The home body crossfades between the sports grid and search
        // results, matching the 200 ms fade the app uses for navigation.
        Crossfade(
            targetState = isSearching,
            animationSpec = tween(durationMillis = 200),
            modifier = Modifier.fillMaxSize(),
            label = "homeContent",
        ) { searching ->
            val isLoadingContent = if (searching) {
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
            } else if (!searching && sports.isEmpty()) {
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
                    if (searching) {
                        item {
                            ExerciseSectionHeading("Search Results")
                        }

                        if (searchResults.isEmpty()) {
                            item {
                                ExerciseSearchEmptyMessage(
                                    text = emptyExerciseSearchMessage(
                                        query = displayQuery,
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
}

private fun sportSubtitle(name: String): String? = when (name) {
    "Gym" -> "Weights & Machines"
    "Calisthenics" -> "Bodyweight Strength & Skills"
    "Street Workout" -> "Bars, Tricks & Freestyle"
    "Home Workout" -> "Home Equipment & Bodyweight"
    else -> null
}
