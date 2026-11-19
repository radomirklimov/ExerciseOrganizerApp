package com.organizer.presentation.screens.general

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.organizer.ui.theme.AppTheme
import java.io.File

/**
 * Round user picture used on the home header and on the profile screen.
 *
 * Falls back to a soft violet gradient with the user's initials while no
 * avatar has been chosen (or when the saved file no longer exists).
 */
@Composable
fun ProfileAvatar(
    avatarPath: String?,
    initials: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val colors = AppTheme.colors
    val file = avatarPath?.let { File(it) }?.takeIf { it.exists() }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(colors.primary, colors.primaryDark)
                )
            )
            .border(1.dp, colors.border, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (file != null) {
            AsyncImage(
                model = file,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = initials,
                color = Color.White,
                fontSize = (size.value * 0.36f).sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Helper for screens that only have a content [Uri] and want a stable path. */
fun avatarFile(context: android.content.Context): File =
    File(context.filesDir, "profile/avatar.jpg")
