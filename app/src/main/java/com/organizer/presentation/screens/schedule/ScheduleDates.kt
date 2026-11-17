package com.organizer.presentation.screens.schedule

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

internal data class ScheduleWeekDay(
    /** ISO day number: Monday = 1 ... Sunday = 7. */
    val isoDay: Int,
    val letter: String,
    val dayOfMonth: Int,
    val isToday: Boolean,
)

internal data class ScheduleWeek(
    val days: List<ScheduleWeekDay>,
    val monthLabel: String,
    val selectedDateLabel: String,
)

private val DayLetters = listOf("M", "T", "W", "T", "F", "S", "S")
private val DayNames = listOf(
    "Monday",
    "Tuesday",
    "Wednesday",
    "Thursday",
    "Friday",
    "Saturday",
    "Sunday",
)
private val DayShortNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val MonthShortNames = arrayOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)
private val MonthLongNames = arrayOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

internal fun currentIsoDay(
    nowMillis: Long = System.currentTimeMillis(),
    timeZone: TimeZone = TimeZone.getDefault(),
): Int {
    val calendarDay = Calendar.getInstance(timeZone).apply {
        timeInMillis = nowMillis
    }.get(Calendar.DAY_OF_WEEK)
    return if (calendarDay == Calendar.SUNDAY) 7 else calendarDay - 1
}

internal fun isoDayName(isoDay: Int): String = DayNames[isoDay - 1]

internal fun isoDayShortName(isoDay: Int): String = DayShortNames[isoDay - 1]

internal fun formatMinutes(minutes: Int): String =
    String.format(Locale.getDefault(), "%02d:%02d", minutes / 60, minutes % 60)

/**
 * Builds the seven dates shown for [weekOffset] (0 = the current week).
 *
 * The schedule itself is weekly, so changing the week only changes the dates
 * under the strip; the same recurring entries stay visible.
 */
internal fun buildWeek(
    weekOffset: Int,
    selectedIsoDay: Int,
    nowMillis: Long = System.currentTimeMillis(),
    timeZone: TimeZone = TimeZone.getDefault(),
): ScheduleWeek {
    val weekStart = Calendar.getInstance(timeZone).apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(
            Calendar.DAY_OF_MONTH,
            1 - currentIsoDay(nowMillis, timeZone) + weekOffset * 7,
        )
    }

    val todayStart = Calendar.getInstance(timeZone).apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    val days = (1..7).map { isoDay ->
        val day = (weekStart.clone() as Calendar).apply {
            add(Calendar.DAY_OF_MONTH, isoDay - 1)
        }
        ScheduleWeekDay(
            isoDay = isoDay,
            letter = DayLetters[isoDay - 1],
            dayOfMonth = day.get(Calendar.DAY_OF_MONTH),
            isToday = day.timeInMillis == todayStart.timeInMillis,
        )
    }

    val weekEnd = (weekStart.clone() as Calendar).apply {
        add(Calendar.DAY_OF_MONTH, 6)
    }
    val selectedDate = (weekStart.clone() as Calendar).apply {
        add(Calendar.DAY_OF_MONTH, selectedIsoDay - 1)
    }
    val selectedDateLabel = buildString {
        append(isoDayName(selectedIsoDay))
        append(", ")
        append(selectedDate.get(Calendar.DAY_OF_MONTH))
        append(' ')
        append(MonthShortNames[selectedDate.get(Calendar.MONTH)])
    }

    return ScheduleWeek(
        days = days,
        monthLabel = monthRangeLabel(weekStart, weekEnd),
        selectedDateLabel = selectedDateLabel,
    )
}

private fun monthRangeLabel(start: Calendar, end: Calendar): String {
    val startMonth = start.get(Calendar.MONTH)
    val endMonth = end.get(Calendar.MONTH)
    val startYear = start.get(Calendar.YEAR)
    val endYear = end.get(Calendar.YEAR)

    return when {
        startYear == endYear && startMonth == endMonth ->
            "${MonthLongNames[startMonth]} $startYear"
        startYear == endYear ->
            "${MonthShortNames[startMonth]} – ${MonthShortNames[endMonth]} $startYear"
        else ->
            "${MonthShortNames[startMonth]} $startYear – " +
                "${MonthShortNames[endMonth]} $endYear"
    }
}
