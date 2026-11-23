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
import androidx.compose.ui.res.stringResource
import com.organizer.presentation.screens.schedule.ScheduleBrand
import com.organizer.R
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
    description: String = "",
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
            text = stringResource(R.string.workout_create_first_title),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = ScheduleInk,
        )
        Text(
            text = description.ifEmpty { stringResource(R.string.workout_create_first_desc_schedule) },
            modifier = Modifier.padding(top = 6.dp),
            fontSize = 14.sp,
            color = ScheduleInkSecondary,
        )
        TextButton(onClick = onOpenWorkouts) {
            Text(
                text = stringResource(R.string.workout_create_first_button),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = ScheduleBrand,
            )
        }
    }
}
