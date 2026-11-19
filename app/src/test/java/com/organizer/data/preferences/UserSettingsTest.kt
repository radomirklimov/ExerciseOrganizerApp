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
}
