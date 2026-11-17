package com.organizer

import android.app.Application
import com.organizer.data.repo.ScheduleRepository
import com.organizer.notification.WorkoutReminderNotifier
import com.organizer.notification.WorkoutReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class OrganizerApplication : Application() {

    @Inject
    lateinit var scheduleRepository: ScheduleRepository

    @Inject
    lateinit var reminderScheduler: WorkoutReminderScheduler

    @Inject
    lateinit var reminderNotifier: WorkoutReminderNotifier

    override fun onCreate() {
        super.onCreate()
        reminderNotifier.ensureChannel()

        // Restore weekly alarms after ordinary app starts as well. This also
        // covers entries added before a crash or a process kill.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            reminderScheduler.rescheduleAll(scheduleRepository.getSchedule())
        }
    }
}
