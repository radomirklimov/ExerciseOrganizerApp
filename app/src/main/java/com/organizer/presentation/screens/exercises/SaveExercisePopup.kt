package com.organizer.presentation.screens.exercises

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.organizer.data.local.db.entities.StarterTemplateEntity
import com.organizer.data.local.db.entities.WorkoutWithExerciseCount
import com.organizer.presentation.OrganizerViewModel

private val Brand = Color(0xFF5E43DA)
private val BrandDark = Color(0xFF4F35C2)
private val Brand50 = Color(0xFFF5F3FF)
private val Brand200 = Color(0xFFDDD6FE)
private val TextPrimary = Color(0xFF1C1B1F)
private val TextSecondary = Color(0xFF7A7582)
private val DividerColor = Color(0xFFF0EEF3)
private val FieldBackground = Color(0xFFF4F3F7)
private val RowBorder = Color(0xFFE4E2EA)
private val DisabledBackground = Color(0xFFE7E5EB)
private val DisabledText = Color(0xFFA9A4B2)

/**
 * Bottom sheet that lets the user add [exerciseId] to one or more existing
 * workouts, create a new workout that already contains it, or start from a
 * starter template.
 */
@Composable
fun SaveExercisePopup(
    exerciseId: Long,
    onDismiss: () -> Unit,
    onCreateWorkoutClick: () -> Unit,
    viewModel: OrganizerViewModel,
) {
    val workouts by viewModel.workoutsWithCountsUiState.collectAsState()
    val starterTemplates by viewModel.starterTemplatesUiState.collectAsState()

    var query by remember { mutableStateOf("") }
    var selectedWorkoutIds by remember { mutableStateOf(setOf<Long>()) }

    val visibleWorkouts = remember(workouts, query) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            workouts
        } else {
            workouts.filter { it.name.contains(trimmed, ignoreCase = true) }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
        ),
    ) {
        // The sheet draws its own scrim, so drop the default dialog dimming.
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { dialogWindow?.setDimAmount(0f) }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val maxSheetHeight = maxHeight * 0.85f

            var sheetVisible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { sheetVisible = true }

            AnimatedVisibility(
                visible = sheetVisible,
                enter = fadeIn(tween(durationMillis = 180)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss,
                        )
                )
            }

            AnimatedVisibility(
                visible = sheetVisible,
                enter = slideInVertically(tween(durationMillis = 260)) { it },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxSheetHeight),
                    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                    color = Color.White,
                    shadowElevation = 24.dp,
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        DragHandle()

                        SheetHeader(
                            title = if (workouts.isEmpty()) "Save to Workout" else "Add to Workout",
                            onClose = onDismiss,
                        )

                        if (workouts.isEmpty()) {
                            EmptyStateBody(
                                templates = starterTemplates,
                                onCreateWorkout = {
                                    onDismiss()
                                    onCreateWorkoutClick()
                                },
                                onTemplateClick = { templateId ->
                                    viewModel.addStarterTemplateToWorkouts(templateId) { workoutId ->
                                        viewModel.addExerciseToWorkout(workoutId, exerciseId)
                                        onDismiss()
                                    }
                                },
                                modifier = Modifier.weight(1f, fill = false),
                            )

                            EmptyStateFooter(
                                onCreateWorkout = {
                                    onDismiss()
                                    onCreateWorkoutClick()
                                },
                            )
                        } else {
                            WorkoutFilterField(
                                query = query,
                                onQueryChange = { query = it },
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = false)
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 24.dp, vertical = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                CreateWorkoutCard(
                                    onClick = {
                                        onDismiss()
                                        onCreateWorkoutClick()
                                    },
                                )

                                visibleWorkouts.forEach { workout ->
                                    WorkoutOptionRow(
                                        workout = workout,
                                        selected = workout.workoutId in selectedWorkoutIds,
                                        onToggle = {
                                            selectedWorkoutIds =
                                                if (workout.workoutId in selectedWorkoutIds) {
                                                    selectedWorkoutIds - workout.workoutId
                                                } else {
                                                    selectedWorkoutIds + workout.workoutId
                                                }
                                        },
                                    )
                                }

                                if (visibleWorkouts.isEmpty()) {
                                    NoMatchesRow(query = query)
                                }
                            }

                            SaveFooter(
                                selectedCount = selectedWorkoutIds.size,
                                onSave = {
                                    selectedWorkoutIds.forEach { workoutId ->
                                        viewModel.addExerciseToWorkout(
                                            workoutId = workoutId,
                                            exerciseId = exerciseId,
                                        )
                                    }
                                    onDismiss()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DragHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(5.dp)
                .clip(CircleShape)
                .background(Color(0xFFCBC7D3)),
        )
    }
}

