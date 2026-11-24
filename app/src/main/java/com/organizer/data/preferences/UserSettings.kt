package com.organizer.data.preferences

import com.organizer.R

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

        /**
         * Maps the device's current locale to a supported app language, or
         * null when the device language is not supported (in which case the
         * user is asked to pick one).
         */
        fun fromDeviceLocale(): LanguageOption? {
            val locale = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                android.content.res.Resources.getSystem().configuration.locales[0]
            } else {
                @Suppress("DEPRECATION")
                android.content.res.Resources.getSystem().configuration.locale
            }
            return when (locale.language) {
                "en" -> ENGLISH
                "ru" -> RUSSIAN
                "de" -> GERMAN
                "es" -> SPANISH
                "fr" -> FRENCH
                "pt" -> if (locale.country == "BR") PORTUGUESE_BRAZIL else null
                else -> null
            }
        }
    }
}

enum class UnitSystem(val label: String, val labelRes: Int) {
    METRIC("kg", R.string.unit_kg),
    IMPERIAL("lb", R.string.unit_lb),
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
    /** Body weight in the unit chosen in [unitSystem] (kg or lb). */
    val weight: Double? = null,
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

    companion object {
        /** One pound expressed in kilograms; used to re-base weights on unit switches. */
        const val POUNDS_TO_KILOGRAMS = 0.45359237

        /**
         * Re-bases a body weight from [from] onto [to]. Switching the unit
         * system must not just relabel stored numbers: the same real weight is
         * re-expressed in the newly selected unit.
         */
        fun convertWeight(weight: Double?, from: UnitSystem, to: UnitSystem): Double? {
            if (weight == null) return null
            val kilograms = if (from == UnitSystem.IMPERIAL) weight * POUNDS_TO_KILOGRAMS else weight
            return if (to == UnitSystem.IMPERIAL) kilograms / POUNDS_TO_KILOGRAMS else kilograms
        }
    }
}
