package com.organizer

import android.app.Application
import androidx.work.Configuration
import com.organizer.auth.AuthRepository
import com.organizer.auth.UserState
import com.organizer.data.repo.ScheduleRepository
import com.organizer.notification.WorkoutReminderNotifier
import com.organizer.notification.WorkoutReminderScheduler
import com.organizer.notification.WeeklyEngagementNotifications
import com.organizer.sync.OutboxSyncObserver
import com.organizer.sync.SyncScheduler
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class OrganizerApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var scheduleRepository: ScheduleRepository

    @Inject
    lateinit var reminderScheduler: WorkoutReminderScheduler

    @Inject
    lateinit var reminderNotifier: WorkoutReminderNotifier

    @Inject
    lateinit var workerFactory: androidx.hilt.work.HiltWorkerFactory

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var syncScheduler: SyncScheduler

    @Inject
    lateinit var outboxSyncObserver: OutboxSyncObserver

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        reminderNotifier.ensureChannel()
        WeeklyEngagementNotifications.ensureChannel(this)
        WeeklyEngagementNotifications.scheduleNext(this)

        // Restore weekly alarms after ordinary app starts as well. This also
        // covers entries added before a crash or a process kill.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            reminderScheduler.rescheduleAll(scheduleRepository.getSchedule())
        }

        // Automatic sync: periodic while installed, plus one immediate run
        // whenever an authenticated account (or a different account) resolves.
        syncScheduler.schedulePeriodic()
        outboxSyncObserver.start(CoroutineScope(SupervisorJob() + Dispatchers.IO))
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            authRepository.state
                .filterIsInstance<UserState.Authenticated>()
                .map { it.firebaseUid }
                .distinctUntilChanged()
                .collect { syncScheduler.enqueueImmediate() }
        }
    }
}
