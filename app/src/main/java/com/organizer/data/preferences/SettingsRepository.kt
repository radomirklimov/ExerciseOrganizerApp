package com.organizer.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Small key-value store for the profile screen. Room backs the exercise
 * library; these values are simple scalars, so SharedPreferences is enough.
 *
 * The stored values drive the profile UI. The language, theme and font-size
 * choices are applied app-wide (through [AppLocale] and
 * [com.organizer.ui.theme.AppThemeProvider]).
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<UserSettings> = _settings.asStateFlow()

    fun update(transform: (UserSettings) -> UserSettings) {
        val updated = transform(_settings.value)
        write(updated)
        _settings.value = updated
    }

    fun setDisplayName(value: String) = update { it.copy(displayName = value.trim().ifBlank { it.displayName }) }

    fun setEmail(value: String) = update { it.copy(email = value.trim().ifBlank { it.email }) }

    fun setAvatarPath(value: String?) = update { it.copy(avatarPath = value) }

    fun setThemeMode(value: ThemeMode) = update { it.copy(themeMode = value) }

    fun setFontSize(value: FontSizeOption) = update { it.copy(fontSize = value) }

    fun setLanguage(value: LanguageOption) = update { it.copy(language = value) }

    /** Re-reads the persisted preferences into the in-memory state. */
    fun reload() {
        _settings.value = read()
    }

    fun setUnitSystem(value: UnitSystem) = update { current ->
        current.copy(
            unitSystem = value,
            weight = UserSettings.convertWeight(current.weight, current.unitSystem, value),
        )
    }

    fun setWorkoutReminderEnabled(value: Boolean) =
        update { it.copy(workoutReminderEnabled = value) }

    fun setNotificationsEnabled(value: Boolean) =
        update { it.copy(notificationsEnabled = value) }

    /** Restores the profile to its factory state (used by "Delete Account"). */
    fun resetProfile() {
        val reset = UserSettings(
            themeMode = _settings.value.themeMode,
            fontSize = _settings.value.fontSize,
            language = _settings.value.language,
        )
        write(reset)
        _settings.value = reset
    }

    private fun read(): UserSettings = UserSettings(
        displayName = prefs.getString(KEY_NAME, null) ?: UserSettings().displayName,
        email = prefs.getString(KEY_EMAIL, null) ?: UserSettings().email,
        avatarPath = prefs.getString(KEY_AVATAR, null),
        themeMode = enumOr(prefs.getString(KEY_THEME, null), ThemeMode.LIGHT),
        fontSize = enumOr(prefs.getString(KEY_FONT, null), FontSizeOption.MEDIUM),
        language = LanguageOption.fromTag(prefs.getString(KEY_LANGUAGE, null)),
        unitSystem = enumOr(prefs.getString(KEY_UNITS, null), UnitSystem.METRIC),
        weight = prefs.getString(KEY_WEIGHT, null)?.toDoubleOrNull(),
        workoutReminderEnabled = prefs.getBoolean(KEY_REMINDER, true),
        notificationsEnabled = prefs.getBoolean(KEY_NOTIFICATIONS, true),
        reminderTime = prefs.getString(KEY_REMINDER_TIME, null) ?: "18:00",
    )

    private fun write(value: UserSettings) {
        prefs.edit {
            putString(KEY_NAME, value.displayName)
            putString(KEY_EMAIL, value.email)
            putString(KEY_AVATAR, value.avatarPath)
            putString(KEY_THEME, value.themeMode.name)
            putString(KEY_FONT, value.fontSize.name)
            putString(KEY_LANGUAGE, value.language.tag)
            putString(KEY_UNITS, value.unitSystem.name)
            putString(KEY_WEIGHT, value.weight?.toString())
            putBoolean(KEY_REMINDER, value.workoutReminderEnabled)
            putBoolean(KEY_NOTIFICATIONS, value.notificationsEnabled)
            putString(KEY_REMINDER_TIME, value.reminderTime)
        }
    }

    private inline fun <reified T : Enum<T>> enumOr(raw: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == raw } ?: fallback

    companion object {
        const val PREF_NAME = "app_preferences"
        const val KEY_LANGUAGE = "profile_language"
        private const val KEY_NAME = "profile_name"
        private const val KEY_EMAIL = "profile_email"
        private const val KEY_AVATAR = "profile_avatar_path"
        private const val KEY_THEME = "profile_theme"
        private const val KEY_FONT = "profile_font_size"
        private const val KEY_UNITS = "profile_units"
        private const val KEY_WEIGHT = "profile_weight"
        private const val KEY_REMINDER = "profile_workout_reminder"
        private const val KEY_NOTIFICATIONS = "profile_notifications"
        private const val KEY_REMINDER_TIME = "profile_reminder_time"

        /**
         * Reads the saved language without constructing the repository. Called
         * from [AppLocale] while the Activity is still being attached.
         */
        fun readLanguageTag(context: Context): String =
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LANGUAGE, null)
                ?: LanguageOption.ENGLISH.tag

        /**
         * Reads the saved theme before the Activity window is created, so the
         * window background and splash screen already match the chosen mode.
         */
        fun readThemeMode(context: Context): ThemeMode {
            val raw = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_THEME, null)
            return ThemeMode.entries.firstOrNull { it.name == raw } ?: ThemeMode.LIGHT
        }

        /**
         * Reads the "Notification Alerts" switch without constructing the
         * repository. Used by background components that need to know whether
         * they are allowed to post anything.
         */
        fun areNotificationsEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_NOTIFICATIONS, true)
    }
}
