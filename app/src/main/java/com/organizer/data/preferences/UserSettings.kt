package com.organizer.data.preferences

/** How the app chooses between the light and dark palette. */
enum class ThemeMode(val label: String) {
    LIGHT("Light"),
    DARK("Dark"),
}

/** App-wide text scale multiplier. Applied on top of the system font scale. */
enum class FontSizeOption(val label: String, val scale: Float) {
    SMALL("Small", 0.9f),
    MEDIUM("Medium", 1f),
    LARGE("Large", 1.15f),
}

/** Languages the profile screen can switch between. */
enum class LanguageOption(
    val tag: String,
    val englishName: String,
    val nativeName: String,
) {
    ENGLISH("en", "English", "English (US)"),
    RUSSIAN("ru", "Russian", "Русский"),
    GERMAN("de", "German", "Deutsch"),
    SPANISH("es", "Spanish", "Español"),
    FRENCH("fr", "French", "Français"),
    PORTUGUESE_BRAZIL("pt-BR", "Portuguese (Brazil)", "Português (Brasil)"),
    ;

    companion object {
        fun fromTag(tag: String?): LanguageOption =
            entries.firstOrNull { it.tag == tag } ?: ENGLISH
    }
}

enum class UnitSystem(val label: String) {
    METRIC("kg / cm"),
    IMPERIAL("lb / ft"),
}

/** Everything the profile screen lets the user personalise. */
data class UserSettings(
    val displayName: String = "Alex Morgan",
    val email: String = "alex.morgan@fitflow.app",
    val avatarPath: String? = null,
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val fontSize: FontSizeOption = FontSizeOption.MEDIUM,
    val language: LanguageOption = LanguageOption.ENGLISH,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val workoutReminderEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val reminderTime: String = "18:00",
) {
    /** First letters of the display name, used by the avatar fallback. */
    val initials: String
        get() = displayName
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifBlank { "A" }
}
