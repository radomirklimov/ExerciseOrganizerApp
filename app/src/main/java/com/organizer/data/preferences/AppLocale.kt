package com.organizer.data.preferences

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Wraps the Activity context with the language selected on the profile screen.
 *
 * The app stores its own language value instead of relying on the system one,
 * so the choice survives restarts and can be changed without the Android 13
 * per-app language APIs.
 */
object AppLocale {

    fun wrap(base: Context): Context {
        val tag = SettingsRepository.readLanguageTag(base)
        val locale = Locale.forLanguageTag(tag)
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return base.createConfigurationContext(configuration)
    }
}
