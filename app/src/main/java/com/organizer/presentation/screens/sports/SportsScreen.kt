package com.organizer.presentation.screens.sports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.organizer.data.local.db.entities.CategoryEntity
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.presentation.OrganizerViewModel
import com.organizer.presentation.model.SearchResult
import com.organizer.presentation.screens.categories.CategoryListItem
import com.organizer.presentation.screens.exercises.ExerciseListItem
import com.organizer.presentation.screens.general.SearchBar

@Composable
fun SportsScreen(
    onSportClick: (CategoryEntity) -> Unit,
    onCategoryClick: (CategoryEntity) -> Unit,
    onExerciseClick: (ExerciseEntity) -> Unit,
    viewModel: OrganizerViewModel,
) {
    val searchResults by viewModel.searchResultsUiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val errorMessage = viewModel.errorMessage
    val isLoading by viewModel.isSyncing.collectAsState()

    // Visible sport icons download first; the rest of the library media keeps
    // downloading in the background.
    LaunchedEffect(searchResults, searchQuery) {
        if (searchQuery.isBlank()) {
            viewModel.prioritizeSportsIcons(
                searchResults.filterIsInstance<SearchResult.Sport>().map { it.sport }
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
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

        if (searchResults.isEmpty() && searchQuery.isBlank()) {
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
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                items(
                    items = searchResults,
                ) { result ->
                    when (result) {
                        is SearchResult.Sport -> {
                            CategoryListItem(
                                category = result.sport,
                                onClick = { onSportClick(result.sport) },
                                viewModel = viewModel
                            )
                        }
                        is SearchResult.Category -> {
                            CategoryListItem(
                                category = result.category,
                                onClick = { onCategoryClick(result.category) },
                                viewModel = viewModel
                            )
                        }
                        is SearchResult.Exercise -> {
                            ExerciseListItem(
                                exercise = result.exercise,
                                deletionInfo = null,
                                expanded = false,
                                onClick = { onExerciseClick(result.exercise) },
                                onOpenClick = {},
                                onDeleteClick = {},
                                viewModel = viewModel
                            )
                        }
                    }
                }
            }
        }
    }
}
