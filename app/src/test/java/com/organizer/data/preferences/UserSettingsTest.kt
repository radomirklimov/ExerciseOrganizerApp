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
    fun languageFromTagFallsBackToEnglish() {
        assertEquals(LanguageOption.GERMAN, LanguageOption.fromTag("de"))
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
