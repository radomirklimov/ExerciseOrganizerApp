package com.organizer.presentation.screens.schedule

import android.content.Context
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.text.DateFormatSymbols

internal data class ScheduleWeekDay(
    /** ISO day number: Monday = 1 ... Sunday = 7. */
    val isoDay: Int,
    val letter: String,
    val dayOfMonth: Int,
    val isToday: Boolean,
    /** Calendar date as `yyyyMMdd`; identifies one-time schedule entries. */
    val dateKey: Int,
)

internal data class ScheduleWeek(
    val days: List<ScheduleWeekDay>,
    val monthLabel: String,
    val selectedDateLabel: String,
)

// ISO day number: Monday = 1 ... Sunday = 7. Calendar.DAY_OF_WEEK: Sunday = 1 ... Saturday = 7.
private fun calendarDayForIso(isoDay: Int): Int = if (isoDay == 7) Calendar.SUNDAY else isoDay + 1

private fun dayNames(locale: Locale): List<String> =
    (1..7).map { iso -> DateFormatSymbols.getInstance(locale).weekdays[calendarDayForIso(iso)] }

private fun dayShortNames(locale: Locale): List<String> =
    (1..7).map { iso -> DateFormatSymbols.getInstance(locale).shortWeekdays[calendarDayForIso(iso)] }

private fun monthShortNames(locale: Locale): Array<String> = DateFormatSymbols.getInstance(locale).shortMonths

private fun monthLongNames(locale: Locale): Array<String> = DateFormatSymbols.getInstance(locale).months

internal fun currentIsoDay(
    nowMillis: Long = System.currentTimeMillis(),
    timeZone: TimeZone = TimeZone.getDefault(),
    locale: Locale = Locale.getDefault(),
): Int {
    val calendarDay = Calendar.getInstance(timeZone).apply {
        timeInMillis = nowMillis
    }.get(Calendar.DAY_OF_WEEK)
    return if (calendarDay == Calendar.SUNDAY) 7 else calendarDay - 1
}

internal fun isoDayName(isoDay: Int, locale: Locale = Locale.getDefault()): String = dayNames(locale)[isoDay - 1]

internal fun isoDayShortName(isoDay: Int, locale: Locale = Locale.getDefault()): String = dayShortNames(locale)[isoDay - 1]

internal fun formatMinutes(minutes: Int, locale: Locale = Locale.getDefault()): String =
    String.format(locale, "%02d:%02d", minutes / 60, minutes % 60)

/** Compact local date (`yyyyMMdd`) used to target one-time schedule entries. */
internal fun calendarDateKey(calendar: Calendar): Int =
    calendar.get(Calendar.YEAR) * 10_000 +
        (calendar.get(Calendar.MONTH) + 1) * 100 +
        calendar.get(Calendar.DAY_OF_MONTH)

/** Locale of the app as configured through the profile screen. */
internal fun appLocale(context: Context): Locale =
    context.resources.configuration.locales[0] ?: Locale.getDefault()

/** Short label for a compact date key, e.g. `16 Nov`. */
internal fun formatDateKeyShort(dateKey: Int, locale: Locale = Locale.getDefault()): String {
    val month = (dateKey / 100) % 100 - 1
    val dayOfMonth = dateKey % 100
    return "$dayOfMonth ${monthShortNames(locale)[month]}"
}

/**
 * Builds the seven dates shown for [weekOffset] (0 = the current week).
 *
 * The schedule itself is weekly, so changing the week only changes the dates
 * under the strip; recurring entries stay visible every week, while one-time
 * entries only appear in the week that contains their date.
 */
internal fun buildWeek(
    weekOffset: Int,
    selectedIsoDay: Int,
    nowMillis: Long = System.currentTimeMillis(),
    timeZone: TimeZone = TimeZone.getDefault(),
    locale: Locale = Locale.getDefault(),
): ScheduleWeek {
    val weekStart = Calendar.getInstance(timeZone).apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(
            Calendar.DAY_OF_MONTH,
            1 - currentIsoDay(nowMillis, timeZone, locale) + weekOffset * 7,
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
            letter = dayShortNames(locale)[isoDay - 1].take(1).uppercase(),
            dayOfMonth = day.get(Calendar.DAY_OF_MONTH),
            isToday = day.timeInMillis == todayStart.timeInMillis,
            dateKey = calendarDateKey(day),
        )
    }

    val weekEnd = (weekStart.clone() as Calendar).apply {
        add(Calendar.DAY_OF_MONTH, 6)
    }
    val selectedDate = (weekStart.clone() as Calendar).apply {
        add(Calendar.DAY_OF_MONTH, selectedIsoDay - 1)
    }
    val selectedDateLabel = buildString {
        append(isoDayName(selectedIsoDay, locale))
        append(", ")
        append(selectedDate.get(Calendar.DAY_OF_MONTH))
        append(' ')
        append(monthShortNames(locale)[selectedDate.get(Calendar.MONTH)])
    }

    return ScheduleWeek(
        days = days,
        monthLabel = monthRangeLabel(weekStart, weekEnd, locale),
        selectedDateLabel = selectedDateLabel,
    )
}

private fun monthRangeLabel(start: Calendar, end: Calendar, locale: Locale): String {
    val startMonth = start.get(Calendar.MONTH)
    val endMonth = end.get(Calendar.MONTH)
    val startYear = start.get(Calendar.YEAR)
    val endYear = end.get(Calendar.YEAR)

    return when {
        startYear == endYear && startMonth == endMonth ->
            "${monthLongNames(locale)[startMonth]} $startYear"
        startYear == endYear ->
            "${monthShortNames(locale)[startMonth]} – ${monthShortNames(locale)[endMonth]} $startYear"
        else ->
            "${monthShortNames(locale)[startMonth]} $startYear – " +
                "${monthShortNames(locale)[endMonth]} $endYear"
    }
}
