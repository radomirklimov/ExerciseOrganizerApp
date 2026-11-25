package com.organizer.sync

import com.organizer.auth.OwnerProvider
import com.organizer.data.local.db.entities.SyncStagedStatus
import com.organizer.data.remote.SyncApi
import com.organizer.data.remote.model.SyncChangeRecordDto
import com.organizer.data.remote.model.SyncChangesResponseDto
import com.organizer.data.remote.model.SyncPushRequestDto
import com.organizer.data.remote.model.SyncPushResponseDto
import com.organizer.fakes.FakeExerciseDao
import com.organizer.fakes.FakeSyncCursorDao
import com.organizer.fakes.FakeSyncOutboxDao
import com.organizer.fakes.FakeSyncStagedDao
import com.organizer.fakes.FakeWorkoutDao
import com.organizer.fakes.ImmediateSyncTransactionRunner
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class SyncManagerTest {

    private val json = Json { ignoreUnknownKeys = true }

    private class MutableOwner : OwnerProvider() {
        @Volatile var uid = "user-a"
        override fun currentOwner(): String = uid
    }

    private class TestTokenProvider : SyncTokenProvider {
        override suspend fun idTokenFor(owner: String): String? =
            if (owner == OwnerProvider.GUEST) null else "captured-token"
    }

    private class FakeSyncApi : SyncApi {
        val authorizations = mutableListOf<String>()
        var changesBlock: suspend (String?, Int) -> SyncChangesResponseDto = { _, _ ->
            SyncChangesResponseDto(emptyList(), "cursor-1", false, "2026-11-24T10:00:01Z")
        }

        override suspend fun changes(
            cursor: String?,
            limit: Int,
            authorization: String,
        ): SyncChangesResponseDto {
            authorizations += authorization
            return changesBlock(cursor, limit)
        }

        override suspend fun push(
            request: SyncPushRequestDto,
            authorization: String,
        ): SyncPushResponseDto = error("not used")
    }

    private class Fixture(val owner: MutableOwner = MutableOwner()) {
        val cursors = FakeSyncCursorDao()
        val staged = FakeSyncStagedDao()
        val workouts = FakeWorkoutDao()
        val exercises = FakeExerciseDao()
        val outbox = FakeSyncOutboxDao()
        val api = FakeSyncApi()
        val json = Json { ignoreUnknownKeys = true }
        val applier = SyncStagedApplier(
            owner, workouts, exercises, outbox, staged, ImmediateSyncTransactionRunner(), json,
        )
        val manager = SyncManager(
            owner, TestTokenProvider(), SyncApiClient(api, json), SyncContractMapper(),
            ImmediateSyncTransactionRunner(), cursors, staged, applier, SyncOperationLocks(),
        )
    }

    private fun page(syncId: String) = SyncChangesResponseDto(
        records = listOf(
            SyncChangeRecordDto(
                entityType = "WORKOUT",
                syncId = syncId,
                serverVersion = 1,
                serverUpdatedAt = "2026-11-24T10:00:00Z",
                deleted = false,
                payload = json.parseToJsonElement("""{"schemaVersion":1,"name":"Remote","notes":""}""").jsonObject,
            )
        ),
        nextCursor = "cursor-2",
        hasMore = false,
        serverTime = "2026-11-24T10:00:01Z",
    )

    @Test
    fun `pull uses the captured owner token`() = runTest {
        val f = Fixture()
        f.api.changesBlock = { _, _ -> page(UUID.randomUUID().toString()) }

        val result = f.manager.pullOnePage()

        assertEquals(1, result.applied)
        assertEquals(listOf("Bearer captured-token"), f.api.authorizations)
    }

    @Test
    fun `account switch after staging aborts application`() = runTest {
        val owner = MutableOwner()
        val cursors = FakeSyncCursorDao()
        val staged = FakeSyncStagedDao()
        val workouts = FakeWorkoutDao()
        val outbox = FakeSyncOutboxDao()
        val api = FakeSyncApi().apply { changesBlock = { _, _ -> page(UUID.randomUUID().toString()) } }
        val json = Json { ignoreUnknownKeys = true }
        // Switch the ambient account only after the staging transaction commits.
        val switchingRunner = object : com.organizer.sync.SyncTransactionRunner {
            override suspend fun <T> run(block: suspend () -> T): T {
                val result = block()
                owner.uid = "user-b"
                return result
            }
        }
        val applier = SyncStagedApplier(
            owner, workouts, FakeExerciseDao(), outbox, staged, switchingRunner, json,
        )
        val manager = SyncManager(
            owner, TestTokenProvider(), SyncApiClient(api, json), SyncContractMapper(),
            switchingRunner, cursors, staged, applier, SyncOperationLocks(),
        )

        val result = manager.pullOnePage()

        assertTrue(result.ownerChanged)
        assertNotNull(result.error)
        assertTrue(staged.records.value.all { it.processingStatus == SyncStagedStatus.PENDING })
        assertTrue(workouts.data.value.isEmpty())
    }

    @Test
    fun `explicit retry requeues only staged failed records`() = runTest {
        val f = Fixture()
        val now = 1_700_000_000_000L
        listOf(
            SyncStagedStatus.FAILED to "WORKOUT",
            SyncStagedStatus.UNSUPPORTED_SCHEMA to "CUSTOM_EXERCISE",
            SyncStagedStatus.LOCAL_PENDING to "WORKOUT_EXERCISE",
        ).forEachIndexed { index, (status, entityType) ->
            f.staged.upsertIfNewer(
                "user-a", entityType, "sync-$index", 1, now, false, null, status, now,
            )
        }

        assertEquals(1, f.manager.retryStagedFailed())

        assertEquals(
            SyncStagedStatus.PENDING,
            f.staged.records.value.first { it.syncId == "sync-0" }.processingStatus,
        )
        assertEquals(
            SyncStagedStatus.UNSUPPORTED_SCHEMA,
            f.staged.records.value.first { it.syncId == "sync-1" }.processingStatus,
        )
        assertEquals(
            SyncStagedStatus.LOCAL_PENDING,
            f.staged.records.value.first { it.syncId == "sync-2" }.processingStatus,
        )
    }

    @Test
    fun `guest pull never requests a token`() = runTest {
        val f = Fixture(MutableOwner().apply { uid = OwnerProvider.GUEST })

        val result = f.manager.pullOnePage()

        assertNotNull(result.error)
        assertFalse(result.ownerChanged)
        assertTrue(f.api.authorizations.isEmpty())
        assertNull(f.cursors.get("user-a"))
    }
}
