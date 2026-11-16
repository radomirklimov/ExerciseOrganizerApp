package com.organizer.presentation.screens.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.organizer.presentation.OrganizerViewModel
import com.organizer.presentation.model.ExerciseCatalogEntry
import com.organizer.presentation.screens.general.SearchBar

private const val MAX_SEARCH_RESULTS = 40

private data class FilterOption(
    val filter: ExerciseFilter,
    val label: String,
)

private val FilterOptions = listOf(
    FilterOption(ExerciseFilter.All, "All"),
    FilterOption(ExerciseFilter.Standard, "Standard"),
    FilterOption(ExerciseFilter.Favorites, "Favorites"),
    FilterOption(ExerciseFilter.Custom, "Custom"),
)

/**
 * Exercise search, source filters and suggestions for the Create Workout
 * screen. Selection mirrors the Add to Workout sheet: tapping a row toggles
 * it and the footer shows the running count.
 */
@Composable
internal fun ExercisePickerSection(
    entries: List<ExerciseCatalogEntry>,
    suggestedEntries: List<ExerciseCatalogEntry>,
    selectedExerciseIds: List<Long>,
    onToggleExercise: (Long) -> Unit,
    viewModel: OrganizerViewModel,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf<ExerciseFilter>(ExerciseFilter.All) }

    val favoriteIds by viewModel.favoriteExerciseIds.collectAsState()
    val mediaTick by viewModel.mediaVersion.collectAsState()

    // With no query the scope is irrelevant: only the exercises the user
    // already uses in other workouts are suggested.
    val source = if (query.isBlank()) suggestedEntries else entries
    val results = remember(source, query, selectedFilter, favoriteIds) {
        filterExerciseEntries(
            entries = source,
            query = query,
            filter = selectedFilter,
            favoriteIds = favoriteIds,
        )
    }
    // Scopes only apply while searching, so each chip can preview how many
    // results it would return for the current query.
    val resultCounts = remember(source, query, favoriteIds) {
        if (query.isBlank()) {
            emptyMap()
        } else {
            ExerciseFilter.entries.associateWith { filter ->
                filterExerciseEntries(
                    entries = source,
                    query = query,
                    filter = filter,
                    favoriteIds = favoriteIds,
                ).size
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Add Exercises",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
        )

        Spacer(modifier = Modifier.height(12.dp))

        SearchBar(
            query = query,
            onQueryChange = { query = it },
            scopeLabel = selectedFilter.name,
            scopeColor = Brand,
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "SEARCH SCOPE",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = TextSecondary,
        )

        Spacer(modifier = Modifier.height(8.dp))

        FilterChipsRow(
            selectedFilter = selectedFilter,
            resultCounts = resultCounts,
            onSelect = { selectedFilter = it },
        )

        // Hide the suggestion heading and hint entirely when there is nothing
        // to suggest yet, e.g. before the user has created any workouts.
        if (query.isNotBlank() || results.isNotEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = if (query.isBlank()) {
                    "Recently Used in Other Workouts"
                } else {
                    "Search Results"
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = TextSecondary,
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (results.isEmpty()) {
                Text(
                    text = emptyMessage(query, selectedFilter),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    fontSize = 14.sp,
                    color = TextSecondary,
                )
            } else {
                val visibleResults = results.take(MAX_SEARCH_RESULTS)

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    visibleResults.forEach { entry ->
                        ExerciseOptionRow(
                            entry = entry,
                            selected = entry.exercise.exerciseId in selectedExerciseIds,
                            onClick = { onToggleExercise(entry.exercise.exerciseId) },
                            mediaTick = mediaTick,
                            viewModel = viewModel,
                        )
                    }
                }

                if (results.size > MAX_SEARCH_RESULTS) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Showing the first $MAX_SEARCH_RESULTS results. Refine your search.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterChipsRow(
    selectedFilter: ExerciseFilter,
    resultCounts: Map<ExerciseFilter, Int>,
    onSelect: (ExerciseFilter) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 1.dp),
    ) {
        items(FilterOptions, key = { it.filter.name }) { option ->
            FilterChip(
                label = option.label,
                selected = selectedFilter == option.filter,
                count = resultCounts[option.filter],
                onClick = { onSelect(option.filter) },
            )
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    count: Int?,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)

    Row(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) Brand else FieldBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else TextSecondary,
            maxLines = 1,
        )

        if (count != null) {
            Spacer(modifier = Modifier.width(7.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (selected) Color.White.copy(alpha = 0.22f)
                        else Brand.copy(alpha = 0.12f)
                    )
                    .padding(horizontal = 7.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = count.toString(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) Color.White else Brand,
                )
            }
        }
    }
}

@Composable
private fun ExerciseOptionRow(
    entry: ExerciseCatalogEntry,
    selected: Boolean,
    onClick: () -> Unit,
    mediaTick: Long,
    viewModel: OrganizerViewModel,
) {
    val shape = RoundedCornerShape(18.dp)
    val imageFile = remember(mediaTick, entry.exercise.exerciseId) {
        entry.exercise.imageUrls.firstOrNull()?.let { name ->
            viewModel.getImageFile(name, entry.exercise.isCustom)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) Brand50.copy(alpha = 0.7f) else Color.White)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Brand else RowBorder,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brand50),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.FitnessCenter,
                contentDescription = null,
                tint = Brand,
                modifier = Modifier.size(24.dp),
            )

            if (imageFile != null) {
                AsyncImage(
                    model = imageFile,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.exercise.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            entry.categoryLabel?.let { label ->
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (selected) Brand else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        SelectionIndicator(selected = selected)
    }
}

@Composable
private fun SelectionIndicator(selected: Boolean) {
    val shape = RoundedCornerShape(9.dp)

    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(shape)
            .background(if (selected) Brand else Color.White)
            .border(
                width = if (selected) 1.dp else 2.dp,
                color = if (selected) Brand else Color(0xFFCAC5D3),
                shape = shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun emptyMessage(query: String, filter: ExerciseFilter): String {
    if (query.isBlank()) {
        return "Exercises you use in other workouts will appear here."
    }

    val area = when (filter) {
        ExerciseFilter.All -> ""
        ExerciseFilter.Standard -> "standard "
        ExerciseFilter.Favorites -> "favorite "
        ExerciseFilter.Custom -> "custom "
    }
    return "No ${area}exercises match \"${query.trim()}\""
}
