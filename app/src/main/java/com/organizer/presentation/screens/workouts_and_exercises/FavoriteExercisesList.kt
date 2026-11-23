package com.organizer.presentation.screens.workouts_and_exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.presentation.OrganizerViewModel
import com.organizer.presentation.screens.exercises.ExerciseListItem
import com.organizer.presentation.screens.general.LoadingState
import com.organizer.R

/**
 * "Favorites" tab of the Exercises page: every exercise the user liked,
 * standard and custom, most recently liked first.
 */
@Composable
fun FavoriteExercisesList(
    onExerciseClick: (ExerciseEntity) -> Unit,
    onBrowseStandardExercisesClick: () -> Unit,
    viewModel: OrganizerViewModel = hiltViewModel(),
) {
    val favoritesState by viewModel.favoriteExercisesUiState.collectAsState()
    val favorites = favoritesState.orEmpty()

    if (favoritesState == null) {
        LoadingState()
    } else if (favorites.isEmpty()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .fillMaxWidth(),
            contentPadding = PaddingValues(top = 40.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                EmptyExercisesState(
                    title = stringResource(R.string.empty_favorites_title),
                    description = stringResource(R.string.empty_favorites_desc),
                    showCreateButton = false,
                    icon = Icons.Filled.Favorite,
                    capabilityPills = emptyList(),
                    onBrowseStandardExercisesClick = onBrowseStandardExercisesClick,
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .fillMaxWidth(),
            contentPadding = PaddingValues(top = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(
                items = favorites,
                key = { it.exerciseId },
            ) { exercise ->
                ExerciseListItem(
                    exercise = exercise,
                    deletionInfo = null,
                    expanded = false,
                    onClick = { onExerciseClick(exercise) },
                    onOpenClick = {},
                    onDeleteClick = {},
                    showDelete = false,
                    viewModel = viewModel,
                )
            }
        }
    }
}
