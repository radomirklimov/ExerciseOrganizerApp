package com.organizer.presentation.screens.schedule

import android.content.Context
import androidx.core.content.edit

/**
 * Remembers whether the user has declined the notification permission.
 *
 * A fresh install keeps the workout reminder on by default, and the permission
 * is only requested when the first reminder is saved. After an explicit
 * denial, new schedules start with the reminder off; granting the permission
 * (or turning the reminder back on) clears the flag again.
 */
internal object ReminderPreferences {
    private const val PREF_NAME = "app_preferences"
    private const val KEY_NOTIFICATIONS_DENIED = "reminder_notifications_denied"

    fun notificationsDenied(context: Context): Boolean =
        context
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_NOTIFICATIONS_DENIED, false)

    fun setNotificationsDenied(context: Context, denied: Boolean) {
        context
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_NOTIFICATIONS_DENIED, denied) }
    }
}
