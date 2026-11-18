package com.organizer.presentation.screens.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The seven-day selector. Selected days are solid purple, days with recurring
 * workouts carry a purple dot, and rest days use the dashed outline from the
 * design.
 */
@Composable
internal fun ScheduleDayStrip(
    week: ScheduleWeek,
    selectedDay: Int,
    scheduledDays: Set<Int>,
    onSelectDay: (Int) -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "SELECT DAY",
                modifier = Modifier.weight(1f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
                color = ScheduleInkMuted,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                WeekArrow(
                    contentDescription = "Previous week",
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    onClick = onPreviousWeek,
                )
                Text(
                    text = week.monthLabel,
                    modifier = Modifier.padding(horizontal = 2.dp),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = ScheduleBrand,
                )
                WeekArrow(
                    contentDescription = "Next week",
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    onClick = onNextWeek,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            week.days.forEach { day ->
                DayPill(
                    day = day,
                    selected = day.isoDay == selectedDay,
                    hasWorkout = day.isoDay in scheduledDays,
                    onClick = { onSelectDay(day.isoDay) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun WeekArrow(
    contentDescription: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(32.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = ScheduleBrand,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun DayPill(
    day: ScheduleWeekDay,
    selected: Boolean,
    hasWorkout: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    val isRestDay = !hasWorkout

    val background = when {
        selected -> ScheduleBrand
        isRestDay -> ScheduleRestDayBackground
        else -> ScheduleDayBackground
    }

    val borderModifier = when {
        selected -> Modifier
        isRestDay -> Modifier.drawBehind {
            // Inset by half the stroke so the clipped pill edge does not hide
            // half of the dashed outline.
            val stroke = 1.dp.toPx()
            drawRoundRect(
                color = ScheduleRestDayBorder,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius(18.dp.toPx() - stroke / 2),
                style = Stroke(
                    width = stroke,
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(5.dp.toPx(), 4.dp.toPx())
                    ),
                ),
            )
        }
        else -> Modifier
    }

    Column(
        modifier = modifier
            .clip(shape)
            .background(background)
            .then(borderModifier)
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = day.letter,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = when {
                selected -> ScheduleSurface.copy(alpha = 0.8f)
                isRestDay -> ScheduleInkMuted
                else -> ScheduleInkMuted
            },
        )

        Text(
            text = day.dayOfMonth.toString(),
            modifier = Modifier.padding(top = 1.dp),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = when {
                selected -> ScheduleSurface
                isRestDay -> ScheduleInkMuted
                else -> ScheduleInk
            },
        )

        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(7.dp)
                .clip(CircleShape)
                .background(
                    when {
                        selected -> ScheduleSurface
                        hasWorkout -> ScheduleBrand
                        else -> androidx.compose.ui.graphics.Color.Transparent
                    }
                ),
        )
    }
}
