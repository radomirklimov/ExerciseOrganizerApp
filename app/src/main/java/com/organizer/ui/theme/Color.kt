package com.organizer.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

/**
 * Slightly darker shade of [color]. Used for the outline of the floating
 * bottom action buttons so it matches their fill instead of the page behind.
 */
fun darkerShade(color: Color, amount: Float = 0.14f): Color =
    lerp(color, Color.Black, amount)