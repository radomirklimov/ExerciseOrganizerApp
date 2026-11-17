package com.organizer.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `notification text contains date time workout and note`() {
        val text = ReminderTextFormatter.format(
            workoutStartMillis = utcMillis(2026, Calendar.NOVEMBER, 23, 18, 0),
            workoutName = "Chest Day",
            note = "Don't forget to bring my lifting belt!",
            timeZone = utc,
            locale = Locale.ENGLISH,
        )

        assertEquals("Monday, 23 November", text.date)
        assertEquals("18:00", text.time)
        assertTrue(text.longText.contains("Date: Monday, 23 November"))
        assertTrue(text.longText.contains("Time: 18:00"))
        assertTrue(text.longText.contains("Workout: Chest Day"))
        assertTrue(text.longText.contains("Note: Don't forget to bring my lifting belt!"))
    }

    @Test
    fun `blank note is omitted from the notification text`() {
        val text = ReminderTextFormatter.format(
            workoutStartMillis = utcMillis(2026, Calendar.NOVEMBER, 23, 18, 0),
            workoutName = "Chest Day",
            note = "   ",
            timeZone = utc,
            locale = Locale.ENGLISH,
        )

        assertFalse(text.longText.contains("Note:"))
        assertEquals("Monday, 23 November · 18:00 · Chest Day", text.shortText)
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
