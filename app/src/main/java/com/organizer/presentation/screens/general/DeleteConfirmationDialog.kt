package com.organizer.presentation.screens.general

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.organizer.ui.theme.AppTheme
import com.organizer.ui.theme.themedColor

private val Danger: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFFB3261E), Color(0xFFC6342B))
private val DangerSoft: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFFF9DEDC), AppTheme.colors.dangerSoft)
private val DangerBorder: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFFF2B8B5), AppTheme.colors.dangerBorder)
private val Ink: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFF0F172A), AppTheme.colors.textPrimary)
private val InkMuted: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFF64748B), AppTheme.colors.textMuted)
private val InkStrong: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFF334155), AppTheme.colors.textSecondary)
private val SlateSoft: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFFF1F5F9), AppTheme.colors.surfaceMuted)
private val SlateLine: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFFF1F5F9), AppTheme.colors.border)
private val SlateIcon: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFF94A3B8), AppTheme.colors.textMuted)

/**
 * Describes the thing a delete button is about to remove.
 *
 * [location] names the container the item is removed from, so the dialog can
 * say what is deleted from where (for example an exercise "from Push Day").
 * [isPermanent] keeps the distinction between data that is gone for good
 * (workouts, custom exercises, tracked sets) and data that is only detached
 * from a workout.
 */
@Immutable
data class DeletionInfo(
    val title: String,
    val location: String? = null,
    val isPermanent: Boolean = false,
)

@Composable
fun DeleteConfirmationDialog(
    title: String,
    itemName: String,
    location: String? = null,
    isPermanent: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { dialogWindow?.setDimAmount(0.6f) }

        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = shown,
                enter = fadeIn(tween(durationMillis = 180)) +
                    scaleIn(
                        animationSpec = tween(durationMillis = 180),
                        initialScale = 0.94f,
                    ),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 360.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = AppTheme.colors.surface,
                    border = BorderStroke(1.dp, SlateLine),
                    shadowElevation = 24.dp,
                ) {
                    Box {
                        Column(
                            modifier = Modifier.padding(
                                start = 24.dp,
                                end = 24.dp,
                                top = 28.dp,
                                bottom = 24.dp,
                            ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(DangerSoft)
                                    .border(1.dp, DangerBorder, RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = null,
                                    tint = Danger,
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = title,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Ink,
                                textAlign = TextAlign.Center,
                                letterSpacing = (-0.2).sp,
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = deletionMessage(
                                    itemName = itemName,
                                    location = location,
                                    isPermanent = isPermanent,
                                    strongColor = InkStrong,
                                ),
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                color = InkMuted,
                                textAlign = TextAlign.Center,
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Button(
                                    onClick = onDismiss,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SlateSoft,
                                        contentColor = InkStrong,
                                    ),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                                    contentPadding = PaddingValues(0.dp),
                                ) {
                                    Text(
                                        text = "Cancel",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }

                                Button(
                                    onClick = onConfirm,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Danger,
                                        contentColor = Color.White,
                                    ),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                                    contentPadding = PaddingValues(0.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isPermanent) "Delete" else "Remove",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 12.dp, end = 12.dp)
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(SlateSoft)
                                .clickable(onClick = onDismiss),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Cancel",
                                tint = SlateIcon,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun deletionMessage(
    itemName: String,
    location: String?,
    isPermanent: Boolean,
    strongColor: Color,
) =
    buildAnnotatedString {
        append(if (isPermanent) "Are you sure you want to permanently delete " else "Are you sure you want to remove ")
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = strongColor)) {
            append(itemName)
        }
        location?.takeIf { it.isNotBlank() }?.let { place ->
            append(" from ")
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = strongColor)) {
                append(place)
            }
        }
        append("?")
    }
