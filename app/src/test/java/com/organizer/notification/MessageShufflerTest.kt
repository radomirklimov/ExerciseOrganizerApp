package com.organizer.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MessageShufflerTest {

    @Test
    fun `uses every message once before repeating any`() {
        val shuffler = MessageShuffler(size = 7, random = Random(42))

        val firstCycle = List(7) { shuffler.next() }

        assertEquals((0 until 7).toSet(), firstCycle.toSet())
        assertEquals(7, firstCycle.distinct().size)
    }

    @Test
    fun `keeps covering the whole list on later cycles`() {
        val shuffler = MessageShuffler(size = 7, random = Random(7))

        repeat(10) { cycle ->
            val drawn = List(7) { shuffler.next() }
            assertEquals("cycle $cycle", (0 until 7).toSet(), drawn.toSet())
        }
    }

    @Test
    fun `never repeats the same message across a cycle boundary`() {
        val shuffler = MessageShuffler(size = 7, random = Random(1234))

        var previous = shuffler.next()
        repeat(200) {
            val next = shuffler.next()
            assertNotEquals(previous, next)
            previous = next
        }
    }

    @Test
    fun `order is shuffled rather than sequential`() {
        val shuffler = MessageShuffler(size = 7, random = Random(99))

        val drawn = List(7) { shuffler.next() }

        assertNotEquals((0 until 7).toList(), drawn)
    }

    @Test
    fun `restores the queue so a restart does not restart the cycle`() {
        val first = MessageShuffler(size = 7, random = Random(2024))
        val firstDraws = List(3) { first.next() }

        val restored = MessageShuffler(size = 7, random = Random(2024))
        restored.restore(first.remaining(), first.lastDrawn())
        val remainingDraws = List(4) { restored.next() }

        assertEquals(
            (0 until 7).toSet(),
            (firstDraws + remainingDraws).toSet(),
        )
    }

    @Test
    fun `ignores corrupt saved state`() {
        val shuffler = MessageShuffler(size = 3, random = Random(5))
        shuffler.restore(remaining = listOf(9, -1, 1, 1), lastDrawn = 42)

        val drawn = List(6) { shuffler.next() }

        assertTrue(drawn.all { it in 0 until 3 })
        assertEquals(setOf(0, 1, 2), drawn.toSet())
    }

    @Test
    fun `handles a single message list`() {
        val shuffler = MessageShuffler(size = 1, random = Random(1))

        repeat(3) {
            assertEquals(0, shuffler.next())
        }
    }
}
