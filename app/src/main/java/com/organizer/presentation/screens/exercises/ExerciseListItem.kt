package com.organizer.presentation.screens.exercises

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
    deletionInfo: Pair<String, String>?,
    expanded: Boolean,
    onClick: () -> Unit,
    onOpenClick: () -> Unit,
    onDeleteClick: () -> Unit,
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
                .height(110.dp),
            onClick = onClick,
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF7B61A8)
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 4.dp
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                dragHandle?.invoke()

                if (exercise.imageUrls.isNotEmpty()) {
                    // Re-read when any media file lands; File equality keeps
                    // Coil from reloading unchanged images.
                    val mediaTick by viewModel.mediaVersion.collectAsState()
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(12.dp)),
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

                Spacer(modifier = Modifier.width(24.dp))

                Text(
                    text = exercise.name,
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(textScrollState),
                    fontSize = 28.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.width(15.dp))

                Text(
                    text = "➜",
                    fontSize = 28.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
        if (expanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onOpenClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Open")
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                ) {
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
