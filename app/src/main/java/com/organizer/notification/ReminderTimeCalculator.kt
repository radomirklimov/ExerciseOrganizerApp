package com.organizer.notification

import java.util.Calendar
import java.util.TimeZone

/**
 * Computes the next weekly reminder time for one schedule entry.
 *
 * The reminder is derived from the workout start time minus
 * [reminderMinutesBefore]. Minutes are subtracted through [Calendar], so a
 * reminder for an early-morning workout correctly lands on the previous day.
 */
object ReminderTimeCalculator {

    data class Occurrence(
        /** When the alarm should fire. */
        val triggerAtMillis: Long,
        /** Start of the workout this reminder belongs to. */
        val workoutStartMillis: Long,
    )

    /**
     * @param isoDayOfWeek Monday = 1 ... Sunday = 7.
     * @param startMinutes Workout start time as minutes since midnight.
     * @param minutesBefore Reminder lead time in minutes.
     */
    fun nextOccurrence(
        nowMillis: Long,
        isoDayOfWeek: Int,
        startMinutes: Int,
        minutesBefore: Int,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Occurrence {
        val start = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, startMinutes / 60)
            set(Calendar.MINUTE, startMinutes % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(
                Calendar.DAY_OF_MONTH,
                daysUntil(targetIsoDay = isoDayOfWeek, currentCalendarDay = get(Calendar.DAY_OF_WEEK)),
            )
        }

        val reminder = (start.clone() as Calendar).apply {
            add(Calendar.MINUTE, -minutesBefore)
        }

        // The reminder for this week may already have passed. Keep whole weeks
        // so the workout date and the reminder stay in the same occurrence.
        while (reminder.timeInMillis <= nowMillis) {
            start.add(Calendar.DAY_OF_MONTH, 7)
            reminder.add(Calendar.DAY_OF_MONTH, 7)
        }

        return Occurrence(
            triggerAtMillis = reminder.timeInMillis,
            workoutStartMillis = start.timeInMillis,
        )
    }

    private fun daysUntil(targetIsoDay: Int, currentCalendarDay: Int): Int {
        val delta = targetIsoDay - toIsoDay(currentCalendarDay)
        return if (delta < 0) delta + 7 else delta
    }

    private fun toIsoDay(calendarDay: Int): Int =
        if (calendarDay == Calendar.SUNDAY) 7 else calendarDay - 1
}
