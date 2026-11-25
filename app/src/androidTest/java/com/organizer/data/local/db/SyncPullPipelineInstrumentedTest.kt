package com.organizer.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.organizer.auth.OwnerProvider
import com.organizer.data.remote.SyncApi
import com.organizer.data.remote.model.SyncChangesResponseDto
import com.organizer.data.remote.model.SyncChangeRecordDto
import com.organizer.sync.SyncApiClient
import com.organizer.sync.SyncContractMapper
import com.organizer.sync.SyncManager
import com.organizer.sync.SyncOperationLocks
import com.organizer.sync.SyncStagedApplier
import com.organizer.sync.SyncTokenProvider
import com.organizer.sync.SyncTransactionRunner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SyncPullPipelineInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val json = Json { ignoreUnknownKeys = true }

    private class MutableOwner(initial: String) : OwnerProvider() {
        @Volatile var uid = initial
        override fun currentOwner(): String = uid
    }

    private class SwitchingOwner(initial: String, private val changeAtCall: Int) : OwnerProvider() {
        @Volatile private var uid = initial
        private var calls = 0

        override fun currentOwner(): String {
            calls++
            if (calls == changeAtCall) uid = "user-b"
            return uid
        }
    }

    private class FakeSyncApi(var response: SyncChangesResponseDto) : SyncApi {
        var calls = 0
        var lastCursor: String? = null
        var lastLimit: Int = 0

        override suspend fun changes(cursor: String?, limit: Int, authorization: String): SyncChangesResponseDto {
            calls++
            lastCursor = cursor
            lastLimit = limit
            return response
        }

        override suspend fun push(
            request: com.organizer.data.remote.model.SyncPushRequestDto,
            authorization: String,
        ): com.organizer.data.remote.model.SyncPushResponseDto = error("push not used")
    }

    private class TestTokenProvider : SyncTokenProvider {
        override suspend fun idTokenFor(owner: String): String? =
            if (owner == OwnerProvider.GUEST) null else "captured-user-token"
    }

    private class FailingRunner(private val db: AppDatabase) : SyncTransactionRunner {
        override suspend fun <T> run(block: suspend () -> T): T = db.withTransaction {
            block()
            throw IllegalStateException("simulated failure")
        }
    }

    private fun response(syncId: String, version: Long = 1, deleted: Boolean = false) = SyncChangesResponseDto(
        records = listOf(
            SyncChangeRecordDto(
                entityType = "WORKOUT",
                syncId = syncId,
                serverVersion = version,
                serverUpdatedAt = "2026-11-24T10:00:00Z",
                deleted = deleted,
                payload = json.parseToJsonElement("""{"schemaVersion":1,"name":"Remote","notes":""}""").jsonObject,
            )
        ),
        nextCursor = "opaque-cursor-1",
        hasMore = false,
        serverTime = "2026-11-24T10:00:01Z",
    )

    private fun openDb() = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()

    private fun manager(
        db: AppDatabase,
        owner: OwnerProvider,
        api: SyncApi,
        runner: SyncTransactionRunner = com.organizer.sync.RoomSyncTransactionRunner(db),
    ) = SyncManager(
        owner,
        TestTokenProvider(),
        SyncApiClient(api, json),
        SyncContractMapper(),
        runner,
        db.syncCursorDao,
        db.syncStagedDao,
        SyncStagedApplier(owner, db.workoutDao, db.exerciseDao, db.syncOutboxDao, db.syncStagedDao, runner, json),
        SyncOperationLocks(),
    )

    @Test
    fun pullStagesAppliesAndPersistsCursor() = runBlocking {
        val db = openDb()
        try {
            val owner = MutableOwner("user-a")
            val api = FakeSyncApi(response(UUID.randomUUID().toString()))
            val result = manager(db, owner, api).pullOnePage(50)

            assertEquals(1, result.received)
            assertEquals(1, result.applied)
            assertEquals("opaque-cursor-1", db.syncCursorDao.get("user-a")!!.cursor)
            assertEquals(50, api.lastLimit)
            assertNull(api.lastCursor)
            assertEquals(1, db.workoutDao.getAll("user-a").first().size)
            assertEquals("SYNCED", db.workoutDao.getAll("user-a").first().single().syncStatus)
            assertEquals("APPLIED", db.syncStagedDao.getAll("user-a").single().processingStatus)
        } finally {
            db.close()
        }
    }

    @Test
    fun cursorIsReusedAndGuestPullIsRejected() = runBlocking {
        val db = openDb()
        try {
            val owner = MutableOwner("user-a")
            val api = FakeSyncApi(response(UUID.randomUUID().toString()))
            val mgr = manager(db, owner, api)
            mgr.pullOnePage()
            mgr.pullOnePage()

            assertEquals("opaque-cursor-1", api.lastCursor)

            owner.uid = com.organizer.auth.OwnerProvider.GUEST
            val before = api.calls
            val guest = mgr.pullOnePage()
            assertNotNull(guest.error)
            assertEquals(before, api.calls)
        } finally {
            db.close()
        }
    }

    @Test
    fun accountSwitchDuringPullDoesNotPersist() = runBlocking {
        val db = openDb()
        try {
            val owner = MutableOwner("user-a")
            val api = FakeSyncApi(response(UUID.randomUUID().toString()))
            // Switch owner after API call is prepared by mutating during the call.
            val switchingApi = object : SyncApi by api {
                override suspend fun changes(cursor: String?, limit: Int, authorization: String): SyncChangesResponseDto {
                    val result = api.changes(cursor, limit, authorization)
                    owner.uid = "user-b"
                    return result
                }
            }
            val result = manager(db, owner, switchingApi).pullOnePage()

            assertNotNull(result.error)
            assertTrue(result.error!!.contains("account changed", ignoreCase = true))
            assertNull(db.syncCursorDao.get("user-a"))
            assertTrue(db.syncStagedDao.getAll("user-a").isEmpty())
            assertTrue(db.workoutDao.getAll("user-a").first().isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun stagedWritesAreVersionMonotonic() = runBlocking {
        val db = openDb()
        try {
            val syncId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            db.syncStagedDao.upsertIfNewer("user-a", "WORKOUT", syncId, 5, now, false, "v5", "PENDING", now)
            val original = db.syncStagedDao.find("user-a", "WORKOUT", syncId)!!

            db.syncStagedDao.upsertIfNewer("user-a", "WORKOUT", syncId, 3, now, false, "v3", "PENDING", now)
            db.syncStagedDao.upsertIfNewer("user-a", "WORKOUT", syncId, 4, now, true, "v4", "PENDING", now)
            db.syncStagedDao.upsertIfNewer("user-a", "WORKOUT", syncId, 5, now, false, "dup", "APPLIED", now)

            val afterOlder = db.syncStagedDao.find("user-a", "WORKOUT", syncId)!!
            assertEquals(5L, afterOlder.serverVersion)
            assertEquals(false, afterOlder.deleted)
            assertEquals(original.stagedId, afterOlder.stagedId)

            db.syncStagedDao.updateStatus("user-a", original.stagedId, "APPLIED", 0, null, now)
            db.syncStagedDao.upsertIfNewer("user-a", "WORKOUT", syncId, 5, now, false, "dup", "PENDING", now)
            assertEquals("APPLIED", db.syncStagedDao.find("user-a", "WORKOUT", syncId)!!.processingStatus)

            db.syncStagedDao.upsertIfNewer("user-a", "WORKOUT", syncId, 6, now, true, "v6", "PENDING", now)
            val newer = db.syncStagedDao.find("user-a", "WORKOUT", syncId)!!
            assertEquals(6L, newer.serverVersion)
            assertEquals(true, newer.deleted)
        } finally {
            db.close()
        }
    }

    @Test
    fun ownerSwitchInsideTransactionDoesNotPersist() = runBlocking {
        val db = openDb()
        try {
            val owner = SwitchingOwner("user-a", changeAtCall = 3)
            val api = FakeSyncApi(response(UUID.randomUUID().toString()))
            val result = manager(db, owner, api).pullOnePage()

            assertNotNull(result.error)
            assertTrue(result.error!!.contains("account changed", ignoreCase = true))
            assertNull(db.syncCursorDao.get("user-a"))
            assertTrue(db.syncStagedDao.getAll("user-a").isEmpty())
            assertTrue(db.workoutDao.getAll("user-a").first().isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun failedStagingRollsBackCursor() = runBlocking {
        val db = openDb()
        try {
            val owner = MutableOwner("user-a")
            val api = FakeSyncApi(response(UUID.randomUUID().toString()))
            val result = manager(db, owner, api, FailingRunner(db)).pullOnePage()
            assertNotNull(result.error)
            assertNull(db.syncCursorDao.get("user-a"))
            assertTrue(db.syncStagedDao.getAll("user-a").isEmpty())
        } finally {
            db.close()
        }
    }
}