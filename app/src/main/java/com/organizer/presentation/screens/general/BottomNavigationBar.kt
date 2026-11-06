package com.organizer.presentation.screens.general

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.List
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.organizer.presentation.Routes

private val NavigationSelectedColor = Color(0xFF6750A4)
private val NavigationUnselectedColor = Color(0xFF8A8A8A)
private val NavigationBackgroundColor = Color.White

@Composable
fun BottomNavigationBar(
    currentRoute: String?,
    onHomeClick: () -> Unit,
    onWorkoutsClick: () -> Unit,
    onExercisesClick: () -> Unit,
    onAddCardClick: () -> Unit,
) {
    NavigationBar(
        containerColor = NavigationBackgroundColor,
        tonalElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(118.dp),
    ) {
        CustomNavigationItem(
            selected = currentRoute == Routes.Sports.route,
            icon = Icons.Outlined.Home,
            label = "Home",
            onClick = onHomeClick,
        )

        CustomNavigationItem(
            selected = currentRoute == Routes.Workouts.route,
            icon = Icons.Outlined.FitnessCenter,
            label = "Workouts",
            onClick = onWorkoutsClick,
        )

        CustomNavigationItem(
            selected = currentRoute == Routes.Exercises.route,
            icon = Icons.Outlined.List,
            label = "Exercises",
            onClick = onExercisesClick,
        )

        CustomNavigationItem(
            selected = currentRoute == Routes.AddCard.route,
            icon = Icons.Outlined.Add,
            label = "Add Card",
            onClick = onAddCardClick,
        )
    }
}

@Composable
private fun RowScope.CustomNavigationItem(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    isAddButton: Boolean = false,
) {
    val animatedColor by animateColorAsState(
        targetValue = if (selected) {
            NavigationSelectedColor
        } else {
            NavigationUnselectedColor
        },
        animationSpec = spring(),
        label = "navigationColor",
    )

    val animatedScale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = spring(
            dampingRatio = 0.55f,
            stiffness = 400f,
        ),
        label = "navigationScale",
    )

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(
                interactionSource = remember {
                    MutableInteractionSource()
                },
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(if (isAddButton) 44.dp else 32.dp)
                .scale(animatedScale)
                .background(
                    color = when {
                        isAddButton -> NavigationSelectedColor
                        selected -> NavigationSelectedColor.copy(alpha = 0.12f)
                        else -> Color.Transparent
                    },
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(
                    if (isAddButton) 26.dp else 24.dp
                ),
                tint = if (isAddButton) {
                    Color.White
                } else {
                    animatedColor
                },
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (selected) {
                FontWeight.Bold
            } else {
                FontWeight.Medium
            },
            color = animatedColor,
        )
    }
}
