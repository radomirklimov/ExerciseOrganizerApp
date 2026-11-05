package com.organizer.presentation.screens.exercises

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.organizer.presentation.OrganizerViewModel
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun ExerciseImageAnimation(
    imageUrls: List<String>,
    exerciseName: String,
    isCustom: Boolean,
    viewModel: OrganizerViewModel,
    modifier: Modifier = Modifier,
    frameDurationMillis: Long = 900L
) {
    if (imageUrls.isEmpty()) return

    var currentIndex by remember(imageUrls) { mutableIntStateOf(0) }

    var isPlaying by remember(imageUrls) { mutableStateOf(false) }

    var animationDirection by remember(imageUrls) {
        mutableIntStateOf(1)
    }

    // Automatic animation.
    LaunchedEffect(imageUrls, isPlaying) {
        if (!isPlaying || imageUrls.size <= 1) {
            return@LaunchedEffect
        }

        while (isPlaying) {
            delay(frameDurationMillis.milliseconds)
            when (currentIndex) {
                imageUrls.lastIndex -> {
                    animationDirection = -1
                    currentIndex--
                }
                0 -> {
                    animationDirection = 1
                    currentIndex++
                }
                else -> {
                    currentIndex += animationDirection
                }
            }
        }
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(imageUrls) {
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onDragEnd = {
                             // Only change the image once when the user releases the finger
                            if (totalDrag < -80f) {
                                currentIndex = (currentIndex + 1) % imageUrls.size

                            } else if (totalDrag > 80f) {
                                currentIndex = if (currentIndex == 0) {
                                        imageUrls.lastIndex
                                    } else {
                                        currentIndex - 1
                                    }
                            }
                            totalDrag = 0f
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            totalDrag += dragAmount
                        }
                    )
                }
        ) {

            /*
             * IMAGE
             * Automatic playback - AnimatedContent fade
             * Manual swipe - Instant image change
             */
            if (isPlaying) {
                AnimatedContent(
                    targetState = currentIndex,
                    transitionSpec = {
                        fadeIn(
                            animationSpec = tween(
                                durationMillis = 450,
                                easing = FastOutSlowInEasing
                            )
                        ) togetherWith fadeOut(
                            animationSpec = tween(
                                durationMillis = 450,
                                easing = FastOutSlowInEasing
                            )
                        )
                    },
                    label = "exercise_image_animation"
                ) { index ->
                    // Keyed by tick and index: a finished download recomposes
                    // the model without reloading the other frames.
                    val mediaTick by viewModel.mediaVersion.collectAsState()
                    AsyncImage(
                        model = remember(mediaTick, index) {
                            viewModel.getImageFile(imageUrls[index], isCustom)
                        },
                        contentDescription = exerciseName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            } else {
                // Manual navigation uses a normal image(no fade)
                val mediaTick by viewModel.mediaVersion.collectAsState()
                AsyncImage(
                    model = remember(mediaTick, currentIndex) {
                        viewModel.getImageFile(
                            imageUrls[currentIndex],
                            isCustom
                        )
                    },
                    contentDescription = exerciseName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            // play/pause button
            if (imageUrls.size > 1) {
                IconButton(
                    onClick = { isPlaying = !isPlaying },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            Color.Black.copy(alpha = 0.55f)
                        )
                ) {
                    Icon(
                        imageVector = if (isPlaying) {
                            Icons.Default.Pause
                        } else {
                            Icons.Default.PlayArrow
                        },
                        contentDescription = if (isPlaying) {
                            "Pause animation"
                        } else {
                            "Play animation"
                        },
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            // Progress dots
            if (imageUrls.size > 1) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    repeat(imageUrls.size) { index ->
                        Box(
                            modifier = Modifier
                                .size(
                                    if (index == currentIndex) {
                                        8.dp
                                    } else {
                                        6.dp
                                    }
                                )
                                .clip(CircleShape)
                                .background(
                                    if (index == currentIndex) {
                                        Color.White
                                    } else {
                                        Color.White.copy(
                                            alpha = 0.45f
                                        )
                                    }
                                )
                        )
                    }
                }
            }
        }
    }
}
