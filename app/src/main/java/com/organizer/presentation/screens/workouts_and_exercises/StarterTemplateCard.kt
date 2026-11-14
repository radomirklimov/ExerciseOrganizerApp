package com.organizer.presentation.screens.workouts_and_exercises

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.SportsGymnastics
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.organizer.data.local.db.entities.StarterTemplateEntity
import com.organizer.presentation.OrganizerViewModel

private val Primary = Color(0xFF4F378A)
private val PrimaryContainer = Color(0xFFE9DDFF)
private val OnSurface = Color(0xFF1C1B1F)
private val OnSurfaceVariant = Color(0xFF494551)
private val OutlineVariant = Color(0xFFCBC4D2)

/**
 * Picks an icon that describes what the template trains. [categoryName] is the
 * dominant standard category computed from the template's exercises.
 */
fun starterTemplateIcon(categoryName: String?): ImageVector {
    val name = categoryName?.lowercase().orEmpty()
    return when {
        name.contains("chest") -> Icons.Filled.FitnessCenter
        name.contains("biceps") || name.contains("triceps") ||
            name.contains("shoulder") || name.contains("arm") ->
            Icons.Filled.SportsGymnastics
        name.contains("back") || name.contains("lat") -> Icons.Filled.AccessibilityNew
        name.contains("leg") || name.contains("quad") || name.contains("hamstring") ||
            name.contains("calf") || name.contains("glute") ->
            Icons.AutoMirrored.Filled.DirectionsRun
        name.contains("core") || name.contains("ab") -> Icons.Filled.SelfImprovement
        name.contains("cardio") || name.contains("run") -> Icons.Filled.MonitorHeart
        else -> Icons.Filled.FitnessCenter
    }
}

/**
 * Compact template card shown in the workout page. Mirrors the starter
 * template visuals that already existed in the empty state.
 */
@Composable
fun StarterTemplateCard(
    template: StarterTemplateEntity,
    onClick: () -> Unit,
    viewModel: OrganizerViewModel,
    modifier: Modifier = Modifier,
) {
    val mediaTick by viewModel.mediaVersion.collectAsState()
    val iconFile = remember(mediaTick, template.iconUrl) {
        template.iconUrl?.let { viewModel.getIconFile(it) }
    }

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, OutlineVariant.copy(alpha = 0.3f)),
        shadowElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PrimaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    if (iconFile != null) {
                        AsyncImage(
                            model = iconFile,
                            contentDescription = template.categoryName ?: template.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(28.dp),
                        )
                    } else {
                        Icon(
                            imageVector = starterTemplateIcon(template.categoryName),
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                Text(
                    text = "${template.exerciseCount} exercises",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = OnSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(9999.dp))
                        .background(Color(0xFFEBE7EC))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = template.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = OnSurface,
                lineHeight = 20.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = template.categoryName ?: "Workout",
                fontSize = 12.sp,
                color = Color(0xFF7A7582),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )

            Spacer(modifier = Modifier.weight(1f).height(12.dp))

            HorizontalDivider(color = Color(0xFFF1ECF2), thickness = 1.dp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Use Routine",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Primary,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/**
 * Horizontally scrollable starter template section shown on the workout page.
 *
 * Shows up to [MAX_VISIBLE_TEMPLATES] templates; the list ends with a
 * "View all" card so the full catalogue stays one swipe away.
 */
@Composable
fun StarterTemplatesSection(
    templates: List<StarterTemplateEntity>,
    onTemplateClick: (Long) -> Unit,
    onViewAllClick: () -> Unit,
    viewModel: OrganizerViewModel,
    modifier: Modifier = Modifier,
    title: String = "Starter templates",
) {
    if (templates.isEmpty()) return

    val visibleTemplates = templates.take(MAX_VISIBLE_TEMPLATES)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = OnSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${templates.size} " +
                    if (templates.size == 1) "routine" else "routines",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF7A7582),
                modifier = Modifier
                    .clip(RoundedCornerShape(9999.dp))
                    .background(Color(0xFFF1ECF2))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(start = 4.dp, end = 4.dp),
        ) {
            items(
                items = visibleTemplates,
                key = { it.templateId },
            ) { template ->
                StarterTemplateCard(
                    template = template,
                    onClick = { onTemplateClick(template.templateId) },
                    viewModel = viewModel,
                    modifier = Modifier
                        .width(TEMPLATE_CARD_WIDTH)
                        .height(TEMPLATE_CARD_HEIGHT),
                )
            }

            item(key = "view_all_templates") {
                ViewAllTemplatesCard(
                    onClick = onViewAllClick,
                    modifier = Modifier
                        .width(VIEW_ALL_CARD_WIDTH)
                        .height(TEMPLATE_CARD_HEIGHT),
                )
            }
        }
    }
}

/** Last card of the horizontal list: opens the full template catalogue. */
@Composable
private fun ViewAllTemplatesCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Primary,
        shadowElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "View all",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "All templates",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

private const val MAX_VISIBLE_TEMPLATES = 5
private val TEMPLATE_CARD_WIDTH = 176.dp
private val TEMPLATE_CARD_HEIGHT = 172.dp
private val VIEW_ALL_CARD_WIDTH = 116.dp
