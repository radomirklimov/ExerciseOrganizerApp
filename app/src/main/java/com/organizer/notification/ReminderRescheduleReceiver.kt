package com.organizer.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.organizer.data.repo.ScheduleRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Recreates pending weekly alarms after a reboot, an app update, or a clock /
 * timezone change. Alarms do not survive these events on their own.
 */
@AndroidEntryPoint
class ReminderRescheduleReceiver : BroadcastReceiver() {

    @Inject
    lateinit var scheduleRepository: ScheduleRepository

    @Inject
    lateinit var reminderScheduler: WorkoutReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                reminderScheduler.rescheduleAll(scheduleRepository.getSchedule())
            } finally {
                pendingResult.finish()
            }
        }
    }
}
