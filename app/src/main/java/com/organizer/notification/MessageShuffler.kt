package com.organizer.notification

import kotlin.random.Random

/**
 * Draws indices in a random order, using every index once before any repeats.
 *
 * A plain random pick can show the same message twice in a row and can leave
 * some messages unused for a long time. This "shuffle bag" keeps the indices
 * that have not been used yet, refills them with a fresh shuffle when empty,
 * and avoids starting the next cycle with the index that was just used.
 *
 * The remaining queue and the last drawn index can be snapshotted, so the
 * rotation survives process death or a device reboot between two
 * notifications.
 */
internal class MessageShuffler(
    private val size: Int,
    private val random: Random = Random.Default,
) {
    private val queue = ArrayDeque<Int>()
    private var lastDrawn: Int = NONE

    /** Index to show next. */
    fun next(): Int {
        if (queue.isEmpty()) refill()
        lastDrawn = queue.removeFirst()
        return lastDrawn
    }

    /** Indices that are still waiting, in draw order. */
    fun remaining(): List<Int> = queue.toList()

    /** The index returned by the most recent [next] call, or -1. */
    fun lastDrawn(): Int = lastDrawn

    /**
     * Restores a previously saved state. Unknown or duplicated indices are
     * dropped so a corrupt preference value cannot crash the notification.
     */
    fun restore(remaining: List<Int>, lastDrawn: Int) {
        queue.clear()
        queue.addAll(remaining.distinct().filter { it in 0 until size })
        this.lastDrawn = lastDrawn.takeIf { it in 0 until size } ?: NONE
    }

    private fun refill() {
        val pool = (0 until size).shuffled(random).toMutableList()
        // A fresh shuffle occasionally starts with the message that was just
        // shown. Swap it away instead of dropping it, so each cycle still
        // contains every message exactly once.
        if (size > 1 && pool.first() == lastDrawn) {
            val swapWith = random.nextInt(1, size)
            pool[0] = pool[swapWith].also { pool[swapWith] = pool[0] }
        }
        queue.addAll(pool)
    }

    private companion object {
        const val NONE = -1
    }
}
