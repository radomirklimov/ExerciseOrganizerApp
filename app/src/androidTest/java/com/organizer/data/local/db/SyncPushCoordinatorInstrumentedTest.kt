package com.organizer.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.organizer.auth.OwnerProvider
import com.organizer.data.local.db.entities.SyncOutboxEntity
import com.organizer.data.local.db.entities.SyncStagedRecordEntity
import com.organizer.data.local.db.entities.SyncStagedStatus
import com.organizer.data.remote.SyncApi
import com.organizer.data.remote.model.SyncChangesResponseDto
import com.organizer.data.remote.model.SyncMutationResultDto
import com.organizer.data.remote.model.SyncPushRequestDto
import com.organizer.data.remote.model.SyncPushResponseDto
import com.organizer.sync.RoomSyncTransactionRunner
import com.organizer.sync.SyncApiClient
import com.organizer.sync.SyncContractMapper
import com.organizer.sync.SyncOutboxAcknowledger
import com.organizer.sync.SyncOperationLocks
import com.organizer.sync.SyncOutboxStatus
import com.organizer.sync.SyncPushCoordinator
import com.organizer.sync.SyncReconciler
import com.organizer.sync.SyncStagedApplier
import com.organizer.sync.SyncTokenProvider
import com.organizer.sync.SyncTransactionRunner
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SyncPushCoordinatorInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val owner = "user-a"

    private class TestOwner : OwnerProvider() {
        @Volatile var uid = "user-a"
        var calls = 0
        var switchAtCall = -1

        override fun currentOwner(): String {
            calls++
            if (calls == switchAtCall) uid = "user-b"
            return uid
        }
    }

    private class TestTokenProvider(var token: String? = "user-a-token") : SyncTokenProvider {
        var hook: (() -> Unit)? = null
        override suspend fun idTokenFor(owner: String): String? {
            hook?.invoke()
            return token
        }
    }

    private class FakeSyncApi : SyncApi {
        val pushed = mutableListOf<SyncPushRequestDto>()
        val authorizations = mutableListOf<String>()
        var pushBlock: suspend (SyncPushRequestDto) -> SyncPushResponseDto = { error("pushBlock not configured") }

        override suspend fun push(request: SyncPushRequestDto, authorization: String): SyncPushResponseDto {
            pushed += request
            authorizations += authorization
            return pushBlock(request)
        }

        override suspend fun changes(cursor: String?, limit: Int, authorization: String): SyncChangesResponseDto =
            throw UnsupportedOperationException("not used in push tests")
    }

    private class FailingRunner(private val db: AppDatabase) : SyncTransactionRunner {
        override suspend fun <T> run(block: suspend () -> T): T = db.withTransaction {
            block()
            throw IllegalStateException("simulated crash")
        }
    }

    private fun openDb() = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()

    private fun mutation(
        syncId: String = UUID.randomUUID().toString(),
        status: String = SyncOutboxStatus.PENDING_UPLOAD,
        ownerUid: String = owner,
        createdAt: Long = 1_000L,
        outboxId: Long = 0L, // 0 lets Room auto-generate a unique primary key
    ) = SyncOutboxEntity(
        outboxId = outboxId,
        mutationId = UUID.randomUUID().toString(),
        ownerFirebaseUid = ownerUid,
        entityType = "WORKOUT",
        recordSyncId = syncId,
        operation = "UPSERT",
        payload = """{"schemaVersion":1,"name":"Local"}""",
        baseVersion = 0,
        createdAt = createdAt,
        syncStatus = status,
    )

    private fun resultsFor(request: SyncPushRequestDto, status: String = "APPLIED") = SyncPushResponseDto(
        results = request.mutations.map {
            SyncMutationResultDto(
                mutationId = it.mutationId,
                syncId = it.syncId,
                status = status,
                serverVersion = 1,
                serverUpdatedAt = "2026-11-24T10:00:00Z",
            )
        },
        serverTime = "2026-11-24T10:00:00Z",
    )

    private fun httpError(code: Int, errorCode: String): suspend (SyncPushRequestDto) -> SyncPushResponseDto {
        val body = """{"error":"$errorCode","message":"invalid request"}"""
        val response = Response.error<SyncPushResponseDto>(
            code,
            body.toResponseBody("application/json".toMediaType()),
        )
        return { throw HttpException(response) }
    }

    private fun coordinator(
        db: AppDatabase,
        api: FakeSyncApi,
        ownerProvider: OwnerProvider,
        runner: SyncTransactionRunner = RoomSyncTransactionRunner(db),
        tokens: TestTokenProvider = TestTokenProvider(),
    ): SyncPushCoordinator {
        val json = Json { ignoreUnknownKeys = true }
        val acknowledger = SyncOutboxAcknowledger(ownerProvider, db.syncOutboxDao, db.workoutDao, db.exerciseDao, db.workoutExerciseDao, runner)
        val applier = SyncStagedApplier(
            ownerProvider, db.workoutDao, db.exerciseDao, db.syncOutboxDao, db.workoutExerciseDao, db.syncStagedDao,
            RoomSyncTransactionRunner(db), json,
        )
        val reconciler = SyncReconciler(ownerProvider, db.syncOutboxDao, db.syncStagedDao, applier, RoomSyncTransactionRunner(db))
        return SyncPushCoordinator(
            ownerProvider, tokens, db.syncOutboxDao, SyncApiClient(api, json), SyncContractMapper(),
            acknowledger, reconciler, runner, SyncOperationLocks(),
        )
    }

    @Test
    fun noSchemaMigrationIsNeededForSupersededStatus() = runBlocking {
        val db = openDb()
        try {
            // SUPERSEDED is a new value of the existing syncStatus column; no
            // column or index change is required, so no migration is needed.
            val m = mutation(status = SyncOutboxStatus.SUPERSEDED)
            db.syncOutboxDao.insert(m)
            assertEquals(SyncOutboxStatus.SUPERSEDED, db.syncOutboxDao.findByMutationId(owner, m.mutationId)!!.syncStatus)
        } finally {
            db.close()
        }
    }

    @Test
    fun selectionAndInFlightMarkingAreAtomic() = runBlocking {
        val db = openDb()
        try {
            val syncId = UUID.randomUUID().toString()
            val first = mutation(syncId = syncId, createdAt = 1L, outboxId = 1L)
            val final = mutation(syncId = syncId, createdAt = 2L, outboxId = 2L)
            val other = mutation(createdAt = 3L, outboxId = 3L)
            db.syncOutboxDao.insert(first)
            db.syncOutboxDao.insert(final)
            db.syncOutboxDao.insert(other)

            val failingApi = FakeSyncApi().apply { pushBlock = { resultsFor(it) } }
            runCatching { coordinator(db, failingApi, TestOwner(), FailingRunner(db)).pushOneBatch() }

            assertEquals(SyncOutboxStatus.PENDING_UPLOAD, db.syncOutboxDao.findByMutationId(owner, first.mutationId)!!.syncStatus)
            assertEquals(SyncOutboxStatus.PENDING_UPLOAD, db.syncOutboxDao.findByMutationId(owner, final.mutationId)!!.syncStatus)
            assertEquals(SyncOutboxStatus.PENDING_UPLOAD, db.syncOutboxDao.findByMutationId(owner, other.mutationId)!!.syncStatus)

            val api = FakeSyncApi()
            var statusesDuringRequest = emptyList<String>()
            api.pushBlock = {
                statusesDuringRequest = db.syncOutboxDao.getPending(owner).map { it.syncStatus }
                resultsFor(it)
            }
            val report = coordinator(db, api, TestOwner()).pushOneBatch()

            assertEquals(2, report.sent)
            assertEquals(1, report.superseded)
            assertEquals(2, statusesDuringRequest.count { it == SyncOutboxStatus.IN_FLIGHT })
            assertEquals(SyncOutboxStatus.SUPERSEDED, db.syncOutboxDao.findByMutationId(owner, first.mutationId)!!.syncStatus)
            assertTrue(failingApi.pushed.isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun interruptedInFlightPushIsRecoveredWithoutChangingTheMutation() = runBlocking {
        val db = openDb()
        try {
            val stale = mutation(status = SyncOutboxStatus.IN_FLIGHT)
            db.syncOutboxDao.insert(stale)

            val api = FakeSyncApi().apply { pushBlock = { resultsFor(it, status = "DUPLICATE") } }
            val report = coordinator(db, api, TestOwner()).pushOneBatch()

            assertEquals(1, report.duplicate)
            val sent = api.pushed.single().mutations.single()
            assertEquals(stale.mutationId, sent.mutationId)
            assertEquals(stale.baseVersion, sent.baseVersion)
            assertEquals(stale.operation, sent.operation)
            assertEquals(SyncOutboxStatus.ACKNOWLEDGED, db.syncOutboxDao.findByMutationId(owner, stale.mutationId)!!.syncStatus)
        } finally {
            db.close()
        }
    }

    @Test
    fun transportFailureLeavesRowRetryableForNextPush() = runBlocking {
        val db = openDb()
        try {
            val m = mutation()
            db.syncOutboxDao.insert(m)

            val failing = FakeSyncApi().apply { pushBlock = { throw IOException("offline") } }
            val failed = coordinator(db, failing, TestOwner()).pushOneBatch()
            assertEquals(1, failed.retryable)
            val stored = db.syncOutboxDao.findByMutationId(owner, m.mutationId)!!
            assertEquals(SyncOutboxStatus.PENDING_UPLOAD, stored.syncStatus)
            assertNotNull(stored.lastError)

            val succeeding = FakeSyncApi().apply { pushBlock = { resultsFor(it, status = "DUPLICATE") } }
            val retried = coordinator(db, succeeding, TestOwner()).pushOneBatch()
            assertEquals(1, retried.duplicate)
            assertEquals(m.mutationId, succeeding.pushed.single().mutations.single().mutationId)
        } finally {
            db.close()
        }
    }

    @Test
    fun partialAcknowledgementKeepsMissingRowRetryable() = runBlocking {
        val db = openDb()
        try {
            val present = mutation()
            val missing = mutation()
            db.syncOutboxDao.insert(present)
            db.syncOutboxDao.insert(missing)

            val api = FakeSyncApi().apply {
                pushBlock = {
                    SyncPushResponseDto(
                        results = listOf(
                            SyncMutationResultDto(
                                mutationId = present.mutationId,
                                syncId = present.recordSyncId,
                                status = "APPLIED",
                                serverVersion = 1,
                                serverUpdatedAt = "2026-11-24T10:00:00Z",
                            ),
                        ),
                        serverTime = "2026-11-24T10:00:00Z",
                    )
                }
            }

            val report = coordinator(db, api, TestOwner()).pushOneBatch()

            assertEquals(1, report.applied)
            assertEquals(1, report.retryable)
            assertEquals(SyncOutboxStatus.ACKNOWLEDGED, db.syncOutboxDao.findByMutationId(owner, present.mutationId)!!.syncStatus)
            assertEquals(SyncOutboxStatus.PENDING_UPLOAD, db.syncOutboxDao.findByMutationId(owner, missing.mutationId)!!.syncStatus)
        } finally {
            db.close()
        }
    }

    @Test
    fun acknowledgementReleasesStagedRecord() = runBlocking {
        val db = openDb()
        try {
            val syncId = UUID.randomUUID().toString()
            val m = mutation(syncId = syncId)
            db.syncOutboxDao.insert(m)
            db.syncStagedDao.upsertIfNewer(
                owner, "WORKOUT", syncId, 2, 1_700_000_000_000L, false,
                """{"schemaVersion":1,"name":"Remote","notes":""}""", SyncStagedStatus.LOCAL_PENDING, 1_700_000_000_000L,
            )

            val api = FakeSyncApi().apply { pushBlock = { resultsFor(it) } }
            val report = coordinator(db, api, TestOwner()).pushOneBatch()

            assertEquals(1, report.reconciled)
            assertEquals(SyncStagedStatus.APPLIED, db.syncStagedDao.find(owner, "WORKOUT", syncId)!!.processingStatus)
        } finally {
            db.close()
        }
    }

    @Test
    fun ownerIsolationAndSwitchDuringPersistence() = runBlocking {
        val db = openDb()
        try {
            val mine = mutation()
            val foreign = mutation(ownerUid = "user-b")
            db.syncOutboxDao.insert(mine)
            db.syncOutboxDao.insert(foreign)

            val api = FakeSyncApi().apply { pushBlock = { resultsFor(it) } }
            coordinator(db, api, TestOwner()).pushOneBatch()
            assertEquals(listOf(mine.mutationId), api.pushed.single().mutations.map { it.mutationId })
            assertEquals(SyncOutboxStatus.PENDING_UPLOAD, db.syncOutboxDao.findByMutationId("user-b", foreign.mutationId)!!.syncStatus)

            val switching = TestOwner().apply { switchAtCall = 3 }
            val switchDb = openDb()
            try {
                val m = mutation()
                switchDb.syncOutboxDao.insert(m)
                val switchApi = FakeSyncApi().apply { pushBlock = { resultsFor(it) } }
                val report = coordinator(switchDb, switchApi, switching).pushOneBatch()
                assertTrue(report.ownerChanged)
                assertTrue(switchApi.pushed.isEmpty())
                assertEquals(
                    SyncOutboxStatus.IN_FLIGHT,
                    switchDb.syncOutboxDao.findByMutationId(owner, m.mutationId)!!.syncStatus,
                )
            } finally {
                switchDb.close()
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun deterministicHttpErrorParksFailedAndExplicitRequeueIsOwnerScoped() = runBlocking {
        val db = openDb()
        try {
            val m = mutation()
            val foreign = mutation(status = SyncOutboxStatus.FAILED, ownerUid = "user-b")
            db.syncOutboxDao.insert(m)
            db.syncOutboxDao.insert(foreign)

            val bad = FakeSyncApi().apply { pushBlock = httpError(413, "PAYLOAD_TOO_LARGE") }
            val push = coordinator(db, bad, TestOwner())
            val report = push.pushOneBatch()

            assertEquals(1, report.failed)
            assertEquals(0, report.retryable)
            val parked = db.syncOutboxDao.findByMutationId(owner, m.mutationId)!!
            assertEquals(SyncOutboxStatus.FAILED, parked.syncStatus)
            assertNotNull(parked.lastError)
            assertTrue(parked.lastError!!.contains("HTTP 413"))
            assertEquals("""{"schemaVersion":1,"name":"Local"}""", parked.payload)
            assertNull(parked.acknowledgedAt)

            val idle = FakeSyncApi().apply { pushBlock = { resultsFor(it) } }
            assertEquals(0, coordinator(db, idle, TestOwner()).pushOneBatch().sent)
            assertTrue(idle.pushed.isEmpty())

            assertEquals(1, push.requeueFailed())
            assertEquals(SyncOutboxStatus.PENDING_UPLOAD, db.syncOutboxDao.findByMutationId(owner, m.mutationId)!!.syncStatus)
            assertEquals(SyncOutboxStatus.FAILED, db.syncOutboxDao.findByMutationId("user-b", foreign.mutationId)!!.syncStatus)

            val retry = FakeSyncApi().apply { pushBlock = { resultsFor(it) } }
            val retried = coordinator(db, retry, TestOwner()).pushOneBatch()
            assertEquals(1, retried.applied)
            assertEquals(m.mutationId, retry.pushed.single().mutations.single().mutationId)
            assertEquals(SyncOutboxStatus.ACKNOWLEDGED, db.syncOutboxDao.findByMutationId(owner, m.mutationId)!!.syncStatus)
        } finally {
            db.close()
        }
    }

    @Test
    fun authorizationStaysBoundToCapturedOwnerAfterAccountSwitch() = runBlocking {
        val db = openDb()
        try {
            val ownerProvider = TestOwner()
            val m = mutation()
            db.syncOutboxDao.insert(m)
            val tokens = TestTokenProvider().apply { hook = { ownerProvider.uid = "user-b" } }
            val api = FakeSyncApi().apply { pushBlock = { resultsFor(it) } }

            val report = coordinator(db, api, ownerProvider, tokens = tokens).pushOneBatch()

            assertTrue(report.ownerChanged)
            assertEquals("Bearer user-a-token", api.authorizations.single())
            assertEquals("user-b", ownerProvider.uid)
            assertEquals(SyncOutboxStatus.IN_FLIGHT, db.syncOutboxDao.findByMutationId(owner, m.mutationId)!!.syncStatus)
        } finally {
            db.close()
        }
    }
}
