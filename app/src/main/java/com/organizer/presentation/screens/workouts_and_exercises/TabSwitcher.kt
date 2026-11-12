package com.organizer.presentation.screens.workouts_and_exercises

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.organizer.presentation.OrganizerViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

@Composable
fun TabSwitcher(
    viewModel: OrganizerViewModel = hiltViewModel(),
) {
    val selectedTab by viewModel.selectedTab.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFFF3EEEE))
            .padding(6.dp)
    ) {

        TabButton(
            title = "My Workouts",
            selected = selectedTab == CustomsTab.WORKOUTS,
            modifier = Modifier.weight(1f),
            onClick = { viewModel.selectedTab.value = CustomsTab.WORKOUTS }
        )

        TabButton(
            title = "My Exercises",
            selected = selectedTab == CustomsTab.EXERCISES,
            modifier = Modifier.weight(1f),
            onClick = { viewModel.selectedTab.value = CustomsTab.EXERCISES }
        )
    }
}
