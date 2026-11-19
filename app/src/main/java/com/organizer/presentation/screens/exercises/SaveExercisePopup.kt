package com.organizer.presentation.screens.exercises

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.organizer.data.local.db.entities.WorkoutWithExerciseCount
import com.organizer.presentation.OrganizerViewModel
import com.organizer.presentation.screens.general.CreateWorkoutFirstHint
import com.organizer.presentation.screens.general.LoadingState
import com.organizer.ui.theme.themedColor
import com.organizer.ui.theme.AppTheme

private val Brand: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFF6750A4), AppTheme.colors.primary)
private val BrandDark: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFF4F378A), AppTheme.colors.primaryDark)
private val Brand50: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFFF1ECF2), AppTheme.colors.primarySoft)
private val Brand200: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFFCBC4D2), AppTheme.colors.borderStrong)
private val TextPrimary: Color
    @Composable @ReadOnlyComposable get() = AppTheme.colors.textPrimary
private val TextSecondary: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFF7A7582), AppTheme.colors.textMuted)
private val DividerColor: Color
    @Composable @ReadOnlyComposable get() = AppTheme.colors.divider
private val FieldBackground: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFFF4F3F7), AppTheme.colors.surfaceMuted)
private val RowBorder: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFFE4E2EA), AppTheme.colors.border)
private val DisabledBackground: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFFE7E5EB), AppTheme.colors.surfaceStrong)
private val DisabledText: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFFA9A4B2), AppTheme.colors.textMuted)

/**
 * Bottom sheet that lets the user add [exerciseId] to one or more existing
 * workouts, or create a new workout that already contains it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveExercisePopup(
    exerciseId: Long,
    onDismiss: () -> Unit,
    onCreateWorkoutClick: () -> Unit,
    onOpenWorkoutsClick: () -> Unit,
    viewModel: OrganizerViewModel,
) {
    val workoutsState by viewModel.workoutsWithCountsUiState.collectAsState()
    val workouts = workoutsState.orEmpty()

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

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppTheme.colors.surface,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() },
        // The default content insets are consumed dynamically from the sheet's drag offset
        // (`consumeWindowInsets(top = sheetState.offset)`). That makes the content's max
        // height change while dragging, which moves the Expanded anchor and makes the sheet
        // bounce forever after a fast drag. Insets are handled statically below instead.
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // Keep the top of the page visible, like the Schedule sheet.
            val maxSheetHeight = maxHeight * 0.85f

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxSheetHeight)
                    .navigationBarsPadding(),
            ) {
                SheetHeader(onClose = onDismiss)

                if (workoutsState == null) {
                    LoadingState(modifier = Modifier.weight(1f, fill = false))
                } else if (workouts.isEmpty()) {
                    // Same hint as the schedule sheet: the popup keeps its
                    // header and the rest of the body is this block.
                    CreateWorkoutFirstHint(
                        onOpenWorkouts = {
                            onDismiss()
                            onOpenWorkoutsClick()
                        },
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .padding(top = 22.dp, bottom = 24.dp),
                        description = "You need at least one workout before you can add this exercise to it.",
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

@Composable
private fun SheetHeader(onClose: () -> Unit) {
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
                    text = "Add to Workout",
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
                color = AppTheme.colors.surfaceMuted,
                modifier = Modifier.size(44.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = AppTheme.colors.textMuted,
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 14.dp, bottom = 4.dp),
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
                    tint = AppTheme.colors.textMuted,
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
                            color = AppTheme.colors.textMuted,
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
                            tint = AppTheme.colors.textMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun CreateWorkoutCard(onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    // Read the palette here: drawWithContent runs outside the composable scope.
    val dashedBorder = Brand200

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
                    color = dashedBorder,
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
                .background(AppTheme.colors.surface)
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
            .background(if (selected) Brand50.copy(alpha = 0.7f) else AppTheme.colors.surface)
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

@Composable
private fun SaveFooter(
    selectedCount: Int,
    onSave: () -> Unit,
) {
    val enabled = selectedCount > 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppTheme.colors.surface),
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
                    contentColor = AppTheme.colors.onPrimary,
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

private fun saveLabel(selectedCount: Int): String =
    if (selectedCount == 0) "Select a Workout" else "Add Exercise"

private fun exerciseCountLabel(count: Int): String =
    "$count ${if (count == 1) "exercise" else "exercises"}"
