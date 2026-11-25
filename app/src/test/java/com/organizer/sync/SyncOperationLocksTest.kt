package com.organizer.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class SyncOperationLocksTest {

    @Test
    fun `same owner shares one lock and different owners use separate locks`() {
        val locks = SyncOperationLocks()
        assertSame(locks.forOwner("user-a"), locks.forOwner("user-a"))
        assertNotSame(locks.forOwner("user-a"), locks.forOwner("user-b"))
    }

    @Test
    fun `operations for the same owner cannot overlap`() = runTest {
        val locks = SyncOperationLocks()
        val gate = CompletableDeferred<Unit>()
        var active = 0
        var maxActive = 0

        val first = async {
            locks.forOwner("user-a").withLock {
                active++
                maxActive = maxOf(maxActive, active)
                gate.await()
                active--
            }
        }
        val second = async {
            locks.forOwner("user-a").withLock {
                active++
                maxActive = maxOf(maxActive, active)
                active--
            }
        }
        yield()
        gate.complete(Unit)
        awaitAll(first, second)

        assertEquals(1, maxActive)
    }
}
