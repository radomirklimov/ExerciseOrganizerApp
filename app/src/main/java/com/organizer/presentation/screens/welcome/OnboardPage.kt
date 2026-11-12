package com.organizer.presentation.screens.welcome

import androidx.annotation.DrawableRes
import com.organizer.R

data class OnboardPage(
    @DrawableRes val imageRes: Int,
    val title: String,
    val description: String
)

val onboardPagesList = listOf(
    OnboardPage(
        imageRes = R.drawable.ic_launcher_foreground,
        title = "Explore Exercises",
        description = "Browse a growing library of exercises for strength, mobility, conditioning, and sports."
    ),
    OnboardPage(
        imageRes = R.drawable.ic_launcher_background,
        title = "Build Workouts",
        description = "Combine exercises into custom routines that fit your goals."
    ),
    OnboardPage(
        imageRes = R.drawable.ic_launcher_background,
        title = "Create Your Own",
        description = "Create personal exercises with your own images, descriptions, and details."
    )
)
