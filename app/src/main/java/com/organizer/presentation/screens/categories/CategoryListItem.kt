package com.organizer.presentation.screens.categories

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.organizer.data.local.db.entities.CategoryEntity
import com.organizer.presentation.OrganizerViewModel
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun CategoryListItem(
    category: CategoryEntity,
    onClick: () -> Unit,
    viewModel: OrganizerViewModel,
    subtitle: String? = null,
    selected: Boolean = false,
) {
    val textScrollState = rememberScrollState()

    LaunchedEffect(category.name) {
        if (textScrollState.maxValue > 0) {
            delay(1500.milliseconds)
            textScrollState.animateScrollBy(
                value = textScrollState.maxValue.toFloat(),
                animationSpec = tween(
                    durationMillis = 3000,
                    easing = LinearEasing
                )
            )
            delay(1500.milliseconds)
            textScrollState.animateScrollBy(
                value = -textScrollState.maxValue.toFloat(),
                animationSpec = tween(
                    durationMillis = 3000,
                    easing = LinearEasing
                )
            )
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp),
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        )
    ) {
        // Re-read when any media file lands so a downloaded icon appears
        // without leaving the screen. File equality keeps Coil from reloading
        // unchanged images.
        val mediaTick by viewModel.mediaVersion.collectAsState()
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!category.iconUrl.isNullOrBlank()) {
                val iconName: String = category.iconUrl
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = remember(mediaTick) { viewModel.getIconFile(iconName) },
                        contentDescription = category.name,
                        contentScale = ContentScale.Fit
                    )
                }
            }

            Spacer(modifier = Modifier.width(24.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = category.name,
                    modifier = Modifier
                        .horizontalScroll(textScrollState),
                    fontSize = 24.sp,
                    color = Color.Black,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontSize = 13.sp,
                        color = Color(0xFF494551),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.width(15.dp))

            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val arrowHighlighted = selected || isPressed
            val arrowBackground by animateColorAsState(
                targetValue = if (arrowHighlighted) Color(0xFF6750A4) else Color(0xFFF7F2F8),
                label = "categoryArrowBackground",
            )
            val arrowTint by animateColorAsState(
                targetValue = if (arrowHighlighted) Color.White else Color(0xFF494551),
                label = "categoryArrowTint",
            )

            Surface(
                onClick = onClick,
                shape = CircleShape,
                color = arrowBackground,
                modifier = Modifier.size(40.dp),
                interactionSource = interactionSource,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Open ${category.name}",
                        tint = arrowTint,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}
