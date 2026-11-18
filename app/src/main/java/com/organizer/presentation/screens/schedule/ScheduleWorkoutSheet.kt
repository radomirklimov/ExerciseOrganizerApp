package com.organizer.presentation.screens.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.organizer.data.local.db.entities.WorkoutWithExerciseCount
import com.organizer.presentation.model.ScheduledWorkoutItem
import java.util.Locale

private const val DEFAULT_START_MINUTES = 18 * 60

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScheduleWorkoutSheet(
    dayOfWeek: Int,
    targetDateKey: Int,
    entry: ScheduledWorkoutItem?,
    workouts: List<WorkoutWithExerciseCount>,
    notificationsEnabled: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        workoutId: Long,
        scheduledDateKey: Int?,
        startMinutes: Int,
        reminderEnabled: Boolean,
        reminderMinutesBefore: Int,
        reminderNote: String,
    ) -> Unit,
    onCreateWorkout: () -> Unit,
    onRequestNotifications: () -> Unit,
) {
    val storedEntry = entry?.entry
    val stateKey = storedEntry?.scheduleId ?: -1L

    var selectedWorkoutId by remember(stateKey) {
        mutableStateOf(storedEntry?.workoutId)
    }
    var startMinutes by remember(stateKey) {
        mutableIntStateOf(storedEntry?.startMinutes ?: DEFAULT_START_MINUTES)
    }
    var reminderEnabled by remember(stateKey) {
        mutableStateOf(storedEntry?.reminderEnabled ?: true)
    }
    var reminderMinutesBefore by remember(stateKey) {
        mutableIntStateOf(storedEntry?.reminderMinutesBefore ?: 60)
    }
    var reminderNote by remember(stateKey) {
        mutableStateOf(storedEntry?.reminderNote.orEmpty())
    }
    var repeatsWeekly by remember(stateKey) {
        mutableStateOf(storedEntry?.scheduledDateKey == null)
    }

    var workoutMenuExpanded by remember { mutableStateOf(false) }
    var repeatsMenuExpanded by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    LaunchedEffect(workouts, selectedWorkoutId) {
        val selectionStillExists = workouts.any { it.workoutId == selectedWorkoutId }
        if (selectedWorkoutId == null || !selectionStillExists) {
            selectedWorkoutId = workouts.firstOrNull()?.workoutId
        }
    }

    val selectedWorkout = workouts.firstOrNull { it.workoutId == selectedWorkoutId }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ScheduleSurface,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (entry == null) "Schedule a Workout" else "Edit Schedule",
                    modifier = Modifier.weight(1f),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = ScheduleInk,
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = ScheduleInkMuted,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            if (workouts.isEmpty()) {
                NoWorkoutsHint(onCreateWorkout = onCreateWorkout)
            } else {
                FieldLabel("Choose Workout")

                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(ScheduleFieldBackground)
                            .border(1.dp, ScheduleFieldBorder, RoundedCornerShape(14.dp))
                            .clickable { workoutMenuExpanded = true }
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = selectedWorkout?.name ?: "Select a workout",
                            modifier = Modifier.weight(1f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selectedWorkout == null) {
                                ScheduleInkMuted
                            } else {
                                ScheduleInk
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(
                            imageVector = Icons.Outlined.ArrowDropDown,
                            contentDescription = null,
                            tint = ScheduleInkMuted,
                            modifier = Modifier.size(22.dp),
                        )
                    }

                    DropdownMenu(
                        expanded = workoutMenuExpanded,
                        onDismissRequest = { workoutMenuExpanded = false },
                        containerColor = ScheduleSurface,
                    ) {
                        workouts.forEach { workout ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = workout.name,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = ScheduleInk,
                                        )
                                        Text(
                                            text = "${workout.exerciseCount} " +
                                                "${if (workout.exerciseCount == 1) "exercise" else "exercises"}",
                                            fontSize = 12.sp,
                                            color = ScheduleInkMuted,
                                        )
                                    }
                                },
                                onClick = {
                                    selectedWorkoutId = workout.workoutId
                                    workoutMenuExpanded = false
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        FieldLabel("Workout Time")
                        CompactField(
                            onClick = { showTimePicker = true },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Schedule,
                                contentDescription = null,
                                tint = ScheduleBrand,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = formatMinutes(startMinutes),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = ScheduleInk,
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        FieldLabel("Repeats")

                        Box {
                            CompactField(onClick = { repeatsMenuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.Repeat,
                                    contentDescription = null,
                                    tint = ScheduleBrand,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (repeatsWeekly) {
                                        "Every ${isoDayName(dayOfWeek)}"
                                    } else {
                                        "This ${isoDayName(dayOfWeek)} Only"
                                    },
                                    modifier = Modifier.weight(1f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ScheduleInk,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Icon(
                                    imageVector = Icons.Outlined.ArrowDropDown,
                                    contentDescription = null,
                                    tint = ScheduleInkMuted,
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            DropdownMenu(
                                expanded = repeatsMenuExpanded,
                                onDismissRequest = { repeatsMenuExpanded = false },
                                containerColor = ScheduleSurface,
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = "Every ${isoDayName(dayOfWeek)}",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = ScheduleInk,
                                            )
                                            Text(
                                                text = "Repeats weekly",
                                                fontSize = 12.sp,
                                                color = ScheduleInkMuted,
                                            )
                                        }
                                    },
                                    onClick = {
                                        repeatsWeekly = true
                                        repeatsMenuExpanded = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = "This ${isoDayName(dayOfWeek)} Only",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = ScheduleInk,
                                            )
                                            Text(
                                                text = "Only on ${formatDateKeyShort(targetDateKey)}",
                                                fontSize = 12.sp,
                                                color = ScheduleInkMuted,
                                            )
                                        }
                                    },
                                    onClick = {
                                        repeatsWeekly = false
                                        repeatsMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(ScheduleFieldBackground)
                        .padding(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ScheduleBrand50),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Notifications,
                                contentDescription = null,
                                tint = ScheduleBrand,
                                modifier = Modifier.size(19.dp),
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Workout Reminder",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = ScheduleInk,
                            )
                            Text(
                                text = if (notificationsEnabled) {
                                    "Get an alert before workout begins"
                                } else {
                                    "Notifications are off — tap to allow"
                                },
                                modifier = if (notificationsEnabled) {
                                    Modifier
                                } else {
                                    Modifier.clickable(onClick = onRequestNotifications)
                                },
                                fontSize = 12.sp,
                                color = if (notificationsEnabled) {
                                    ScheduleInkMuted
                                } else {
                                    ScheduleBrand
                                },
                            )
                        }

                        Switch(
                            checked = reminderEnabled,
                            onCheckedChange = { enabled ->
                                reminderEnabled = enabled
                                if (enabled && !notificationsEnabled) {
                                    onRequestNotifications()
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ScheduleSurface,
                                checkedTrackColor = ScheduleBrand,
                                checkedBorderColor = ScheduleBrand,
                                uncheckedThumbColor = ScheduleSurface,
                                uncheckedTrackColor = androidx.compose.ui.graphics.Color(0xFFCBD5E1),
                                uncheckedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                            ),
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Notification Timing",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (reminderEnabled) {
                                androidx.compose.ui.graphics.Color(0xFF334155)
                            } else {
                                ScheduleInkMuted
                            },
                        )
                        Text(
                            text = reminderTimingLabel(reminderMinutesBefore),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (reminderEnabled) ScheduleBrand else ScheduleInkMuted,
                        )
                    }

                    ReminderTimingPills(
                        selectedMinutes = reminderMinutesBefore,
                        enabled = reminderEnabled,
                        onSelect = { reminderMinutesBefore = it },
                    )

                    FieldLabel("Personal Note", modifier = Modifier.padding(top = 14.dp))

                    OutlinedTextField(
                        value = reminderNote,
                        onValueChange = { reminderNote = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                text = "Included in the notification",
                                fontSize = 14.sp,
                                color = ScheduleInkMuted,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Notes,
                                contentDescription = null,
                                tint = ScheduleBrand,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 14.sp),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ScheduleSurface,
                            unfocusedContainerColor = ScheduleSurface,
                            focusedBorderColor = ScheduleBrand,
                            unfocusedBorderColor = ScheduleFieldBorder,
                            focusedTextColor = ScheduleInk,
                            unfocusedTextColor = ScheduleInk,
                            cursorColor = ScheduleBrand,
                        ),
                    )
                }

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = {
                        val workoutId = selectedWorkoutId ?: return@Button
                        onSave(
                            workoutId,
                            if (repeatsWeekly) null else targetDateKey,
                            startMinutes,
                            reminderEnabled,
                            reminderMinutesBefore,
                            reminderNote,
                        )
                    },
                    enabled = selectedWorkoutId != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ScheduleBrand,
                        contentColor = ScheduleSurface,
                        disabledContainerColor = ScheduleBrand.copy(alpha = 0.4f),
                        disabledContentColor = ScheduleSurface,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (entry == null) "Save to Schedule" else "Save Changes",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

            }
        }
    }

    if (showTimePicker) {
        WorkoutTimePickerDialog(
            initialMinutes = startMinutes,
            onDismiss = { showTimePicker = false },
            onConfirm = {
                startMinutes = it
                showTimePicker = false
            },
        )
    }
}

