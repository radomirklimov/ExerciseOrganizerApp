package com.organizer.presentation.screens.exercises

import androidx.compose.foundation.background
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
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.organizer.presentation.OrganizerViewModel
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun ExerciseCard(
    exerciseId: Long,
    onCreateWorkoutClick: () -> Unit,
    onBackClick: () -> Unit,
    viewModel: OrganizerViewModel,
) {
    val exercise by viewModel.observeExercise(exerciseId).collectAsState(initial = null)
    val favoriteIds by viewModel.favoriteExerciseIds.collectAsState()
    var showSavePopup by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

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
                    color = Color.Gray,
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
                targetValue = if (isPressed) Color(0xFF6750A4) else Color(0xFFF7F2F8),
                label = "exerciseBackArrowBackground",
            )
            val arrowTint by animateColorAsState(
                targetValue = if (isPressed) Color.White else Color(0xFF494551),
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
                onCreateWorkoutClick = onCreateWorkoutClick,
                viewModel = viewModel
            )
        }
    }

    if (exercise != null) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .matchParentSize()
                    .blur(48.dp)
                    .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
            )
            Button(
                onClick = { showSavePopup = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(25.dp)
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
    }
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
        targetValue = if (isFavorite) Color(0xFFFBE4EC) else Color(0xFFF7F2F8),
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
                tint = if (isFavorite) Color(0xFFE05275) else Color(0xFF494551),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
