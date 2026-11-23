package com.organizer.presentation.screens.welcome

import androidx.annotation.DrawableRes
import com.organizer.R

data class OnboardPage(
    @DrawableRes val imageRes: Int,
    val titleRes: Int,
    val descriptionRes: Int,
)

internal val onboardPagesList = listOf(
    OnboardPage(
        imageRes = R.drawable.ic_launcher_foreground,
        titleRes = R.string.onboard_explore_title,
        descriptionRes = R.string.onboard_explore_description,
    ),
    OnboardPage(
        imageRes = R.drawable.ic_launcher_background,
        titleRes = R.string.onboard_build_title,
        descriptionRes = R.string.onboard_build_description,
    ),
    OnboardPage(
        imageRes = R.drawable.ic_launcher_background,
        titleRes = R.string.onboard_create_title,
        descriptionRes = R.string.onboard_create_description,
    ),
)
