package com.organizer.presentation.screens.schedule

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.organizer.presentation.model.ScheduledWorkoutItem
import com.organizer.presentation.screens.general.DeleteConfirmationDialog
import com.organizer.presentation.screens.general.LoadingState

@Composable
fun ScheduleScreen(
    onOpenWorkout: (Long) -> Unit,
    onCreateWorkout: () -> Unit,
    viewModel: ScheduleViewModel = hiltViewModel(),
) {
    val entriesState by viewModel.entriesUiState.collectAsState()
    val workoutsState by viewModel.workoutsUiState.collectAsState()
    val selectedDay by viewModel.selectedDay.collectAsState()
    val weekOffset by viewModel.weekOffset.collectAsState()

    val context = LocalContext.current
    var notificationsEnabled by remember {
        mutableStateOf(notificationsAllowed(context))
    }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        notificationsEnabled = notificationsAllowed(context)
    }

    val lifecycleOwner = context.findLifecycleOwner()
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsEnabled = notificationsAllowed(context)
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(observer)
        }
    }

    fun requestNotifications() {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

        when {
            !granted -> {
                if (permissionRequested) {
                    // The system has already shown its prompt. Further taps
                    // belong in app settings, where "Don't allow" can be undone.
                    openNotificationSettings(context)
                } else {
                    permissionRequested = true
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            !NotificationManagerCompat.from(context).areNotificationsEnabled() ->
                openNotificationSettings(context)
            else -> notificationsEnabled = true
        }
    }

    if (entriesState == null || workoutsState == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ScheduleBackground),
        ) {
            LoadingState()
        }
        return
    }

    val entries = entriesState.orEmpty()
    val workouts = workoutsState.orEmpty()
    val scheduledDays = remember(entries) {
        entries.map { it.entry.dayOfWeek }.toSet()
    }
    val week = remember(weekOffset, selectedDay) {
        buildWeek(weekOffset, selectedDay)
    }
    val dayEntries = remember(entries, selectedDay) {
        entries.filter { it.entry.dayOfWeek == selectedDay }
    }
    val dayCount = remember(entries) {
        entries.map { it.entry.dayOfWeek }.distinct().size
    }
    val selectedDayIsToday = week.days
        .firstOrNull { it.isoDay == selectedDay }
        ?.isToday == true

    var sheetOpen by remember { mutableStateOf(false) }
    var sheetEntry by remember { mutableStateOf<ScheduledWorkoutItem?>(null) }
    var pendingRemoval by remember { mutableStateOf<ScheduledWorkoutItem?>(null) }

    fun openSheet(entry: ScheduledWorkoutItem?) {
        sheetEntry = entry
        sheetOpen = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScheduleBackground),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 16.dp,
                bottom = 20.dp,
            ),
        ) {
            item {
                ScheduleHeader(dayCount = dayCount)
                Spacer(Modifier.height(18.dp))
            }

            item {
                ScheduleDayStrip(
                    week = week,
                    selectedDay = selectedDay,
                    scheduledDays = scheduledDays,
                    onSelectDay = viewModel::selectDay,
                    onPreviousWeek = viewModel::previousWeek,
                    onNextWeek = viewModel::nextWeek,
                )
                Spacer(Modifier.height(18.dp))
            }

            item {
                SelectedDayHeader(
                    dateLabel = week.selectedDateLabel,
                    isToday = selectedDayIsToday,
                    workoutCount = dayEntries.size,
                    onAddWorkout = { openSheet(null) },
                )
                Spacer(Modifier.height(12.dp))
            }

            if (dayEntries.isEmpty()) {
                item {
                    EmptyDayCard()
                    Spacer(Modifier.height(16.dp))
                }
            } else {
                items(
                    items = dayEntries,
                    key = { it.entry.scheduleId },
                ) { item ->
                    ScheduledWorkoutCard(
                        item = item,
                        notificationsEnabled = notificationsEnabled,
                        onStartWorkout = { onOpenWorkout(item.entry.workoutId) },
                        onEdit = { openSheet(item) },
                        onRemove = { pendingRemoval = item },
                        onToggleReminder = { enabled ->
                            viewModel.setReminderEnabled(item.entry.scheduleId, enabled)
                        },
                        onReminderMinutesChange = { minutes ->
                            viewModel.setReminderMinutesBefore(item.entry.scheduleId, minutes)
                        },
                        onNoteChange = { note ->
                            viewModel.updateReminderNote(item.entry.scheduleId, note)
                        },
                        onRequestNotifications = ::requestNotifications,
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                AddToScheduleButton(onClick = { openSheet(null) })
            }
        }
    }

    if (sheetOpen) {
        ScheduleWorkoutSheet(
            dayOfWeek = sheetEntry?.entry?.dayOfWeek ?: selectedDay,
            entry = sheetEntry,
            workouts = workouts,
            notificationsEnabled = notificationsEnabled,
            onDismiss = {
                sheetOpen = false
                sheetEntry = null
            },
            onSave = { workoutId, startMinutes, reminderEnabled, minutesBefore, note ->
                viewModel.saveEntry(
                    scheduleId = sheetEntry?.entry?.scheduleId,
                    workoutId = workoutId,
                    dayOfWeek = sheetEntry?.entry?.dayOfWeek ?: selectedDay,
                    startMinutes = startMinutes,
                    reminderEnabled = reminderEnabled,
                    reminderMinutesBefore = minutesBefore,
                    reminderNote = note,
                )
                sheetOpen = false
                sheetEntry = null
                if (reminderEnabled && !notificationsEnabled) {
                    requestNotifications()
                }
            },
            onRemove = sheetEntry?.let { item ->
                {
                    pendingRemoval = item
                    sheetOpen = false
                    sheetEntry = null
                }
            },
            onCreateWorkout = {
                sheetOpen = false
                sheetEntry = null
                onCreateWorkout()
            },
            onRequestNotifications = ::requestNotifications,
        )
    }

    pendingRemoval?.let { item ->
        DeleteConfirmationDialog(
            title = "Remove from Schedule?",
            itemName = item.workoutName,
            location = "your weekly schedule",
            isPermanent = false,
            onConfirm = {
                viewModel.removeEntry(item)
                pendingRemoval = null
            },
            onDismiss = { pendingRemoval = null },
        )
    }
}

