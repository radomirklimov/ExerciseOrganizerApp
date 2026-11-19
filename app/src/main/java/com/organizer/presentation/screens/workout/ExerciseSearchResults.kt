package com.organizer.presentation.screens.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.organizer.ui.theme.AppTheme
import com.organizer.presentation.model.ExerciseCatalogEntry

/** Upper bound for a search result list; the UI offers a "refine" hint above it. */
internal const val MAX_EXERCISE_SEARCH_RESULTS = 40

/** "Search Results" / "Recently Used in Other Workouts" heading. */
@Composable
internal fun ExerciseSectionHeading(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        color = TextSecondary,
    )
}

@Composable
internal fun ExerciseSearchEmptyMessage(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        fontSize = 14.sp,
        color = TextSecondary,
    )
}

@Composable
internal fun ExerciseSearchLimitHint() {
    Text(
        text = "Showing the first $MAX_EXERCISE_SEARCH_RESULTS results. Refine your search.",
        fontSize = 12.sp,
        color = TextSecondary,
    )
}

/**
 * A catalogue row: image, exercise name and its category summary.
 *
 * Used by the Create Workout search (selectable, with a checkbox) and by the
 * home search (tap opens the exercise, with a chevron instead).
 */
@Composable
internal fun ExerciseOptionRow(
    entry: ExerciseCatalogEntry,
    onClick: () -> Unit,
    viewModel: OrganizerViewModel,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    selectable: Boolean = true,
) {
    val mediaTick by viewModel.mediaVersion.collectAsState()
    val shape = RoundedCornerShape(18.dp)
    val imageFile = remember(mediaTick, entry.exercise.exerciseId) {
        entry.exercise.imageUrls.firstOrNull()?.let { name ->
            viewModel.getImageFile(name, entry.exercise.isCustom)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) Brand50.copy(alpha = 0.7f) else AppTheme.colors.surface)
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

        if (selectable) {
            SelectionIndicator(selected = selected)
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SelectionIndicator(selected: Boolean) {
    val shape = RoundedCornerShape(9.dp)

    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(shape)
            .background(if (selected) Brand else AppTheme.colors.surface)
            .border(
                width = if (selected) 1.dp else 2.dp,
                color = if (selected) Brand else AppTheme.colors.borderStrong,
                shape = shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = AppTheme.colors.onPrimary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

internal fun emptyExerciseSearchMessage(query: String, filter: ExerciseFilter): String {
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
