package com.organizer.presentation.screens.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.organizer.data.local.db.entities.CategoryEntity
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.presentation.OrganizerViewModel
import com.organizer.presentation.screens.sports.LoadingErrorView

@Composable
fun CategoryContentScreen(
    categoryId: Long,
    onCategoryClick: (CategoryEntity) -> Unit,
    onExerciseClick: (ExerciseEntity) -> Unit,
    onBackClick: () -> Unit,
    onAddCustomExerciseClick: () -> Unit,
    viewModel: OrganizerViewModel,
) {
    val categoryPath by viewModel.getCategoryPath(categoryId).collectAsState(initial = emptyList())
    val subcategories by viewModel.observeSubcategories(categoryId).collectAsState(initial = emptyList())
    val exercises by viewModel.observeExercisesByCategory(categoryId).collectAsState(initial = emptyList())
    val isLoading by viewModel.isSyncing.collectAsState()
    val errorMessage = viewModel.errorMessage

    // Keep the same background color on every screen.
    val backgroundColor = Color(0xFFFEFBFE)

    // Prioritize this category's media every time its data changes: opening
    // another category dynamically raises its images and icons above pending
    // background downloads. Data itself renders from Room immediately.
    LaunchedEffect(categoryId, subcategories, exercises, categoryPath) {
        viewModel.prioritizeCategoryMedia(subcategories, exercises, categoryPath)
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Keep the header in the default surface color, independent of the
        // category-depth background tint used by the content below.
        Surface(
            color = Color(0xFFFEFBFE),
            modifier = Modifier.fillMaxWidth()
        ) {
            CategoryHeader(
                categoryPath.toMutableList(),
                onBackClick,
                onCategoryClick,
                viewModel
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp)
        ) {

            Spacer(modifier = Modifier.height(16.dp))

            // An empty list while syncing (or failing) means "not loaded yet",
            // not "this category is empty". Reuses the existing loading design.
            if (subcategories.isEmpty() && exercises.isEmpty() && (isLoading || errorMessage != null)) {
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
                CategoryList(
                    categoryId = categoryId,
                    categoryName = categoryPath.lastOrNull()?.name,
                    onCategoryClick = onCategoryClick,
                    onExerciseClick = onExerciseClick,
                    onAddCustomClick = onAddCustomExerciseClick,
                    viewModel = viewModel,
                )
            }
        }
    }
}