@Composable
private fun ScheduleHeader(dayCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Schedule",
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
                color = ScheduleInk,
            )
            Text(
                text = "Weekly Routine & Reminders",
                modifier = Modifier.padding(top = 1.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = ScheduleInkSecondary,
            )
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(ScheduleBrand50)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                text = "$dayCount ${if (dayCount == 1) "day" else "days"} / week",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = ScheduleBrand,
            )
        }
    }
}

@Composable
private fun SelectedDayHeader(
    dateLabel: String,
    isToday: Boolean,
    workoutCount: Int,
    onAddWorkout: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = dateLabel,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = ScheduleInk,
                )
                if (isToday) {
                    Text(
                        text = "Today",
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(ScheduleTodayGreenBg)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = ScheduleTodayGreen,
                    )
                }
            }

            Text(
                text = when (workoutCount) {
                    0 -> "Rest day — no workouts scheduled"
                    1 -> "1 workout scheduled for this day"
                    else -> "$workoutCount workouts scheduled for this day"
                },
                modifier = Modifier.padding(top = 1.dp),
                fontSize = 11.sp,
                color = ScheduleInkSecondary,
            )
        }

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(ScheduleBrand50)
                .clickable(onClick = onAddWorkout)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = ScheduleBrand,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = "Add Workout",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = ScheduleBrand,
            )
        }
    }
}

@Composable
private fun EmptyDayCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(ScheduleSurface)
            .padding(horizontal = 20.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Rest day",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = ScheduleInk,
        )
        Text(
            text = "No workouts scheduled for this day. Add one to build your weekly routine.",
            modifier = Modifier.padding(top = 4.dp),
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = ScheduleInkSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AddToScheduleButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ScheduleBrand,
            contentColor = Color.White,
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 6.dp,
            pressedElevation = 2.dp,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "Add Workout to Schedule",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun Context.findLifecycleOwner(): LifecycleOwner? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is LifecycleOwner) return current
        current = current.baseContext
    }
    return current as? LifecycleOwner
}

private fun notificationsAllowed(context: Context): Boolean {
    val runtimeGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

    return runtimeGranted &&
        NotificationManagerCompat.from(context).areNotificationsEnabled()
}

private fun openNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
    } else {
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    runCatching { context.startActivity(intent) }
}
