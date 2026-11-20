package com.organizer.presentation.settings

import androidx.lifecycle.ViewModel
import com.organizer.data.preferences.FontSizeOption
import com.organizer.data.preferences.LanguageOption
import com.organizer.data.preferences.SettingsRepository
import com.organizer.data.preferences.ThemeMode
import com.organizer.data.preferences.UnitSystem
import com.organizer.data.preferences.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<UserSettings> = repository.settings

    fun setThemeMode(mode: ThemeMode) = repository.setThemeMode(mode)

    fun setFontSize(size: FontSizeOption) = repository.setFontSize(size)

    fun setLanguage(language: LanguageOption) = repository.setLanguage(language)

    /** Re-reads preferences into the in-memory state (used after external writes). */
    fun reload() = repository.reload()

    fun setUnitSystem(units: UnitSystem) = repository.setUnitSystem(units)

    fun setWeight(value: Double?) = repository.setWeight(value)

    fun setWorkoutReminderEnabled(enabled: Boolean) =
        repository.setWorkoutReminderEnabled(enabled)

    fun setNotificationsEnabled(enabled: Boolean) =
        repository.setNotificationsEnabled(enabled)

    fun updateProfile(name: String, email: String) {
        repository.setDisplayName(name)
        repository.setEmail(email)
    }

    fun updateAvatar(path: String?) = repository.setAvatarPath(path)

    fun resetProfile() = repository.resetProfile()
}
