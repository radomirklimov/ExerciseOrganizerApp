package com.organizer.presentation.screens.schedule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.organizer.data.repo.joinCategoryNames
import com.organizer.presentation.model.ScheduledWorkoutItem
import kotlinx.coroutines.delay

@Composable
internal fun ScheduledWorkoutCard(
    item: ScheduledWorkoutItem,
    notificationsEnabled: Boolean,
    onStartWorkout: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onToggleReminder: (Boolean) -> Unit,
    onReminderMinutesChange: (Int) -> Unit,
    onNoteChange: (String) -> Unit,
    onRequestNotifications: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var noteText by remember(item.entry.scheduleId) {
        mutableStateOf(item.entry.reminderNote)
    }
    var noteDirty by remember(item.entry.scheduleId) {
        mutableStateOf(false)
    }
    val latestNote by rememberUpdatedState(noteText)
    val latestStoredNote by rememberUpdatedState(item.entry.reminderNote)

    // Adopt changes made elsewhere (the edit sheet), but never overwrite text
    // the user is currently typing while a debounced save is still pending.
    LaunchedEffect(item.entry.reminderNote) {
        when {
            item.entry.reminderNote == noteText -> noteDirty = false
            !noteDirty -> noteText = item.entry.reminderNote
        }
    }

    // Persist notes after the user pauses instead of on every keystroke.
    LaunchedEffect(noteText) {
        if (noteText != item.entry.reminderNote) {
            noteDirty = true
            delay(600)
            onNoteChange(noteText)
        } else {
            noteDirty = false
        }
    }

    DisposableEffect(item.entry.scheduleId) {
        onDispose {
            if (latestNote != latestStoredNote) {
                onNoteChange(latestNote)
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = ScheduleSurface,
        border = BorderStroke(1.dp, ScheduleCardBorder),
        shadowElevation = 2.dp,
    ) {
        Box {
            // Subtle accent line across the top of the card.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.horizontalGradient(
                            listOf(ScheduleBrand, Color(0xFF9B7BE8))
                        )
                    ),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp, bottom = 14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(ScheduleBrand50)
                            .border(1.dp, ScheduleBrand100, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FitnessCenter,
                            contentDescription = null,
                            tint = ScheduleBrand,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = item.workoutName,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = ScheduleInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )

                            ExerciseCountPill(item.exerciseCount)
                        }

                        val categoryLabel = joinCategoryNames(item.categories)
                        if (!categoryLabel.isNullOrBlank()) {
                            Text(
                                text = categoryLabel,
                                modifier = Modifier.padding(top = 2.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = ScheduleInkSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Row(
                            modifier = Modifier.padding(top = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TimePill(item.entry.startMinutes)

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Repeat,
                                    contentDescription = null,
                                    tint = ScheduleInkMuted,
                                    modifier = Modifier.size(12.dp),
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = "Every ${isoDayShortName(item.entry.dayOfWeek)}",
                                    fontSize = 10.sp,
                                    color = ScheduleInkMuted,
                                )
                            }
                        }
                    }

                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.MoreVert,
                                contentDescription = "Schedule options",
                                tint = ScheduleInkMuted,
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            containerColor = ScheduleSurface,
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit schedule", fontSize = 13.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onEdit()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Remove", fontSize = 13.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = null,
                                        tint = ScheduleDanger,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onRemove()
                                },
                            )
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(top = 12.dp),
                    color = ScheduleDivider,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onStartWorkout)
                        .padding(top = 10.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Start Workout Now",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ScheduleBrand,
                    )
                    Spacer(Modifier.width(3.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = ScheduleBrand,
                        modifier = Modifier.size(15.dp),
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(top = 10.dp),
                    color = ScheduleDivider,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ScheduleBrand50),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = null,
                            tint = ScheduleBrand,
                            modifier = Modifier.size(17.dp),
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Workout Reminder",
                            fontSize = 13.sp,
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
                            fontSize = 10.sp,
                            color = if (notificationsEnabled) {
                                ScheduleInkMuted
                            } else {
                                ScheduleBrand
                            },
                        )
                    }

                    Switch(
                        checked = item.entry.reminderEnabled,
                        onCheckedChange = { enabled ->
                            onToggleReminder(enabled)
                            if (enabled && !notificationsEnabled) {
                                onRequestNotifications()
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ScheduleSurface,
                            checkedTrackColor = ScheduleBrand,
                            checkedBorderColor = ScheduleBrand,
                            uncheckedThumbColor = ScheduleSurface,
                            uncheckedTrackColor = Color(0xFFCBD5E1),
                            uncheckedBorderColor = Color.Transparent,
                        ),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Notification Timing",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (item.entry.reminderEnabled) {
                            Color(0xFF334155)
                        } else {
                            ScheduleInkMuted
                        },
                    )
                    Text(
                        text = reminderTimingLabel(item.entry.reminderMinutesBefore),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (item.entry.reminderEnabled) {
                            ScheduleBrand
                        } else {
                            ScheduleInkMuted
                        },
                    )
                }

                ReminderTimingPills(
                    selectedMinutes = item.entry.reminderMinutesBefore,
                    enabled = item.entry.reminderEnabled,
                    onSelect = onReminderMinutesChange,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Personal Note",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF334155),
                    )
                    Text(
                        text = "Included in alert",
                        fontSize = 9.sp,
                        color = ScheduleInkMuted,
                    )
                }

                OutlinedTextField(
                    value = noteText,
                    onValueChange = {
                        noteDirty = true
                        noteText = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = "Add a note for this reminder",
                            fontSize = 12.sp,
                            color = ScheduleInkMuted,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = null,
                            tint = ScheduleBrand,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ScheduleFieldBackground,
                        unfocusedContainerColor = ScheduleFieldBackground,
                        focusedBorderColor = ScheduleBrand,
                        unfocusedBorderColor = ScheduleFieldBorder,
                        focusedTextColor = ScheduleInk,
                        unfocusedTextColor = ScheduleInk,
                        cursorColor = ScheduleBrand,
                    ),
                )
            }
        }
    }
}

@Composable
private fun ExerciseCountPill(count: Int) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFFF1F5F9))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            text = "$count ${if (count == 1) "exercise" else "exercises"}",
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF475569),
        )
    }
}

@Composable
private fun TimePill(startMinutes: Int) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(ScheduleBrand50)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Schedule,
            contentDescription = null,
            tint = ScheduleBrand,
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = formatMinutes(startMinutes),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = ScheduleBrand,
        )
    }
}
