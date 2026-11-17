package com.organizer.notification

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Text shown by a workout reminder notification. */
data class ReminderText(
    val date: String,
    val time: String,
    val shortText: String,
    val longText: String,
)

object ReminderTextFormatter {

    fun format(
        workoutStartMillis: Long,
        workoutName: String,
        note: String,
        timeZone: TimeZone = TimeZone.getDefault(),
        locale: Locale = Locale.getDefault(),
    ): ReminderText {
        val dateFormat = SimpleDateFormat("EEEE, d MMMM", locale).apply {
            this.timeZone = timeZone
        }
        val timeFormat = SimpleDateFormat("HH:mm", locale).apply {
            this.timeZone = timeZone
        }

        val date = dateFormat.format(Date(workoutStartMillis))
        val time = timeFormat.format(Date(workoutStartMillis))
        val cleanNote = note.trim()

        val longText = buildString {
            append("Date: ").append(date).append('\n')
            append("Time: ").append(time).append('\n')
            append("Workout: ").append(workoutName)
            if (cleanNote.isNotEmpty()) {
                append('\n').append("Note: ").append(cleanNote)
            }
        }

        return ReminderText(
            date = date,
            time = time,
            shortText = "$date · $time · $workoutName",
            longText = longText,
        )
    }
}