@Composable
private fun SheetHeader(
    title: String,
    onClose: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    letterSpacing = (-0.5).sp,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Select existing routine or create a new one",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary,
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Surface(
                onClick = onClose,
                shape = CircleShape,
                color = Color(0xFFF1F0F4),
                modifier = Modifier.size(44.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = Color(0xFF6B6675),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        HorizontalDivider(color = DividerColor)
    }
}

@Composable
private fun WorkoutFilterField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 14.dp, bottom = 4.dp),
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
            ),
            cursorBrush = SolidColor(Brand),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(FieldBackground)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = Color(0xFF9A95A3),
                        modifier = Modifier.size(20.dp),
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (query.isEmpty()) {
                            Text(
                                text = "Search workouts...",
                                fontSize = 14.sp,
                                color = Color(0xFF9A95A3),
                            )
                        }
                        innerTextField()
                    }

                    if (query.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .clickable { onQueryChange("") },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Clear filter",
                                tint = Color(0xFF9A95A3),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun CreateWorkoutCard(onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brand50.copy(alpha = 0.55f))
            .clickable(onClick = onClick)
            .drawWithContent {
                drawContent()
                val strokeWidth = 2.dp.toPx()
                drawRoundRect(
                    color = Brand200,
                    topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f),
                    size = Size(
                        width = size.width - strokeWidth,
                        height = size.height - strokeWidth,
                    ),
                    cornerRadius = CornerRadius(18.dp.toPx() - strokeWidth / 2f),
                    style = Stroke(
                        width = strokeWidth,
                        pathEffect = PathEffect.dashPathEffect(
                            intervals = floatArrayOf(8.dp.toPx(), 5.dp.toPx()),
                        ),
                    ),
                )
            }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White)
                .border(1.dp, Brand200, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = Brand,
                modifier = Modifier.size(24.dp),
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Create New Workout",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = BrandDark,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Build routine and include this exercise",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Brand.copy(alpha = 0.65f),
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = Brand,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun WorkoutOptionRow(
    workout: WorkoutWithExerciseCount,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)

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
            .clickable(onClick = onToggle)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = workout.name,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = exerciseCountLabel(workout.exerciseCount),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary,
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

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

@Composable
private fun NoMatchesRow(query: String) {
    Text(
        text = "No workouts match \"${query.trim()}\"",
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        fontSize = 14.sp,
        color = TextSecondary,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun SaveFooter(
    selectedCount: Int,
    onSave: () -> Unit,
) {
    val enabled = selectedCount > 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White),
    ) {
        HorizontalDivider(color = DividerColor)

        Box(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(top = 14.dp, bottom = 22.dp),
        ) {
            Button(
                onClick = onSave,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .shadow(
                        elevation = if (enabled) 14.dp else 0.dp,
                        shape = RoundedCornerShape(20.dp),
                        ambientColor = Brand.copy(alpha = 0.45f),
                        spotColor = Brand.copy(alpha = 0.45f),
                    ),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Brand,
                    contentColor = Color.White,
                    disabledContainerColor = DisabledBackground,
                    disabledContentColor = DisabledText,
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    disabledElevation = 0.dp,
                ),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                Text(
                    text = saveLabel(selectedCount),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.2.sp,
                )
            }
        }
    }
}

@Composable
private fun EmptyStateBody(
    templates: List<StarterTemplateEntity>,
    onCreateWorkout: () -> Unit,
    onTemplateClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 22.dp, bottom = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Same illustration treatment as the empty Workouts page: soft lavender
        // glow, white rounded card and a lavender tile with a deep-purple icon.
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(152.dp)
                    .blur(48.dp)
                    .background(Color(0xFFE9DDFF).copy(alpha = 0.5f), CircleShape),
            )

            Box(
                modifier = Modifier
                    .size(128.dp)
                    .shadow(4.dp, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White)
                    .border(
                        1.dp,
                        Color(0xFFCBC4D2).copy(alpha = 0.3f),
                        RoundedCornerShape(24.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFE9DDFF)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.FitnessCenter,
                        contentDescription = null,
                        tint = Color(0xFF4F378A),
                        modifier = Modifier.size(42.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "You don't have any workouts yet",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            letterSpacing = (-0.2).sp,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Create your first workout routine to start organizing your exercises and tracking your training progress.",
            fontSize = 14.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 21.sp,
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onCreateWorkout,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Brand,
                contentColor = Color.White,
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
            ),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Create Workout Routine",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.2.sp,
            )
        }

        if (templates.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFFF8F7FA))
                    .border(1.dp, Color(0xFFE9E7EE), RoundedCornerShape(18.dp))
                    .padding(16.dp),
            ) {
                Text(
                    text = "QUICK START TEMPLATES",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4A4653),
                    letterSpacing = 0.7.sp,
                )

                Spacer(modifier = Modifier.height(10.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    templates.take(3).forEach { template ->
                        QuickStartChip(
                            label = template.name,
                            onClick = { onTemplateClick(template.templateId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickStartChip(
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE4E2EA)),
        shadowElevation = 1.dp,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF4A4653),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EmptyStateFooter(onCreateWorkout: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White),
    ) {
        HorizontalDivider(color = DividerColor)

        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button(
                onClick = {},
                enabled = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    disabledContainerColor = DisabledBackground,
                    disabledContentColor = DisabledText,
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    disabledElevation = 0.dp,
                ),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                Text(
                    text = "Save Exercise",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.2.sp,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onCreateWorkout)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = Brand,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Create New Workout",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Brand,
                )
            }
        }
    }
}

private fun saveLabel(selectedCount: Int): String = when (selectedCount) {
    0 -> "Select a Workout"
    1 -> "Save to 1 Workout"
    else -> "Save to $selectedCount Workouts"
}

private fun exerciseCountLabel(count: Int): String =
    "$count ${if (count == 1) "exercise" else "exercises"}"
