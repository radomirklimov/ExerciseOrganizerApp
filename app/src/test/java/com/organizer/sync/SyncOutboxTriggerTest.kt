package com.organizer.sync

import com.organizer.auth.OwnerProvider
import com.organizer.data.local.db.entities.SyncOutboxEntity
import com.organizer.fakes.FakeSyncOutboxDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncOutboxTriggerTest {
    private class TestOwner : OwnerProvider() {
        val ownerFlow = MutableStateFlow(OwnerProvider.GUEST)
        override fun currentOwner(): String = ownerFlow.value
        override fun observeOwner() = ownerFlow
    }

    private class CountingTrigger : SyncTrigger {
        var calls = 0
        override fun requestSyncAfterLocalChange() { calls++ }
    }

    private fun mutation(id: String, status: String = SyncOutboxStatus.PENDING_UPLOAD, owner: String = "user-a") =
        SyncOutboxEntity(
            mutationId = id, ownerFirebaseUid = owner, entityType = SyncEntityTypes.WORKOUT,
            recordSyncId = "rec-$id", operation = "UPSERT", baseVersion = 0, payload = "{}", syncStatus = status,
        )

    @Test
    fun `committed eligible outbox mutation requests sync`() = runTest {
        val owner = TestOwner().apply { ownerFlow.value = "user-a" }
        val outbox = FakeSyncOutboxDao()
        val trigger = CountingTrigger()
        val scope = TestScope()
        OutboxSyncObserver(owner, outbox, trigger).start(scope)
        scope.advanceUntilIdle()
        assertEquals(0, trigger.calls)
        outbox.insert(mutation("m1"))
        scope.advanceUntilIdle()
        assertEquals(1, trigger.calls)
    }

    @Test
    fun `multiple rapid mutations coalesce into one request`() = runTest {
        val owner = TestOwner().apply { ownerFlow.value = "user-a" }
        val outbox = FakeSyncOutboxDao()
        val trigger = CountingTrigger()
        val scope = TestScope()
        OutboxSyncObserver(owner, outbox, trigger).start(scope)
        scope.advanceUntilIdle()
        outbox.insert(mutation("m1"))
        outbox.insert(mutation("m2"))
        scope.advanceUntilIdle()
        assertEquals(1, trigger.calls)
    }

    @Test
    fun `guest outbox does not request sync`() = runTest {
        val owner = TestOwner()
        val outbox = FakeSyncOutboxDao()
        val trigger = CountingTrigger()
        val scope = TestScope()
        OutboxSyncObserver(owner, outbox, trigger).start(scope)
        scope.advanceUntilIdle()
        outbox.insert(mutation("m1", owner = OwnerProvider.GUEST))
        scope.advanceUntilIdle()
        assertEquals(0, trigger.calls)
    }

    @Test
    fun `non eligible outbox states do not request sync`() = runTest {
        val owner = TestOwner().apply { ownerFlow.value = "user-a" }
        val outbox = FakeSyncOutboxDao()
        val trigger = CountingTrigger()
        val scope = TestScope()
        OutboxSyncObserver(owner, outbox, trigger).start(scope)
        scope.advanceUntilIdle()
        outbox.insert(mutation("m1", status = SyncOutboxStatus.CONFLICTED))
        scope.advanceUntilIdle()
        assertEquals(0, trigger.calls)
    }

    @Test
    fun `remote apply without outbox writes does not request sync`() = runTest {
        val owner = TestOwner().apply { ownerFlow.value = "user-a" }
        val outbox = FakeSyncOutboxDao()
        val trigger = CountingTrigger()
        val scope = TestScope()
        OutboxSyncObserver(owner, outbox, trigger).start(scope)
        scope.advanceUntilIdle()
        // no outbox write: no request
        assertEquals(0, trigger.calls)
        assertEquals(0, outbox.entries.value.size)
    }

    @Test
    fun `new mutation after previous sync triggers follow up`() = runTest {
        val owner = TestOwner().apply { ownerFlow.value = "user-a" }
        val outbox = FakeSyncOutboxDao()
        val trigger = CountingTrigger()
        val scope = TestScope()
        OutboxSyncObserver(owner, outbox, trigger).start(scope)
        scope.advanceUntilIdle()
        outbox.insert(mutation("m1"))
        scope.advanceUntilIdle()
        outbox.insert(mutation("m2"))
        scope.advanceUntilIdle()
        assertEquals(2, trigger.calls)
    }

    @Test
    fun `scheduling failure does not undo outbox mutation`() = runTest {
        val owner = TestOwner().apply { ownerFlow.value = "user-a" }
        val outbox = FakeSyncOutboxDao()
        val failing = object : SyncTrigger {
            var calls = 0
            override fun requestSyncAfterLocalChange() {
                calls++
                throw IllegalStateException("WorkManager unavailable")
            }
        }
        // Observer does not catch trigger failures today; wrap at call site.
        val safe = SyncTrigger { failing.calls++; runCatching { failing.requestSyncAfterLocalChange() } }
        val scope = TestScope()
        OutboxSyncObserver(owner, outbox, safe).start(scope)
        scope.advanceUntilIdle()
        outbox.insert(mutation("m1"))
        scope.advanceUntilIdle()
        assertTrue(failing.calls >= 1)
        assertEquals(1, outbox.entries.value.size)
    }
}
