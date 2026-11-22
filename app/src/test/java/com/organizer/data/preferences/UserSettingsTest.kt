package com.organizer.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class UserSettingsTest {

    @Test
    fun initialsUseFirstTwoWords() {
        assertEquals("AM", UserSettings(displayName = "Alex Morgan").initials)
        assertEquals("A", UserSettings(displayName = "Alex").initials)
        assertEquals("AM", UserSettings(displayName = "  alex   morgan  ").initials)
    }

    @Test
    fun initialsFallBackToLetterWhenNameIsBlank() {
        assertEquals("A", UserSettings(displayName = "   ").initials)
    }

    @Test
    fun languageTagsMapToTheSixSupportedLanguages() {
        assertEquals(LanguageOption.ENGLISH, LanguageOption.fromTag("en"))
        assertEquals(LanguageOption.RUSSIAN, LanguageOption.fromTag("ru"))
        assertEquals(LanguageOption.GERMAN, LanguageOption.fromTag("de"))
        assertEquals(LanguageOption.SPANISH, LanguageOption.fromTag("es"))
        assertEquals(LanguageOption.FRENCH, LanguageOption.fromTag("fr"))
        assertEquals(LanguageOption.PORTUGUESE_BRAZIL, LanguageOption.fromTag("pt-BR"))
    }

    @Test
    fun languageFromTagFallsBackToEnglish() {
        assertEquals(LanguageOption.ENGLISH, LanguageOption.fromTag("bg"))
        assertEquals(LanguageOption.ENGLISH, LanguageOption.fromTag("unknown"))
        assertEquals(LanguageOption.ENGLISH, LanguageOption.fromTag(null))
    }

    @Test
    fun defaultsMatchTheDesign() {
        val settings = UserSettings()
        assertEquals(ThemeMode.LIGHT, settings.themeMode)
        assertEquals(FontSizeOption.MEDIUM, settings.fontSize)
        assertEquals(UnitSystem.METRIC, settings.unitSystem)
        assertEquals(LanguageOption.ENGLISH, settings.language)
    }

    @Test
    fun weightConversionRebasesBetweenUnits() {
        assertEquals(null, UserSettings.convertWeight(null, UnitSystem.METRIC, UnitSystem.IMPERIAL))
        assertEquals(80.0, UserSettings.convertWeight(80.0, UnitSystem.METRIC, UnitSystem.METRIC) ?: 0.0, 0.0001)
        val pounds = UserSettings.convertWeight(80.0, UnitSystem.METRIC, UnitSystem.IMPERIAL) ?: 0.0
        assertEquals(176.37, pounds, 0.01)
        val kilograms = UserSettings.convertWeight(176.37, UnitSystem.IMPERIAL, UnitSystem.METRIC) ?: 0.0
        assertEquals(80.0, kilograms, 0.01)
    }
}
