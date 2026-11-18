package com.organizer.presentation.screens.general

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.organizer.presentation.screens.schedule.ScheduleBrand
import com.organizer.presentation.screens.schedule.ScheduleFieldBackground
import com.organizer.presentation.screens.schedule.ScheduleInk
import com.organizer.presentation.screens.schedule.ScheduleInkSecondary

/**
 * Hint shown wherever a workout list is empty: it explains that a workout has
 * to exist first and offers a shortcut to the Workouts page.
 *
 * Shared by the schedule sheet and the exercise card's "Add to Workout" sheet
 * so both empty states are identical.
 */
@Composable
internal fun CreateWorkoutFirstHint(
    onOpenWorkouts: () -> Unit,
    modifier: Modifier = Modifier,
    description: String = "You need at least one workout before you can put it on your schedule.",
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(ScheduleFieldBackground)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Create a workout first",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = ScheduleInk,
        )
        Text(
            text = description,
            modifier = Modifier.padding(top = 6.dp),
            fontSize = 14.sp,
            color = ScheduleInkSecondary,
        )
        TextButton(onClick = onOpenWorkouts) {
            Text(
                text = "Create Workout",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = ScheduleBrand,
            )
        }
    }
}
