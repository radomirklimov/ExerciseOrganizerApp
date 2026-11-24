package com.organizer.presentation.screens.sports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.organizer.R
import androidx.compose.ui.res.stringResource

@Composable
fun LoadingErrorView(
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when {
            isLoading -> {
                CircularProgressIndicator()

                Text(
                    text = stringResource(R.string.common_loading),
                    color = com.organizer.ui.theme.AppTheme.colors.textMuted,
                    fontSize = 18.sp
                )
            }

            errorMessage != null -> {
                Text(
                    text = errorMessage,
                    color = com.organizer.ui.theme.AppTheme.colors.textMuted,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )

                Button(
                    onClick = onRetry
                ) {
                    Text(stringResource(R.string.common_retry))
                }
            }

            else -> {
                Text(
                    text = stringResource(R.string.common_no_data),
                    color = com.organizer.ui.theme.AppTheme.colors.textMuted,
                    fontSize = 18.sp
                )

                Button(
                    onClick = onRetry
                ) {
                    Text(stringResource(R.string.common_load_data))
                }
            }
        }
    }
}
