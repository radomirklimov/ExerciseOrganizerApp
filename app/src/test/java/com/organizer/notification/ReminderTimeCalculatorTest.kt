package com.organizer.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class ReminderTimeCalculatorTest {

    private val utc: TimeZone = TimeZone.getTimeZone("UTC")

    @Test
    fun `uses today when the reminder is still ahead`() {
        // Monday 16 November 2026, 10:00. Workout at 18:00, one hour before.
        val occurrence = ReminderTimeCalculator.nextOccurrence(
            nowMillis = utcMillis(2026, Calendar.NOVEMBER, 16, 10, 0),
            isoDayOfWeek = 1,
            startMinutes = 18 * 60,
            minutesBefore = 60,
            timeZone = utc,
        )

        assertEquals(utcMillis(2026, Calendar.NOVEMBER, 16, 17, 0), occurrence.triggerAtMillis)
        assertEquals(utcMillis(2026, Calendar.NOVEMBER, 16, 18, 0), occurrence.workoutStartMillis)
    }

    @Test
    fun `moves to the next week once the reminder has passed`() {
        // Monday 18:30, after the 17:00 reminder.
        val occurrence = ReminderTimeCalculator.nextOccurrence(
            nowMillis = utcMillis(2026, Calendar.NOVEMBER, 16, 18, 30),
            isoDayOfWeek = 1,
            startMinutes = 18 * 60,
            minutesBefore = 60,
            timeZone = utc,
        )

        assertEquals(utcMillis(2026, Calendar.NOVEMBER, 23, 17, 0), occurrence.triggerAtMillis)
        assertEquals(utcMillis(2026, Calendar.NOVEMBER, 23, 18, 0), occurrence.workoutStartMillis)
    }

    @Test
    fun `early workout reminder lands on the previous evening`() {
        // Tuesday 00:30 workout with a one hour lead time.
        val occurrence = ReminderTimeCalculator.nextOccurrence(
            nowMillis = utcMillis(2026, Calendar.NOVEMBER, 16, 10, 0),
            isoDayOfWeek = 2,
            startMinutes = 30,
            minutesBefore = 60,
            timeZone = utc,
        )

        assertEquals(utcMillis(2026, Calendar.NOVEMBER, 16, 23, 30), occurrence.triggerAtMillis)
        assertEquals(utcMillis(2026, Calendar.NOVEMBER, 17, 0, 30), occurrence.workoutStartMillis)
    }

    @Test
    fun `reminder at exactly now is skipped until next week`() {
        val occurrence = ReminderTimeCalculator.nextOccurrence(
            nowMillis = utcMillis(2026, Calendar.NOVEMBER, 16, 17, 0),
            isoDayOfWeek = 1,
            startMinutes = 18 * 60,
            minutesBefore = 60,
            timeZone = utc,
        )

        assertEquals(utcMillis(2026, Calendar.NOVEMBER, 23, 17, 0), occurrence.triggerAtMillis)
        assertEquals(utcMillis(2026, Calendar.NOVEMBER, 23, 18, 0), occurrence.workoutStartMillis)
    }

    @Test
    fun `one-time reminder fires on its own date`() {
        val occurrence = ReminderTimeCalculator.occurrenceOnDate(
            nowMillis = utcMillis(2026, Calendar.NOVEMBER, 16, 10, 0),
            dateKey = 20261120,
            startMinutes = 18 * 60,
            minutesBefore = 60,
            timeZone = utc,
        )

        assertNotNull(occurrence)
        assertEquals(utcMillis(2026, Calendar.NOVEMBER, 20, 17, 0), occurrence!!.triggerAtMillis)
        assertEquals(utcMillis(2026, Calendar.NOVEMBER, 20, 18, 0), occurrence.workoutStartMillis)
    }

    @Test
    fun `one-time early workout reminder may land on the previous day`() {
        val occurrence = ReminderTimeCalculator.occurrenceOnDate(
            nowMillis = utcMillis(2026, Calendar.NOVEMBER, 16, 10, 0),
            dateKey = 20261117,
            startMinutes = 30,
            minutesBefore = 60,
            timeZone = utc,
        )

        assertNotNull(occurrence)
        assertEquals(utcMillis(2026, Calendar.NOVEMBER, 16, 23, 30), occurrence!!.triggerAtMillis)
    }

    @Test
    fun `one-time reminder in the past is not scheduled`() {
        val occurrence = ReminderTimeCalculator.occurrenceOnDate(
            nowMillis = utcMillis(2026, Calendar.NOVEMBER, 21, 10, 0),
            dateKey = 20261120,
            startMinutes = 18 * 60,
            minutesBefore = 60,
            timeZone = utc,
        )

        assertNull(occurrence)
    }

    @Test
    fun `notification text contains time and note`() {
        val text = ReminderTextFormatter.format(
            workoutStartMillis = utcMillis(2026, Calendar.NOVEMBER, 23, 18, 0),
            note = "Don't forget to bring my lifting belt!",
            timeZone = utc,
            locale = Locale.ENGLISH,
        )

        assertEquals("18:00", text.time)
        assertEquals(
            "18:00 · Don't forget to bring my lifting belt!",
            text.shortText,
        )
        assertEquals(
            "18:00\nDon't forget to bring my lifting belt!",
            text.longText,
        )
        assertFalse(text.longText.contains("Date:"))
        assertFalse(text.longText.contains("Workout:"))
        assertFalse(text.longText.contains("Time:"))
        assertFalse(text.longText.contains("Note:"))
    }

    @Test
    fun `blank note is omitted from the notification text`() {
        val text = ReminderTextFormatter.format(
            workoutStartMillis = utcMillis(2026, Calendar.NOVEMBER, 23, 18, 0),
            note = "   ",
            timeZone = utc,
            locale = Locale.ENGLISH,
        )

        assertFalse(text.longText.contains("Note:"))
        assertEquals("18:00", text.shortText)
        assertEquals("18:00", text.longText)
    }

    private fun utcMillis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): Long = Calendar.getInstance(utc).apply {
        clear()
        set(year, month, day, hour, minute, 0)
    }.timeInMillis
}
