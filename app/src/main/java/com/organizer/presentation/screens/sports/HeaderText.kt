package com.organizer.presentation.screens.sports

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.organizer.R

@Preview
@Composable
fun HeaderText() {
    Text(
        text = stringResource(R.string.home_title),
        fontSize = 30.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.SemiBold,
        color = com.organizer.ui.theme.AppTheme.colors.textPrimary,
    )

    Spacer(modifier = Modifier.height(12.dp))

    Text(
        text = stringResource(R.string.home_subtitle),
        fontSize = 16.sp,
        color = com.organizer.ui.theme.AppTheme.colors.textMuted
    )

    Spacer(modifier = Modifier.height(16.dp))
}
