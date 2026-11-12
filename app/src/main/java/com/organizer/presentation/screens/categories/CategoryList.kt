package com.organizer.presentation.screens.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.organizer.data.local.db.entities.CategoryEntity
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.presentation.OrganizerViewModel
import com.organizer.presentation.screens.exercises.ExerciseListItem

@Composable
fun CategoryList(
    categoryId: Long,
    onCategoryClick: (CategoryEntity) -> Unit,
    onExerciseClick: (ExerciseEntity) -> Unit,
    viewModel: OrganizerViewModel
) {
    val categories by viewModel.observeSubcategories(categoryId).collectAsState(initial = emptyList())
    val exercises by viewModel.observeExercisesByCategory(categoryId).collectAsState(initial = emptyList())

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 20.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {

        items(
            items = categories,
            key = { it.categoryId }
        ) { category ->
            CategoryListItem(
                category = category,
                onClick = {
                    onCategoryClick(category)
                },
                viewModel = viewModel
            )
        }

        items(
            items = exercises,
            key = { it.exerciseId }
        ) { exercise ->
            ExerciseListItem(
                exercise = exercise,
                deletionInfo = null,
                expanded = false,
                onClick = { onExerciseClick(exercise) },
                onOpenClick = {},
                onDeleteClick = {},
                viewModel = viewModel
            )
        }
    }
}
