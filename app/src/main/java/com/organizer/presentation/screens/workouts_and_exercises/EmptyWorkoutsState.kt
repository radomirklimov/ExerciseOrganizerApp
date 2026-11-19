package com.organizer.presentation.screens.workouts_and_exercises

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.organizer.data.local.db.entities.StarterTemplateEntity
import com.organizer.presentation.OrganizerViewModel
import com.organizer.ui.theme.AppTheme

@Composable
fun EmptyWorkoutsState(
    templates: List<StarterTemplateEntity>,
    onCreateWorkoutClick: () -> Unit,
    onTemplateClick: (Long) -> Unit,
    onViewAllTemplatesClick: () -> Unit,
    viewModel: OrganizerViewModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(176.dp)
                    .blur(48.dp)
                    .background(AppTheme.colors.primarySoft.copy(alpha = 0.5f), CircleShape),
            )

            Box(
                modifier = Modifier
                    .size(144.dp)
                    .shadow(4.dp, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
                    .background(AppTheme.colors.surface)
                    .border(1.dp, AppTheme.colors.borderStrong.copy(alpha = 0.3f), RoundedCornerShape(24.dp)),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(96.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(AppTheme.colors.primarySoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.FitnessCenter,
                        contentDescription = null,
                        tint = AppTheme.colors.primaryDark,
                        modifier = Modifier.size(48.dp),
                    )
                }
            }
        }

        Text(
            text = "No Workout Routines Yet",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = AppTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Create structured workout routines to organize exercises, track sets, reps, and weights during your sessions.",
            fontSize = 14.sp,
            color = AppTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onCreateWorkoutClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(25.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Create New Workout",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (templates.isNotEmpty()) {
            Spacer(modifier = Modifier.height(32.dp))
            StarterTemplatesSection(
                templates = templates,
                onTemplateClick = onTemplateClick,
                onViewAllClick = onViewAllTemplatesClick,
                title = "Or explore starter templates",
                viewModel = viewModel,
            )
        }
    }
}
