package com.organizer.presentation.screens.exercises

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.presentation.OrganizerViewModel
import com.organizer.presentation.screens.general.DeleteConfirmationDialog
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun ExerciseListItem(
    modifier: Modifier = Modifier,
    dragHandle: @Composable (() -> Unit)? = null,
    exercise: ExerciseEntity,
    displayExerciseType: String = exercise.exerciseType,
    deletionInfo: Pair<String, String>?,
    expanded: Boolean,
    onClick: () -> Unit,
    onOpenClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onTrackClick: (() -> Unit)? = null,
    expandedContent: (@Composable () -> Unit)? = null,
    viewModel: OrganizerViewModel,
) {
    val textScrollState = rememberScrollState()
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(exercise.name) {
        if (textScrollState.maxValue > 0) {
            delay(1500.milliseconds)
            textScrollState.animateScrollBy(
                value = textScrollState.maxValue.toFloat(),
                animationSpec = tween(
                    durationMillis = 3000,
                    easing = LinearEasing
                )
            )
            delay(1500.milliseconds)
            textScrollState.animateScrollBy(
                value = -textScrollState.maxValue.toFloat(),
                animationSpec = tween(
                    durationMillis = 3000,
                    easing = LinearEasing
                )
            )
        }
    }

    Column(
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp),
            onClick = onClick,
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF7B61A8)
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 8.dp
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                dragHandle?.invoke()
                if (dragHandle != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                }

                if (exercise.imageUrls.isNotEmpty()) {
                    // Re-read when any media file lands; File equality keeps
                    // Coil from reloading unchanged images.
                    val mediaTick by viewModel.mediaVersion.collectAsState()
                    Box(
                        modifier = Modifier
                            .size(80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = remember(mediaTick) {
                                viewModel.getImageFile(exercise.imageUrls.first(), exercise.isCustom)
                            },
                            contentDescription = exercise.name,
                            modifier = Modifier
                                .fillMaxSize()
                                .scale(1.1f),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = exercise.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(textScrollState),
                        fontSize = 28.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                    ExerciseTypePill(exerciseType = displayExerciseType)
                }

                Spacer(modifier = Modifier.width(8.dp))

                val arrowRotation by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (expanded) 90f else 0f,
                    label = "exerciseExpandArrow"
                )
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowRight,
                    contentDescription = if (expanded) "Collapse exercise" else "Expand exercise",
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer { rotationZ = arrowRotation },
                    tint = Color.White.copy(alpha = 0.9f)
                )
            }
        }
        if (expanded) {
            if (expandedContent != null) {
                Spacer(modifier = Modifier.height(8.dp))
                expandedContent()
                Spacer(modifier = Modifier.height(8.dp))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onOpenClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open")
                }

                if (onTrackClick != null) {
                    Button(
                        onClick = onTrackClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF7B61A8),
                            contentColor = Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Track")
                    }
                }

                Button(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Delete")
                }

                if (showDeleteDialog) {
                    DeleteConfirmationDialog(
                        title = deletionInfo?.first ?: "no message provided",
                        message = deletionInfo?.second ?: "no message provided",
                        onConfirm = {
                            onDeleteClick()
                            showDeleteDialog = false
                        },
                        onDismiss = {
                            showDeleteDialog = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ExerciseTypePill(exerciseType: String) {
    val label = when (exerciseType) {
        "DURATION" -> "Duration"
        "REPS" -> "Reps"
        else -> "Reps + Weight"
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}
