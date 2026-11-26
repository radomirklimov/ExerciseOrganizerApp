package com.organizer.sync

import com.organizer.auth.OwnerProvider
import com.organizer.data.local.db.entities.SyncCursorEntity
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.util.UUID

class SyncManagerTest {

    private val json = Json { ignoreUnknownKeys = true }

    private class MutableOwner : OwnerProvider() {
        @Volatile var uid = "user-a"
        override fun currentOwner(): String = uid
    }

    private class SwitchOwner(private val switchAtCall: Int) : OwnerProvider() {
        var calls = 0
        override fun currentOwner(): String {
            calls++
            return if (calls == switchAtCall) "user-b" else "user-a"
        }
    }

    private class TestTokenProvider : SyncTokenProvider {
        override suspend fun idTokenFor(owner: String): String? =
            if (owner == OwnerProvider.GUEST) null else "captured-token"
    }

    private class FakeSyncApi : SyncApi {
        val authorizations = mutableListOf<String>()
        val cursors = mutableListOf<String?>()
        var changesBlock: suspend (String?, Int) -> SyncChangesResponseDto = { _, _ ->
            SyncChangesResponseDto(emptyList(), "cursor-1", false, "2026-11-24T10:00:01Z")
        }

        override suspend fun changes(
            cursor: String?,
            limit: Int,
            authorization: String,
        ): SyncChangesResponseDto {
            authorizations += authorization
            cursors += cursor
            return changesBlock(cursor, limit)
        }

        override suspend fun push(
            request: SyncPushRequestDto,
            authorization: String,
        ): SyncPushResponseDto = error("not used")
    }

    private class Fixture(val owner: OwnerProvider = MutableOwner()) {
        val cursors = FakeSyncCursorDao()
        val staged = FakeSyncStagedDao()
        val workouts = FakeWorkoutDao()
        val exercises = FakeExerciseDao()
        val outbox = FakeSyncOutboxDao()
        val api = FakeSyncApi()
        val json = Json { ignoreUnknownKeys = true }
        val applier = SyncStagedApplier(
            owner, workouts, exercises, outbox, com.organizer.fakes.FakeWorkoutExerciseDao(), staged, ImmediateSyncTransactionRunner(), json,
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
            owner, workouts, FakeExerciseDao(), outbox, com.organizer.fakes.FakeWorkoutExerciseDao(), staged, switchingRunner, json,
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

    private suspend fun seedCursor(f: Fixture, value: String) {
        f.cursors.upsert(
            SyncCursorEntity(
                ownerFirebaseUid = "user-a",
                cursor = value,
                lastSyncedAt = 1L,
                updatedAt = 1L,
            )
        )
    }

    private fun httpError(code: Int, error: String): suspend (String?, Int) -> SyncChangesResponseDto {
        val response = Response.error<SyncChangesResponseDto>(
            code,
            """{"error":"$error","message":"request failed"}""".toResponseBody("application/json".toMediaType()),
        )
        return { _, _ -> throw HttpException(response) }
    }

    @Test
    fun `expired cursor is cleared and pull retried once from null`() = runTest {
        val f = Fixture()
        seedCursor(f, "old-cursor")
        val syncId = UUID.randomUUID().toString()
        var call = 0
        f.api.changesBlock = { _, _ ->
            call++
            if (call == 1) throw HttpException(
                Response.error<SyncChangesResponseDto>(
                    400,
                    """{"error":"CURSOR_EXPIRED","message":"Sync cursor expired"}"""
                        .toResponseBody("application/json".toMediaType()),
                )
            ) else page(syncId)
        }

        val result = f.manager.pullOnePage()

        assertEquals(1, result.applied)
        assertEquals(listOf<String?>("old-cursor", null), f.api.cursors)
        assertEquals("cursor-2", f.cursors.get("user-a")!!.cursor)
    }

    @Test
    fun `expired cursor retry failure does not loop`() = runTest {
        val f = Fixture()
        seedCursor(f, "old-cursor")
        var call = 0
        f.api.changesBlock = { _, _ ->
            call++
            if (call == 1) throw HttpException(
                Response.error<SyncChangesResponseDto>(
                    400,
                    """{"error":"CURSOR_EXPIRED","message":"Sync cursor expired"}"""
                        .toResponseBody("application/json".toMediaType()),
                )
            ) else httpError(400, "INVALID_REQUEST")(null, 100)
        }

        val result = f.manager.pullOnePage()

        assertNotNull(result.error)
        assertEquals(2, call)
        assertEquals(listOf<String?>("old-cursor", null), f.api.cursors)
        assertNull(f.cursors.get("user-a"))
    }

    @Test
    fun `generic and invalid cursor errors preserve the cursor`() {
        listOf("INVALID_REQUEST", "UNAUTHENTICATED", "SOMETHING_ELSE").forEach { code ->
            runTest {
                val f = Fixture()
                seedCursor(f, "old-cursor")
                f.api.changesBlock = httpError(400, code)

                val result = f.manager.pullOnePage()

                assertNotNull(result.error)
                assertEquals(1, f.api.cursors.size)
                assertEquals("old-cursor", f.cursors.get("user-a")!!.cursor)
            }
        }
    }

    @Test
    fun `account switch before cursor reset aborts without clearing or retrying`() = runTest {
        val f = Fixture(SwitchOwner(2))
        seedCursor(f, "old-cursor")
        f.api.changesBlock = httpError(400, "CURSOR_EXPIRED")

        val result = f.manager.pullOnePage()

        assertTrue(result.ownerChanged)
        assertEquals(1, f.api.cursors.size)
        assertEquals("old-cursor", f.cursors.get("user-a")!!.cursor)
    }

    @Test
    fun `account switch before retry aborts after clearing the cursor`() = runTest {
        val f = Fixture(SwitchOwner(4))
        seedCursor(f, "old-cursor")
        f.api.changesBlock = httpError(400, "CURSOR_EXPIRED")

        val result = f.manager.pullOnePage()

        assertTrue(result.ownerChanged)
        assertEquals(1, f.api.cursors.size)
        assertNull(f.cursors.get("user-a"))
    }
}
