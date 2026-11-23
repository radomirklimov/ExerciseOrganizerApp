package com.organizer.presentation.screens.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.organizer.R

internal val ReminderMinuteOptions = listOf(15, 30, 60)

@Composable
internal fun reminderTimingLabel(minutes: Int): String = when {
    minutes == 60 -> stringResource(R.string.schedule_reminder_1_hour_before)
    minutes > 60 && minutes % 60 == 0 -> stringResource(R.string.schedule_reminder_hours_before, minutes / 60)
    else -> stringResource(R.string.schedule_reminder_min_before, minutes)
}

@Composable
internal fun reminderTimingChipLabel(minutes: Int): String = when {
    minutes == 60 -> stringResource(R.string.schedule_reminder_1_hour)
    minutes > 60 && minutes % 60 == 0 -> stringResource(R.string.schedule_reminder_hours, minutes / 60)
    else -> stringResource(R.string.schedule_reminder_min, minutes)
}

/** 15 min / 30 min / 1 hour selector shared by the card and the sheet. */
@Composable
internal fun ReminderTimingPills(
    selectedMinutes: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ReminderMinuteOptions.forEach { minutes ->
            val selected = minutes == selectedMinutes
            val shape = RoundedCornerShape(12.dp)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(
                        if (selected) ScheduleBrand50 else ScheduleFieldBackground
                    )
                    .border(
                        width = 1.dp,
                        color = if (selected) ScheduleBrand else ScheduleFieldBorder,
                        shape = shape,
                    )
                    .clickable(enabled = enabled) { onSelect(minutes) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = reminderTimingChipLabel(minutes),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        !enabled -> ScheduleInkMuted
                        selected -> ScheduleBrand
                        else -> ScheduleInkSecondary
                    },
                )
            }
        }
    }
}
