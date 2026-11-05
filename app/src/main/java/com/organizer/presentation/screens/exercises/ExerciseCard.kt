package com.organizer.presentation.screens.exercises

import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import coil.compose.AsyncImage
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
    var showSavePopup by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    // All images of the displayed exercise download first.
    LaunchedEffect(exercise) {
        exercise?.let { viewModel.prioritizeExerciseMedia(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp),
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
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // MAIN IMAGE CARD
        ExerciseImageAnimation(
            imageUrls = exercise.imageUrls,
            exerciseName = exercise.name,
            isCustom = exercise.isCustom,
            viewModel = viewModel,
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp),
            frameDurationMillis = 900L
        )

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = exercise.name,
            textAlign = TextAlign.Center,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(28.dp))

        if (exercise.instructions.isNotEmpty()) {
            InstructionPager(
                instructions = exercise.instructions
            )
        }

        Spacer(modifier = Modifier.height(28.dp))
        Spacer(modifier = Modifier.weight(1f))

        // Save button
        Button(
            onClick = { showSavePopup = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                "Save",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

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
}
