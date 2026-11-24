package com.organizer.presentation.screens.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MotivationalPhrasesTest {

    @Test
    fun `token round trips through every phrase index`() {
        repeat(30) { index ->
            assertEquals(index, MotivationalPhrases.autoIndex(MotivationalPhrases.tokenFor(index)))
        }
    }

    @Test
    fun `custom note is not treated as an automatic note`() {
        assertNull(MotivationalPhrases.autoIndex("Remember to stretch"))
        assertEquals(
            "Remember to stretch",
            MotivationalPhrases.resolve("Remember to stretch") { error("should not be called") },
        )
    }

    @Test
    fun `invalid token is rendered as-is`() {
        listOf("auto_motivational:30", "auto_motivational:abc", "auto_motivational:").forEach {
            assertNull(MotivationalPhrases.autoIndex(it))
            assertEquals(it, MotivationalPhrases.resolve(it) { error("should not be called") })
        }
    }

    @Test
    fun `automatic note follows the strings supplied by the current language`() {
        val token = MotivationalPhrases.tokenFor(2)
        val english = MotivationalPhrases.resolve(token) { index -> "phrase_${index + 1}" }
        val russian = MotivationalPhrases.resolve(token) { index -> "фраза_${index + 1}" }

        assertTrue(english.startsWith("phrase_3"))
        assertTrue(russian.startsWith("фраза_3"))
        assertEquals(english.removePrefix("phrase_3"), russian.removePrefix("фраза_3"))
    }

    @Test
    fun `random index can be stored and resolved later`() {
        val token = MotivationalPhrases.tokenFor(MotivationalPhrases.randomIndex())
        val index = MotivationalPhrases.autoIndex(token)

        assertTrue(index != null && index in 0 until 30)
        assertTrue(MotivationalPhrases.resolve(token) { "phrase_$it" }.startsWith("phrase_"))
    }
}
