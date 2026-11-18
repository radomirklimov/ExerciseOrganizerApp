package com.organizer.notification

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Text shown by a workout reminder notification. The workout name is used as
 * the notification title, so the body only carries the time and the note.
 */
data class ReminderText(
    val time: String,
    val shortText: String,
    val longText: String,
)

object ReminderTextFormatter {

    fun format(
        workoutStartMillis: Long,
        note: String,
        timeZone: TimeZone = TimeZone.getDefault(),
        locale: Locale = Locale.getDefault(),
    ): ReminderText {
        val timeFormat = SimpleDateFormat("HH:mm", locale).apply {
            this.timeZone = timeZone
        }

        val time = timeFormat.format(Date(workoutStartMillis))
        val cleanNote = note.trim()

        val longText = buildString {
            append("Time: ").append(time)
            if (cleanNote.isNotEmpty()) {
                append('\n').append("Note: ").append(cleanNote)
            }
        }

        return ReminderText(
            time = time,
            shortText = if (cleanNote.isEmpty()) {
                "Time: $time"
            } else {
                "Time: $time · $cleanNote"
            },
            longText = longText,
        )
    }
}
