package com.organizer.presentation.screens.exercises

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.organizer.presentation.OrganizerViewModel
import com.organizer.presentation.screens.general.CelebrationMessage
import com.organizer.presentation.screens.general.CelebrationOverlay
import com.organizer.ui.theme.AppTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.organizer.ui.theme.darkerShade

@Composable
fun ExerciseCard(
    exerciseId: Long,
    onCreateWorkoutClick: (Long) -> Unit,
    onOpenWorkoutsClick: () -> Unit,
    onBackClick: () -> Unit,
    viewModel: OrganizerViewModel,
) {
    val exercise by viewModel.observeExercise(exerciseId).collectAsState(initial = null)
    val favoriteIds by viewModel.favoriteExerciseIds.collectAsState()
    var showSavePopup by remember { mutableStateOf(false) }
    var celebration by remember {
        mutableStateOf<CelebrationMessage?>(null, referentialEqualityPolicy())
    }
    // Held until the "Add to Workout" sheet has left the composition. The sheet
    // is a dialog window drawn above the screen, so confetti started while it is
    // still around would be hidden behind it.
    var pendingCelebration by remember {
        mutableStateOf<CelebrationMessage?>(null, referentialEqualityPolicy())
    }
    val scrollState = rememberScrollState()

    LaunchedEffect(showSavePopup, pendingCelebration) {
        val message = pendingCelebration ?: return@LaunchedEffect
        if (!showSavePopup) {
            pendingCelebration = null
            celebration = message
        }
    }

    // All images of the displayed exercise download first.
    LaunchedEffect(exercise) {
        exercise?.let { viewModel.prioritizeExerciseMedia(it) }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .padding(bottom = 88.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

        if (exercise == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Loading...",
                    color = AppTheme.colors.textMuted,
                    fontSize = 16.sp
                )
            }
            return@Column
        }

        val exercise = exercise!!

        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val arrowBackground by animateColorAsState(
                targetValue = if (isPressed) AppTheme.colors.primary else AppTheme.colors.surfaceMuted,
                label = "exerciseBackArrowBackground",
            )
            val arrowTint by animateColorAsState(
                targetValue = if (isPressed) AppTheme.colors.onPrimary else AppTheme.colors.textSecondary,
                label = "exerciseBackArrowTint",
            )

            Surface(
                onClick = onBackClick,
                shape = CircleShape,
                color = arrowBackground,
                modifier = Modifier.size(40.dp),
                interactionSource = interactionSource,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = arrowTint,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            FavoriteToggle(
                isFavorite = exerciseId in favoriteIds,
                onToggle = { viewModel.toggleFavoriteExercise(exerciseId) },
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // MAIN IMAGE CARD
        ExerciseImageAnimation(
            imageUrls = exercise.imageUrls,
            exerciseName = exercise.name,
            isCustom = exercise.isCustom,
            viewModel = viewModel,
            modifier = Modifier
                .fillMaxWidth(),
            frameDurationMillis = 900L
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = exercise.name,
            textAlign = TextAlign.Center,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(18.dp))

        if (exercise.instructions.isNotEmpty()) {
            InstructionPager(
                instructions = exercise.instructions
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (showSavePopup) {
            SaveExercisePopup(
                exerciseId = exerciseId,
                onDismiss = {
                    showSavePopup = false
                },
                onCreateWorkoutClick = { onCreateWorkoutClick(exerciseId) },
                onOpenWorkoutsClick = onOpenWorkoutsClick,
                viewModel = viewModel,
                onExercisesAdded = { workoutNames ->
                    pendingCelebration = CelebrationMessage(
                        title = "Exercise Added! 🎉",
                        subtitle = workoutNamesSubtitle(workoutNames),
                    )
                },
            )
        }
    }

    if (exercise != null) {
        // Floats above the scrolling exercise content: the card stays visible
        // around the button instead of it sitting on a frosted container.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = { showSavePopup = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(25.dp),
                border = BorderStroke(2.dp, darkerShade(MaterialTheme.colorScheme.primary))
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Add to Workout",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    // Above both the scrolled content and the floating button.
    CelebrationOverlay(
        message = celebration,
        onDismiss = { celebration = null },
    )
    }
}

/** Subtitle for the "added" toast, naming where the exercise went. */
private fun workoutNamesSubtitle(workoutNames: List<String>): String = when {
    workoutNames.isEmpty() -> "Added to your workout"
    workoutNames.size == 1 -> "Added to ${workoutNames.first()}"
    else -> "Added to ${workoutNames.first()} + ${workoutNames.size - 1} more"
}

/**
 * Heart shown in the top-right corner of the exercise detail screen.
 * Filled and pink when the exercise is a favorite, outlined otherwise.
 */
@Composable
private fun FavoriteToggle(
    isFavorite: Boolean,
    onToggle: () -> Unit,
) {
    val containerColor by animateColorAsState(
        targetValue = if (isFavorite) AppTheme.colors.favoriteSoft else AppTheme.colors.surfaceMuted,
        label = "exerciseFavoriteBackground",
    )

    Surface(
        onClick = onToggle,
        shape = CircleShape,
        color = containerColor,
        modifier = Modifier.size(40.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isFavorite) {
                    Icons.Filled.Favorite
                } else {
                    Icons.Outlined.FavoriteBorder
                },
                contentDescription = if (isFavorite) {
                    "Remove from favorite exercises"
                } else {
                    "Add to favorite exercises"
                },
                tint = if (isFavorite) AppTheme.colors.favorite else AppTheme.colors.textSecondary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
