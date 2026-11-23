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
import androidx.compose.runtime.LaunchedEffect
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
import com.organizer.notification.canScheduleExactAlarms
import com.organizer.presentation.model.ScheduledWorkoutItem
import com.organizer.presentation.screens.general.DeleteConfirmationDialog
import com.organizer.presentation.screens.general.LoadingState
import com.organizer.ui.theme.darkerShade
import com.organizer.R
import androidx.compose.ui.res.stringResource

@Composable
fun ScheduleScreen(
    onOpenWorkout: (Long) -> Unit,
    onOpenWorkouts: () -> Unit,
    viewModel: ScheduleViewModel = hiltViewModel(),
) {
    val entriesState by viewModel.entriesUiState.collectAsState()
    val workoutsState by viewModel.workoutsUiState.collectAsState()
    val selectedDay by viewModel.selectedDay.collectAsState()
    val weekOffset by viewModel.weekOffset.collectAsState()
    val completionPercentByDate by viewModel.completionPercentByDate.collectAsState()

    val context = LocalContext.current
    var notificationsEnabled by remember {
        mutableStateOf(notificationsAllowed(context))
    }
    var notificationsDenied by remember {
        mutableStateOf(ReminderPreferences.notificationsDenied(context))
    }
    var exactAlarmsAllowed by remember {
        mutableStateOf(context.canScheduleExactAlarms())
    }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }

    // A save waiting for the notification permission answer. The entry is
    // written only after the dialog closes, so a denial can be stored with the
    // reminder turned off.
    var pendingSave by remember { mutableStateOf<PendingScheduleSave?>(null) }
    var permissionOutcome by remember { mutableStateOf<Boolean?>(null) }
    // True while a pending save waits for the user to return from the app's
    // notification settings (permission granted but notifications turned off).
    var awaitingSettingsSave by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        val granted = notificationsAllowed(context)
        notificationsEnabled = granted
        // A rejection keeps new schedules' reminders off; granting clears it.
        ReminderPreferences.setNotificationsDenied(context, !granted)
        notificationsDenied = !granted
        permissionOutcome = granted
    }

    val lifecycleOwner = context.findLifecycleOwner()
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsEnabled = notificationsAllowed(context)
                if (awaitingSettingsSave) {
                    // Back from the notification settings: finish the save that
                    // was waiting for the user to enable notifications.
                    awaitingSettingsSave = false
                    ReminderPreferences.setNotificationsDenied(context, !notificationsEnabled)
                    notificationsDenied = !notificationsEnabled
                    permissionOutcome = notificationsEnabled
                }
                if (notificationsEnabled && notificationsDenied) {
                    // Granted from system settings, so new schedules default
                    // back to a reminder that is on.
                    ReminderPreferences.setNotificationsDenied(context, false)
                    notificationsDenied = false
                }
                val allowed = context.canScheduleExactAlarms()
                if (allowed != exactAlarmsAllowed) {
                    // Exact-alarm access can only change outside the app, so
                    // recreate the alarms when the user toggles it in settings.
                    viewModel.rescheduleReminders()
                }
                exactAlarmsAllowed = allowed
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(observer)
        }
    }

    fun requestNotifications() {
        when {
            !notificationPermissionGranted(context) -> {
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
        buildWeek(weekOffset, selectedDay, locale = appLocale(context))
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

    fun persistEntry(save: PendingScheduleSave, reminderEnabled: Boolean) {
        viewModel.saveEntry(
            scheduleId = save.scheduleId,
            workoutId = save.workoutId,
            dayOfWeek = save.dayOfWeek,
            scheduledDateKey = save.scheduledDateKey,
            startMinutes = save.startMinutes,
            reminderEnabled = reminderEnabled,
            reminderMinutesBefore = save.reminderMinutesBefore,
            reminderNote = save.reminderNote,
        )
        sheetOpen = false
        sheetEntry = null
    }

    // Completes a save that was waiting for the notification permission. The
    // reminder is kept only when the user allowed notifications.
    LaunchedEffect(permissionOutcome) {
        val granted = permissionOutcome ?: return@LaunchedEffect
        val save = pendingSave ?: return@LaunchedEffect
        pendingSave = null
        permissionOutcome = null
        persistEntry(save, reminderEnabled = granted)
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
                // scroll clear of it. 56 dp button + 8 dp bottom gap plus a
                // small breathing space.
                bottom = 80.dp,
            ),
        ) {
            item {
                ScheduleDayStrip(
                    week = week,
                    selectedDay = selectedDay,
                    dayCount = dayCount,
                    scheduledDays = scheduledDays,
                    completionPercentByDate = completionPercentByDate,
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
                    completionPercent = completionPercentByDate[selectedDateKey],
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
                .padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
        )
    }

    if (sheetOpen) {
        ScheduleWorkoutSheet(
            dayOfWeek = sheetEntry?.entry?.dayOfWeek ?: selectedDay,
            targetDateKey = sheetEntry?.entry?.scheduledDateKey ?: selectedDateKey,
            entry = sheetEntry,
            workouts = workouts,
            notificationsEnabled = notificationsEnabled,
            notificationsDenied = notificationsDenied,
            reminderDefaultOn = notificationsEnabled || !notificationsDenied,
            onDismiss = {
                sheetOpen = false
                sheetEntry = null
            },
            onSave = { workoutId, scheduledDateKey, startMinutes, reminderEnabled, minutesBefore, note ->
                val save = PendingScheduleSave(
                    scheduleId = sheetEntry?.entry?.scheduleId,
                    workoutId = workoutId,
                    dayOfWeek = sheetEntry?.entry?.dayOfWeek ?: selectedDay,
                    scheduledDateKey = scheduledDateKey,
                    startMinutes = startMinutes,
                    reminderMinutesBefore = minutesBefore,
                    reminderNote = note,
                )

                if (reminderEnabled && !notificationsEnabled) {
                    // Ask for notifications only when a reminder is actually
                    // saved. The answer decides whether the entry keeps the
                    // reminder or is stored with it turned off.
                    pendingSave = save
                    permissionOutcome = null
                    if (notificationPermissionGranted(context)) {
                        // The permission exists but notifications are turned
                        // off for the app, so only system settings can fix it.
                        awaitingSettingsSave = true
                        if (!openNotificationSettings(context)) {
                            awaitingSettingsSave = false
                            persistEntry(save, reminderEnabled = false)
                        }
                    } else {
                        permissionRequested = true
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                } else {
                    persistEntry(save, reminderEnabled)
                }
            },
            onOpenWorkouts = {
                sheetOpen = false
                sheetEntry = null
                onOpenWorkouts()
            },
            onRequestNotifications = ::requestNotifications,
        )
    }

    pendingRemoval?.let { item ->
        DeleteConfirmationDialog(
            title = stringResource(R.string.schedule_remove_title),
            itemName = item.workoutName,
            location = stringResource(R.string.schedule_weekly_location),
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
private fun SelectedDayHeader(
    dateLabel: String,
    isToday: Boolean,
    workoutCount: Int,
    completionPercent: Int?,
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
                    text = stringResource(R.string.schedule_today),
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
                0 -> stringResource(R.string.schedule_rest_day_none)
                1 -> stringResource(R.string.schedule_one_workout)
                else -> stringResource(R.string.schedule_n_workouts, workoutCount)
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
            text = stringResource(R.string.schedule_rest_day),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = ScheduleInk,
        )
        Text(
            text = stringResource(R.string.schedule_no_workouts_day),
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
            text = stringResource(R.string.schedule_add_workout),
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

private fun notificationsAllowed(context: Context): Boolean =
    notificationPermissionGranted(context) &&
        NotificationManagerCompat.from(context).areNotificationsEnabled()

private fun notificationPermissionGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED


private fun openNotificationSettings(context: Context): Boolean {
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

    return runCatching { context.startActivity(intent) }.isSuccess
}

/**
 * Everything needed to write a schedule entry, held while the app waits for
 * the user to answer the notification permission prompt.
 */
private data class PendingScheduleSave(
    val scheduleId: Long?,
    val workoutId: Long,
    val dayOfWeek: Int,
    val scheduledDateKey: Int?,
    val startMinutes: Int,
    val reminderMinutesBefore: Int,
    val reminderNote: String,
)
