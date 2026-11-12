package com.organizer.presentation.screens.workout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.organizer.data.local.db.entities.WorkoutEntity

@Composable
fun WorkoutHeader(
    workout: WorkoutEntity?,
    onBackClick: () -> Unit,
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {

            // Icon for back click
            IconButton(
                onClick = onBackClick
            ) {
                Text(
                    text = "←",
                    fontSize = 28.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            workout?.let {
                Text(
                    text = workout.name,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }
        }
    }
}
