package com.organizer.presentation.screens.welcome

import android.content.Context
import androidx.core.content.edit

object OnboardingManager {
    private const val PREF_NAME = "app_preferences"
    private const val KEY_ONBOARDING_DONE = "onboarding_done"


    fun isCompleted(context: Context): Boolean {
        return context
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ONBOARDING_DONE, false)
    }


    fun complete(context: Context) {
        context
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit {
                putBoolean(KEY_ONBOARDING_DONE, true)
            }
    }
}