@Composable
private fun NoWorkoutsHint(onCreateWorkout: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(ScheduleFieldBackground)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Create a workout first",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = ScheduleInk,
        )
        Text(
            text = "You need at least one workout before you can put it on your schedule.",
            modifier = Modifier.padding(top = 6.dp),
            fontSize = 14.sp,
            color = ScheduleInkSecondary,
        )
        TextButton(onClick = onCreateWorkout) {
            Text(
                text = "Create Workout",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = ScheduleBrand,
            )
        }
    }
}

@Composable
private fun FieldLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.padding(bottom = 8.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = androidx.compose.ui.graphics.Color(0xFF334155),
    )
}

@Composable
private fun CompactField(
    onClick: (() -> Unit)?,
    content: @Composable RowScope.() -> Unit,
) {
    val base = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .background(ScheduleFieldBackground)
        .border(1.dp, ScheduleFieldBorder, RoundedCornerShape(14.dp))

    Row(
        modifier = (if (onClick != null) base.clickable(onClick = onClick) else base)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutTimePickerDialog(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = true,
    )

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(30.dp))
                .background(ScheduleSurface)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Workout Time",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = ScheduleInk,
            )

            Spacer(Modifier.height(10.dp))

            TimePicker(state = timePickerState)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = ScheduleInkSecondary)
                }
                TextButton(
                    onClick = {
                        onConfirm(timePickerState.hour * 60 + timePickerState.minute)
                    },
                ) {
                    Text(
                        text = "Set",
                        fontWeight = FontWeight.Bold,
                        color = ScheduleBrand,
                    )
                }
            }
        }
    }
}
