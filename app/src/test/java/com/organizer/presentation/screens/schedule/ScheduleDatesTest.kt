package com.organizer.presentation.screens.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class ScheduleDatesTest {

    private val utc: TimeZone = TimeZone.getTimeZone("UTC")
    private val monday16November2026 =
        Calendar.getInstance(utc).apply {
            clear()
            set(2026, Calendar.NOVEMBER, 16, 10, 0, 0)
        }.timeInMillis

    @Test
    fun `current week starts on Monday`() {
        val week = buildWeek(
            weekOffset = 0,
            selectedIsoDay = 1,
            nowMillis = monday16November2026,
            timeZone = utc,
            locale = Locale.US,
        )

        assertEquals(listOf(16, 17, 18, 19, 20, 21, 22), week.days.map { it.dayOfMonth })
        assertEquals(
            listOf(20261116, 20261117, 20261118, 20261119, 20261120, 20261121, 20261122),
            week.days.map { it.dateKey },
        )
        assertEquals("November 2026", week.monthLabel)
        assertEquals("Monday, 16 Nov", week.selectedDateLabel)
        assertTrue(week.days.first().isToday)
    }

    @Test
    fun `next week shows the same recurring days with new dates`() {
        val week = buildWeek(
            weekOffset = 1,
            selectedIsoDay = 1,
            nowMillis = monday16November2026,
            timeZone = utc,
            locale = Locale.US,
        )

        assertEquals(listOf(23, 24, 25, 26, 27, 28, 29), week.days.map { it.dayOfMonth })
        assertEquals("Monday, 23 Nov", week.selectedDateLabel)
        assertTrue(week.days.none { it.isToday })
    }

    @Test
    fun `previous week is available too`() {
        val week = buildWeek(
            weekOffset = -1,
            selectedIsoDay = 3,
            nowMillis = monday16November2026,
            timeZone = utc,
            locale = Locale.US,
        )

        assertEquals(listOf(9, 10, 11, 12, 13, 14, 15), week.days.map { it.dayOfMonth })
        assertEquals("Wednesday, 11 Nov", week.selectedDateLabel)
    }

    @Test
    fun `week crossing a month shows both month names`() {
        val lateNovember = Calendar.getInstance(utc).apply {
            clear()
            set(2026, Calendar.NOVEMBER, 30, 10, 0, 0)
        }.timeInMillis

        val week = buildWeek(
            weekOffset = 0,
            selectedIsoDay = 1,
            nowMillis = lateNovember,
            timeZone = utc,
            locale = Locale.US,
        )

        assertEquals("Nov – Dec 2026", week.monthLabel)
    }
}
