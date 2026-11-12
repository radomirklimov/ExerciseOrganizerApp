package com.organizer.presentation.screens.welcome

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun OnBoardNavButton(
    modifier: Modifier = Modifier,
    currentPage: Int,
    noOfPages: Int,
    onNextClicked: () -> Unit
) {

    Button(
        onClick = onNextClicked,
        modifier = modifier.padding(bottom = 16.dp)
    ) {
        Text(
            text = if (currentPage < noOfPages - 1)
                "Next"
            else
                "Start Building"
        )
    }
}
