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
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.organizer.ui.theme.darkerShade

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
    val week = remember(weekOffset, selectedDay) {
        buildWeek(weekOffset, selectedDay)
    }
    val selectedDateKey = week.days.first { it.isoDay == selectedDay }.dateKey
    // Recurring entries mark their weekday in every week; one-time entries
    // only mark the week that contains their date.
    val scheduledDays = remember(entries, week) {
        week.days
            .filter { day ->
                entries.any { item ->
                    val entry = item.entry
                    if (entry.scheduledDateKey != null) {
                        entry.scheduledDateKey == day.dateKey
                    } else {
                        entry.dayOfWeek == day.isoDay
                    }
                }
            }
            .map { it.isoDay }
            .toSet()
    }
    val dayEntries = remember(entries, selectedDay, selectedDateKey) {
        entries.filter { item ->
            val entry = item.entry
            if (entry.scheduledDateKey != null) {
                entry.scheduledDateKey == selectedDateKey
            } else {
                entry.dayOfWeek == selectedDay
            }
        }
    }
    val dayCount = scheduledDays.size
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScheduleBackground),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 24.dp,
                end = 24.dp,
                top = 20.dp,
                // Leaves room for the pinned button so the final card can
                // scroll clear of it. While scrolling, the schedule content
                // passes underneath the button.
                bottom = 108.dp,
            ),
        ) {
            item {
                ScheduleHeader(dayCount = dayCount)
                Spacer(Modifier.height(20.dp))
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
                Spacer(Modifier.height(20.dp))
            }

            item {
                SelectedDayHeader(
                    dateLabel = week.selectedDateLabel,
                    isToday = selectedDayIsToday,
                    workoutCount = dayEntries.size,
                )
                Spacer(Modifier.height(14.dp))
            }

            if (dayEntries.isEmpty()) {
                item {
                    EmptyDayCard()
                    Spacer(Modifier.height(18.dp))
                }
            } else {
                items(
                    items = dayEntries,
                    key = { it.entry.scheduleId },
                ) { item ->
                    ScheduledWorkoutCard(
                        item = item,
                        onStartWorkout = { onOpenWorkout(item.entry.workoutId) },
                        onEdit = { openSheet(item) },
                        onRemove = { pendingRemoval = item },
                        onNoteChange = { note ->
                            viewModel.updateReminderNote(item.entry.scheduleId, note)
                        },
                    )
                }
            }
        }

        // Floats above the scrolling list: the schedule content passes
        // underneath, so the cards remain visible around the button instead
        // of it sitting on its own strip of page background.
        AddToScheduleButton(
            onClick = { openSheet(null) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 24.dp, end = 24.dp, bottom = 20.dp),
        )
    }

    if (sheetOpen) {
        ScheduleWorkoutSheet(
            dayOfWeek = sheetEntry?.entry?.dayOfWeek ?: selectedDay,
            targetDateKey = sheetEntry?.entry?.scheduledDateKey ?: selectedDateKey,
            entry = sheetEntry,
            workouts = workouts,
            notificationsEnabled = notificationsEnabled,
            onDismiss = {
                sheetOpen = false
                sheetEntry = null
            },
            onSave = { workoutId, scheduledDateKey, startMinutes, reminderEnabled, minutesBefore, note ->
                viewModel.saveEntry(
                    scheduleId = sheetEntry?.entry?.scheduleId,
                    workoutId = workoutId,
                    dayOfWeek = sheetEntry?.entry?.dayOfWeek ?: selectedDay,
                    scheduledDateKey = scheduledDateKey,
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
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = ScheduleInk,
            )
            Text(
                text = "Weekly routine & reminders",
                modifier = Modifier.padding(top = 2.dp),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = ScheduleInkSecondary,
            )
        }

        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .clip(RoundedCornerShape(50))
                .background(ScheduleBrand50)
                .padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            Text(
                text = "$dayCount ${if (dayCount == 1) "day" else "days"} / week",
                fontSize = 12.sp,
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
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = dateLabel,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = ScheduleInk,
            )
            if (isToday) {
                Text(
                    text = "Today",
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(ScheduleTodayGreenBg)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    fontSize = 12.sp,
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
            modifier = Modifier.padding(top = 2.dp),
            fontSize = 14.sp,
            color = ScheduleInkSecondary,
        )
    }
}

@Composable
private fun EmptyDayCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(ScheduleSurface)
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Rest day",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = ScheduleInk,
        )
        Text(
            text = "No workouts scheduled for this day. Add one to build your weekly routine.",
            modifier = Modifier.padding(top = 6.dp),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = ScheduleInkSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AddToScheduleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(25.dp),
        border = BorderStroke(2.dp, darkerShade(MaterialTheme.colorScheme.primary)),
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Add Workout to Schedule",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
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
