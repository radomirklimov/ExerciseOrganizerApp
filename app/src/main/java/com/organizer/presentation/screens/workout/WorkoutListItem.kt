package com.organizer.presentation.screens.workout

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.organizer.data.repo.joinCategoryNames

@Composable
fun WorkoutListItem(
    title: String,
    categories: List<String>,
    onClick: () -> Unit
) {

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 6.dp
        )
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 24.dp,
                    vertical = 28.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 24.sp,
                    color = Color.Black,
                    fontWeight = FontWeight.Medium,
//                    fontWeight = FontWeight.Bold,
//                    color = Color(0xFF1C1B1F),
//                    lineHeight = 20.sp,
//                    maxLines = 2,
//                    overflow = TextOverflow.Ellipsis,
                )

                WorkoutCategorySummary(categories = categories)
            }

            Spacer(Modifier.width(8.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null
            )
        }
    }
}

/**
 * Renders as many category names as fit on a single line, using the same
 * "A & B" / "A · B · C" formatting as starter templates. Whole category names
 * are kept: when the next one would not fit it is dropped rather than cut in
 * half.
 */
@Composable
private fun WorkoutCategorySummary(
    categories: List<String>,
    modifier: Modifier = Modifier,
) {
    if (categories.isEmpty()) return

    // Same style as the sport subtitles on the home page: size and colour.
    val textStyle = LocalTextStyle.current.copy(
        fontSize = 13.sp,
        color = Color(0xFF494551),
    )
    val measurer = rememberTextMeasurer()

    BoxWithConstraints(modifier = modifier.padding(top = 2.dp)) {
        val maxWidthPx = constraints.maxWidth
        val label = remember(categories, maxWidthPx, textStyle) {
            fitCategoryLabel(categories, maxWidthPx, measurer, textStyle)
        }

        if (label.isNotEmpty()) {
            Text(
                text = label,
                style = textStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun fitCategoryLabel(
    categories: List<String>,
    maxWidthPx: Int,
    measurer: TextMeasurer,
    style: TextStyle,
): String {
    fun fits(label: String): Boolean =
        measurer.measure(
            text = label,
            style = style,
            maxLines = 1,
        ).size.width <= maxWidthPx

    val full = joinCategoryNames(categories).orEmpty()
    if (fits(full)) return full

    // Drop categories from the end until the remaining summary fits. Because
    // the separator depends on the count, every candidate is re-joined.
    for (count in categories.size - 1 downTo 1) {
        val label = joinCategoryNames(categories.take(count)).orEmpty()
        if (fits(label)) return label
    }

    return categories.first()
}
