package com.organizer.sync

import com.organizer.auth.OwnerProvider
import com.organizer.data.local.db.entities.SyncOutboxEntity
import com.organizer.data.local.db.entities.WorkoutEntity
import com.organizer.data.local.db.entities.SyncStagedRecordEntity
import com.organizer.data.local.db.entities.SyncStagedStatus
import com.organizer.data.remote.SyncApi
import com.organizer.data.remote.model.SyncChangesResponseDto
import com.organizer.data.remote.model.SyncMutationResultDto
import com.organizer.data.remote.model.SyncPushRequestDto
import com.organizer.data.remote.model.SyncPushResponseDto
import com.organizer.fakes.FakeExerciseDao
import com.organizer.fakes.FakeSyncOutboxDao
import com.organizer.fakes.FakeSyncStagedDao
import com.organizer.fakes.FakeWorkoutDao
import com.organizer.fakes.ImmediateSyncTransactionRunner
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
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
import java.io.IOException
import java.util.UUID

class SyncPushCoordinatorTest {

    private val owner = "user-a"

    private class TestOwner(initial: String) : OwnerProvider() {
        @Volatile var uid = initial
        var calls = 0
        var switchAtCall = -1

        override fun currentOwner(): String {
            calls++
            if (calls == switchAtCall) uid = "user-b"
            return uid
        }

        fun switchTo(value: String) {
            uid = value
        }
    }

    private class TestTokenProvider(var token: String? = "user-a-token") : SyncTokenProvider {
        val requestedFor = mutableListOf<String>()
        var hook: (() -> Unit)? = null

        override suspend fun idTokenFor(owner: String): String? {
            requestedFor += owner
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

    private class Fixture(val ownerProvider: TestOwner = TestOwner("user-a")) {
        val outbox = FakeSyncOutboxDao()
        val workouts = FakeWorkoutDao()
        val exercises = FakeExerciseDao()
        val staged = FakeSyncStagedDao()
        val api = FakeSyncApi()
        val tokens = TestTokenProvider()
        val runner = ImmediateSyncTransactionRunner()
        val json = Json { ignoreUnknownKeys = true }
        val acknowledger = SyncOutboxAcknowledger(ownerProvider, outbox, workouts, exercises, com.organizer.fakes.FakeWorkoutExerciseDao(), com.organizer.fakes.FakeFavoriteExerciseDao(), null, runner)
        val applier = SyncStagedApplier(ownerProvider, workouts, exercises, outbox, com.organizer.fakes.FakeWorkoutExerciseDao(), com.organizer.fakes.FakeFavoriteExerciseDao(), null, null, staged, runner, json)
        val reconciler = SyncReconciler(ownerProvider, outbox, staged, applier, runner)
        val coordinator = SyncPushCoordinator(
            ownerProvider,
            tokens,
            outbox,
            SyncApiClient(api, json),
            SyncContractMapper(),
            acknowledger,
            reconciler,
            runner,
            SyncOperationLocks(),
        )
    }

    private fun response(results: List<SyncMutationResultDto>) = SyncPushResponseDto(
        results = results,
        serverTime = "2026-11-24T10:00:00Z",
    )

    private fun mutation(
        syncId: String = UUID.randomUUID().toString(),
        mutationId: String = UUID.randomUUID().toString(),
        ownerUid: String = owner,
        entityType: String = SyncEntityTypes.WORKOUT,
        operation: String = "UPSERT",
        status: String = SyncOutboxStatus.PENDING_UPLOAD,
        baseVersion: Long = 0,
        createdAt: Long = 1_700_000_000_000L,
        outboxId: Long = 1L,
        retryCount: Int = 0,
        lastError: String? = null,
        payload: String? = """{"schemaVersion":1,"name":"Local"}""",
    ) = SyncOutboxEntity(
        outboxId = outboxId,
        mutationId = mutationId,
        ownerFirebaseUid = ownerUid,
        entityType = entityType,
        recordSyncId = syncId,
        operation = operation,
        payload = payload,
        baseVersion = baseVersion,
        createdAt = createdAt,
        retryCount = retryCount,
        lastError = lastError,
        syncStatus = status,
    )

    private fun result(
        mutation: SyncOutboxEntity,
        status: String = "APPLIED",
        version: Long = 1,
        errorCode: String? = null,
    ) = SyncMutationResultDto(
        mutationId = mutation.mutationId,
        syncId = mutation.recordSyncId,
        status = status,
        serverVersion = version,
        serverUpdatedAt = "2026-11-24T10:00:00Z",
        errorCode = errorCode,
    )

    private fun httpError(code: Int, body: String = """{"error":"BAD_REQUEST","message":"invalid"}"""): suspend (SyncPushRequestDto) -> SyncPushResponseDto {
        val response = Response.error<SyncPushResponseDto>(
            code,
            body.toResponseBody("application/json".toMediaType()),
        )
        return { throw HttpException(response) }
    }

    @Test
    fun `guest push is rejected without a network call`() = runTest {
        val f = Fixture(TestOwner(OwnerProvider.GUEST))
        f.outbox.insert(mutation(ownerUid = OwnerProvider.GUEST))

        val report = f.coordinator.pushOneBatch()

        assertNotNull(report.error)
        assertTrue(f.api.pushed.isEmpty())
    }

    @Test
    fun `empty batch is a successful no-op`() = runTest {
        val f = Fixture()

        val report = f.coordinator.pushOneBatch()

        assertEquals(0, report.selected)
        assertEquals(0, report.sent)
        assertNull(report.error)
        assertTrue(f.api.pushed.isEmpty())
    }

    @Test
    fun `mutations are marked in flight before transmission and sent in deterministic order`() = runTest {
        val f = Fixture()
        val lateLowerId = mutation(createdAt = 20L, outboxId = 1L, syncId = UUID.randomUUID().toString())
        val lateHigherId = mutation(createdAt = 20L, outboxId = 2L, syncId = UUID.randomUUID().toString())
        val early = mutation(createdAt = 10L, outboxId = 99L, syncId = UUID.randomUUID().toString())
        f.outbox.insert(lateHigherId)
        f.outbox.insert(lateLowerId)
        f.outbox.insert(early)
        var statusesDuringRequest = emptyList<String>()
        f.api.pushBlock = {
            statusesDuringRequest = f.outbox.entries.value.map { it.syncStatus }
            response(listOf(result(early), result(lateLowerId), result(lateHigherId)))
        }

        val report = f.coordinator.pushOneBatch()

        assertEquals(3, report.sent)
        assertEquals(3, statusesDuringRequest.count { it == SyncOutboxStatus.IN_FLIGHT })
        val sentIds = f.api.pushed.single().mutations.map { it.mutationId }
        assertEquals(listOf(early.mutationId, lateLowerId.mutationId, lateHigherId.mutationId), sentIds)
    }

    @Test
    fun `selection is owner scoped`() = runTest {
        val f = Fixture()
        val mine = mutation()
        val foreign = mutation(ownerUid = "user-b")
        f.outbox.insert(mine)
        f.outbox.insert(foreign)
        f.api.pushBlock = { response(listOf(result(mine))) }

        val report = f.coordinator.pushOneBatch()

        assertEquals(1, report.sent)
        assertEquals(listOf(mine.mutationId), f.api.pushed.single().mutations.map { it.mutationId })
        assertEquals(SyncOutboxStatus.PENDING_UPLOAD, f.outbox.findByMutationId("user-b", foreign.mutationId)!!.syncStatus)
    }

    @Test
    fun `batch is bounded by the backend limit`() = runTest {
        val f = Fixture()
        val mutations = (1..120).map { mutation(createdAt = it.toLong(), outboxId = it.toLong()) }
        mutations.forEach { f.outbox.insert(it) }
        f.api.pushBlock = { call ->
            response(call.mutations.map { m ->
                SyncMutationResultDto(
                    mutationId = m.mutationId,
                    syncId = m.syncId,
                    status = "APPLIED",
                    serverVersion = 1,
                    serverUpdatedAt = "2026-11-24T10:00:00Z",
                )
            })
        }

        val report = f.coordinator.pushOneBatch()

        assertEquals(100, report.sent)
        assertEquals(100, f.api.pushed.single().mutations.size)
        assertEquals(20, f.outbox.findByStatus(owner, SyncOutboxStatus.PENDING_UPLOAD).size)
    }

    @Test
    fun `at most one mutation per record is selected`() = runTest {
        val f = Fixture()
        val syncId = UUID.randomUUID().toString()
        val otherSyncId = UUID.randomUUID().toString()
        f.outbox.insert(mutation(syncId = syncId, createdAt = 1L, outboxId = 1L, payload = """{"schemaVersion":1,"name":"First"}"""))
        val final = mutation(syncId = syncId, createdAt = 2L, outboxId = 2L, payload = """{"schemaVersion":1,"name":"Final"}""")
        f.outbox.insert(final)
        val other = mutation(syncId = otherSyncId, createdAt = 3L, outboxId = 3L)
        f.outbox.insert(other)
        f.api.pushBlock = { call ->
            response(call.mutations.map {
                SyncMutationResultDto(it.mutationId, it.syncId, "APPLIED", serverVersion = 1, serverUpdatedAt = "2026-11-24T10:00:00Z")
            })
        }

        val report = f.coordinator.pushOneBatch()

        assertEquals(2, report.sent)
        assertEquals(1, report.superseded)
        val sent = f.api.pushed.single().mutations
        assertTrue(sent.any { it.mutationId == final.mutationId && it.payload.toString().contains("Final") })
        assertFalse(sent.any { it.mutationId != final.mutationId && it.syncId == syncId })
        assertEquals(SyncOutboxStatus.ACKNOWLEDGED, f.outbox.findByMutationId(owner, final.mutationId)!!.syncStatus)
    }

    @Test
    fun `clean chain ending in RESTORE is not compressed`() = runTest {
        val f = Fixture()
        val syncId = UUID.randomUUID().toString()
        val delete = mutation(syncId = syncId, operation = "DELETE", createdAt = 1L, outboxId = 1L)
        val restore = mutation(syncId = syncId, operation = "RESTORE", createdAt = 2L, outboxId = 2L)
        f.outbox.insert(delete)
        f.outbox.insert(restore)
        f.api.pushBlock = { response(listOf(result(delete))) }

        val report = f.coordinator.pushOneBatch()

        assertEquals(1, report.sent)
        assertEquals(0, report.superseded)
        assertEquals(listOf(delete.mutationId), f.api.pushed.single().mutations.map { it.mutationId })
        assertEquals(SyncOutboxStatus.PENDING_UPLOAD, f.outbox.findByMutationId(owner, restore.mutationId)!!.syncStatus)
    }

    @Test
    fun `chain with an already-sent earlier mutation is not compressed`() = runTest {
        val f = Fixture()
        val syncId = UUID.randomUUID().toString()
        val retried = mutation(syncId = syncId, createdAt = 1L, outboxId = 1L, retryCount = 1, lastError = "timeout")
        val final = mutation(syncId = syncId, createdAt = 2L, outboxId = 2L)
        f.outbox.insert(retried)
        f.outbox.insert(final)
        f.api.pushBlock = { response(listOf(result(retried))) }

        val report = f.coordinator.pushOneBatch()

        assertEquals(0, report.superseded)
        assertEquals(listOf(retried.mutationId), f.api.pushed.single().mutations.map { it.mutationId })
        assertEquals(SyncOutboxStatus.PENDING_UPLOAD, f.outbox.findByMutationId(owner, final.mutationId)!!.syncStatus)
    }

    @Test
    fun `superseded rows are non blocking but never acknowledged`() = runTest {
        val f = Fixture()
        val syncId = UUID.randomUUID().toString()
        val old = mutation(syncId = syncId, createdAt = 1L, outboxId = 1L)
        val final = mutation(syncId = syncId, createdAt = 2L, outboxId = 2L)
        f.outbox.insert(old)
        f.outbox.insert(final)
        f.staged.upsertIfNewer(
            owner, SyncEntityTypes.WORKOUT, syncId, 1, 1_700_000_000_000L, false,
            """{"schemaVersion":1,"name":"Remote","notes":"remote"}""", SyncStagedStatus.LOCAL_PENDING, 1L,
        )
        f.api.pushBlock = { response(listOf(result(final))) }

        f.coordinator.pushOneBatch()

        val storedOld = f.outbox.findByMutationId(owner, old.mutationId)!!
        assertEquals(SyncOutboxStatus.SUPERSEDED, storedOld.syncStatus)
        assertNull(storedOld.acknowledgedAt)
        assertFalse(SyncOutboxStatus.BLOCKING.contains(SyncOutboxStatus.SUPERSEDED))
        assertEquals(SyncStagedStatus.APPLIED, f.staged.find(owner, SyncEntityTypes.WORKOUT, syncId)!!.processingStatus)
    }

    @Test
    fun `stale in flight rows are recovered and retried with identical request contents`() = runTest {
        val f = Fixture()
        val stale = mutation(status = SyncOutboxStatus.IN_FLIGHT, baseVersion = 3, createdAt = 1L)
        f.outbox.insert(stale)
        f.api.pushBlock = { response(listOf(result(stale, status = "DUPLICATE", version = 3))) }

        val report = f.coordinator.pushOneBatch()

        assertEquals(1, report.sent)
        assertEquals(1, report.duplicate)
        val sent = f.api.pushed.single().mutations.single()
        assertEquals(stale.mutationId, sent.mutationId)
        assertEquals(stale.operation, sent.operation)
        assertEquals(stale.baseVersion, sent.baseVersion)
        assertEquals(Json.parseToJsonElement(stale.payload!!), sent.payload)
        assertEquals(SyncOutboxStatus.ACKNOWLEDGED, f.outbox.findByMutationId(owner, stale.mutationId)!!.syncStatus)
    }

    @Test
    fun `transport failure leaves the mutation retryable with unchanged contents`() = runTest {
        val f = Fixture()
        val m = mutation(baseVersion = 4)
        f.outbox.insert(m)
        f.api.pushBlock = { throw IOException("offline") }

        val failed = f.coordinator.pushOneBatch()

        assertNotNull(failed.error)
        assertEquals(1, failed.retryable)
        val stored = f.outbox.findByMutationId(owner, m.mutationId)!!
        assertEquals(SyncOutboxStatus.PENDING_UPLOAD, stored.syncStatus)
        assertNotNull(stored.lastError)
        assertEquals(0, stored.retryCount)

        f.api.pushBlock = { call ->
            response(call.mutations.map {
                SyncMutationResultDto(it.mutationId, it.syncId, "DUPLICATE", serverVersion = 4, serverUpdatedAt = "2026-11-24T10:00:00Z")
            })
        }
        val retried = f.coordinator.pushOneBatch()

        assertEquals(1, retried.duplicate)
        val first = f.api.pushed[0].mutations.single()
        val second = f.api.pushed[1].mutations.single()
        assertEquals(first, second)
        assertEquals(SyncOutboxStatus.ACKNOWLEDGED, f.outbox.findByMutationId(owner, m.mutationId)!!.syncStatus)
    }

    @Test
    fun `malformed response and server error leave mutations retryable`() = runTest {
        val malformed = Fixture()
        val m1 = mutation()
        malformed.outbox.insert(m1)
        malformed.api.pushBlock = { throw SerializationException("bad json") }
        val malformedReport = malformed.coordinator.pushOneBatch()
        assertEquals(1, malformedReport.retryable)
        assertEquals(SyncOutboxStatus.PENDING_UPLOAD, malformed.outbox.findByMutationId(owner, m1.mutationId)!!.syncStatus)

        val serverError = Fixture()
        val m2 = mutation(baseVersion = 7)
        serverError.outbox.insert(m2)
        serverError.api.pushBlock = httpError(503, """{"error":"SERVER_ERROR","message":"down"}""")
        val serverReport = serverError.coordinator.pushOneBatch()
        assertEquals(1, serverReport.retryable)
        val stored = serverError.outbox.findByMutationId(owner, m2.mutationId)!!
        assertEquals(SyncOutboxStatus.PENDING_UPLOAD, stored.syncStatus)
        assertEquals(m2.mutationId, stored.mutationId)
        assertEquals(m2.operation, stored.operation)
        assertEquals(m2.baseVersion, stored.baseVersion)
        assertEquals(m2.payload, stored.payload)
    }

    @Test
    fun `401 stays retryable with unchanged mutation data`() = runTest {
        val f = Fixture()
        val m = mutation(baseVersion = 6)
        f.outbox.insert(m)
        f.api.pushBlock = httpError(401, """{"error":"UNAUTHENTICATED","message":"login"}""")

        val report = f.coordinator.pushOneBatch()

        assertEquals(1, report.retryable)
        val stored = f.outbox.findByMutationId(owner, m.mutationId)!!
        assertEquals(SyncOutboxStatus.PENDING_UPLOAD, stored.syncStatus)
        assertEquals(m.mutationId, stored.mutationId)
        assertEquals(m.operation, stored.operation)
        assertEquals(m.baseVersion, stored.baseVersion)
        assertEquals(m.payload, stored.payload)
        assertTrue(report.error!!.contains("401"))

        // After auth refresh the same mutation is eligible again unchanged.
        f.api.pushBlock = { call ->
            response(call.mutations.map {
                SyncMutationResultDto(it.mutationId, it.syncId, "APPLIED", serverVersion = 6, serverUpdatedAt = "2026-11-24T10:00:00Z")
            })
        }
        val retried = f.coordinator.pushOneBatch()
        assertEquals(1, retried.applied)
        assertEquals(m.mutationId, f.api.pushed[1].mutations.single().mutationId)
    }

    @Test
    fun `deterministic 4xx parks mutation as failed and explicit requeue restores eligibility`() {
        listOf(400 to "BAD_REQUEST", 413 to "PAYLOAD_TOO_LARGE").forEach { (code, errorCode) ->
            runTest {
                val f = Fixture()
                val m = mutation(baseVersion = 5)
                f.outbox.insert(m)
                f.api.pushBlock = httpError(code, """{"error":"$errorCode","message":"invalid"}""")

                val report = f.coordinator.pushOneBatch()

                assertEquals(1, report.failed)
                assertEquals(0, report.retryable)
                assertTrue(report.error!!.contains("HTTP $code"))
                val parked = f.outbox.findByMutationId(owner, m.mutationId)!!
                assertEquals(SyncOutboxStatus.FAILED, parked.syncStatus)
                assertTrue(parked.lastError!!.contains("HTTP $code"))
                assertEquals(m.mutationId, parked.mutationId)
                assertEquals(m.operation, parked.operation)
                assertEquals(m.baseVersion, parked.baseVersion)
                assertEquals(m.payload, parked.payload)
                assertNull(parked.acknowledgedAt)
                assertTrue(SyncOutboxStatus.BLOCKING.contains(SyncOutboxStatus.FAILED))
                assertFalse(SyncOutboxStatus.TERMINAL.contains(SyncOutboxStatus.FAILED))

                // Ordinary manual pushes must not retry the invalid batch.
                f.api.pushBlock = { response(emptyList()) }
                val skipped = f.coordinator.pushOneBatch()
                assertEquals(0, skipped.sent)
                assertEquals(1, f.api.pushed.size)

                // Explicit recovery makes the same data eligible again.
                assertEquals(1, f.coordinator.requeueFailed())
                val reloaded = f.outbox.findByMutationId(owner, m.mutationId)!!
                assertEquals(SyncOutboxStatus.PENDING_UPLOAD, reloaded.syncStatus)
                assertNull(reloaded.lastError)
                assertEquals(m.mutationId, reloaded.mutationId)
                assertEquals(m.baseVersion, reloaded.baseVersion)
                assertEquals(m.payload, reloaded.payload)

                f.api.pushBlock = { call ->
                    response(call.mutations.map {
                        SyncMutationResultDto(it.mutationId, it.syncId, "APPLIED", serverVersion = 5, serverUpdatedAt = "2026-11-24T10:00:00Z")
                    })
                }
                assertEquals(1, f.coordinator.pushOneBatch().applied)
                assertEquals(m.mutationId, f.api.pushed[1].mutations.single().mutationId)
                assertEquals(SyncOutboxStatus.ACKNOWLEDGED, f.outbox.findByMutationId(owner, m.mutationId)!!.syncStatus)
            }
        }
    }

    @Test
    fun `partial response acknowledges present results and resets missing rows`() = runTest {
        val f = Fixture()
        val present = mutation()
        val missing = mutation()
        f.outbox.insert(present)
        f.outbox.insert(missing)
        f.api.pushBlock = { response(listOf(result(present))) }

        val report = f.coordinator.pushOneBatch()

        assertEquals(1, report.applied)
        assertEquals(1, report.retryable)
        assertEquals(SyncOutboxStatus.ACKNOWLEDGED, f.outbox.findByMutationId(owner, present.mutationId)!!.syncStatus)
        val storedMissing = f.outbox.findByMutationId(owner, missing.mutationId)!!
        assertEquals(SyncOutboxStatus.PENDING_UPLOAD, storedMissing.syncStatus)
        assertNotNull(storedMissing.lastError)
    }

    @Test
    fun `duplicate response entries are processed once`() = runTest {
        val f = Fixture()
        val m = mutation()
        f.outbox.insert(m)
        f.api.pushBlock = { response(listOf(result(m), result(m))) }

        val report = f.coordinator.pushOneBatch()

        assertEquals(1, report.applied)
        assertEquals(1, report.skipped)
        assertEquals(SyncOutboxStatus.ACKNOWLEDGED, f.outbox.findByMutationId(owner, m.mutationId)!!.syncStatus)
    }

    @Test
    fun `unknown mutation ids and mismatched sync ids are handled safely`() = runTest {
        val f = Fixture()
        val m = mutation()
        f.outbox.insert(m)
        val mismatched = SyncMutationResultDto(
            mutationId = m.mutationId,
            syncId = UUID.randomUUID().toString(),
            status = "APPLIED",
        )
        val unknown = SyncMutationResultDto(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "APPLIED")
        f.api.pushBlock = { response(listOf(unknown, mismatched)) }

        val report = f.coordinator.pushOneBatch()

        assertEquals(0, report.applied)
        assertEquals(2, report.skipped) // one unknown mutationId, one mismatched syncId
        assertEquals(1, report.retryable)
        assertEquals(SyncOutboxStatus.PENDING_UPLOAD, f.outbox.findByMutationId(owner, m.mutationId)!!.syncStatus)
    }

    @Test
    fun `unrecognized result status is reset to retryable`() = runTest {
        val f = Fixture()
        val m = mutation()
        f.outbox.insert(m)
        f.api.pushBlock = { response(listOf(result(m, status = "UNKNOWN_STATUS"))) }

        val report = f.coordinator.pushOneBatch()

        assertEquals(1, report.retryable)
        assertEquals(SyncOutboxStatus.PENDING_UPLOAD, f.outbox.findByMutationId(owner, m.mutationId)!!.syncStatus)
    }

    @Test
    fun `conflict and rejection remain blocking and are not retried`() = runTest {
        val f = Fixture()
        val conflicted = mutation()
        val rejected = mutation()
        f.outbox.insert(conflicted)
        f.outbox.insert(rejected)
        f.api.pushBlock = {
            response(listOf(result(conflicted, status = "CONFLICT", errorCode = "VERSION_CONFLICT"), result(rejected, status = "REJECTED")))
        }

        val report = f.coordinator.pushOneBatch()

        assertEquals(1, report.conflicted)
        assertEquals(1, report.rejected)
        assertEquals(0, report.retryable)
        assertEquals(SyncOutboxStatus.CONFLICTED, f.outbox.findByMutationId(owner, conflicted.mutationId)!!.syncStatus)
        assertEquals(SyncOutboxStatus.REJECTED_TERMINAL, f.outbox.findByMutationId(owner, rejected.mutationId)!!.syncStatus)
    }

    @Test
    fun `account switch before request leaves rows recoverable`() = runTest {
        val switching = TestOwner(owner).apply { switchAtCall = 3 }
        val f = Fixture(switching)
        val m = mutation()
        f.outbox.insert(m)

        val report = f.coordinator.pushOneBatch()

        assertTrue(report.ownerChanged)
        assertTrue(f.api.pushed.isEmpty())
        assertEquals(SyncOutboxStatus.IN_FLIGHT, f.outbox.findByMutationId(owner, m.mutationId)!!.syncStatus)

        switching.switchTo(owner)
        f.api.pushBlock = { response(listOf(result(m))) }
        val recovered = f.coordinator.pushOneBatch()
        assertEquals(1, recovered.applied)
    }

    @Test
    fun `account switch during acknowledgement aborts persistence`() = runTest {
        val switching = TestOwner(owner).apply { switchAtCall = 4 }
        val f = Fixture(switching)
        val m = mutation()
        f.outbox.insert(m)
        f.api.pushBlock = { response(listOf(result(m))) }

        val report = f.coordinator.pushOneBatch()

        assertTrue(report.ownerChanged)
        assertEquals(SyncOutboxStatus.IN_FLIGHT, f.outbox.findByMutationId(owner, m.mutationId)!!.syncStatus)
    }

    @Test
    fun `successful acknowledgement triggers reconciliation`() = runTest {
        val f = Fixture()
        val syncId = UUID.randomUUID().toString()
        val m = mutation(syncId = syncId)
        f.outbox.insert(m)
        f.staged.upsertIfNewer(
            owner, SyncEntityTypes.WORKOUT, syncId, 1, 1_700_000_000_000L, false,
            """{"schemaVersion":1,"name":"Remote","notes":"remote"}""", SyncStagedStatus.LOCAL_PENDING, 1L,
        )
        f.api.pushBlock = { response(listOf(result(m))) }

        val report = f.coordinator.pushOneBatch()

        assertEquals(1, report.applied)
        assertEquals(1, report.reconciled)
        assertEquals(SyncStagedStatus.APPLIED, f.staged.find(owner, SyncEntityTypes.WORKOUT, syncId)!!.processingStatus)
    }

    @Test
    fun `recovered in flight mutation is never coalesced and retries byte-identically`() = runTest {
        val f = Fixture()
        val original = mutation(
            baseVersion = 2,
            createdAt = 1L,
            payload = """{"schemaVersion":1,"name":"Original"}""",
        )
        f.outbox.insert(original)

        // The server may have applied it before the app died.
        f.outbox.markInFlight(owner, setOf(original.mutationId))
        f.outbox.markTransmitted(owner, setOf(original.mutationId))
        f.outbox.recoverInFlight(owner)
        val recovered = f.outbox.findByMutationId(owner, original.mutationId)!!
        assertTrue(recovered.everTransmitted)
        assertEquals(SyncOutboxStatus.PENDING_UPLOAD, recovered.syncStatus)

        // A later local edit of the same record and operation must not replace
        // the payload under the original mutation ID.
        SyncOutboxWriter(f.outbox, SyncPayloadFactory(f.json)).upsertWorkout(
            owner,
            WorkoutEntity(
                name = "Edited",
                ownerFirebaseUid = owner,
                syncId = original.recordSyncId,
                serverVersion = 2,
            ),
        )
        val pending = f.outbox.findByStatus(owner, SyncOutboxStatus.PENDING_UPLOAD)
        assertEquals(2, pending.size)
        val storedOriginal = f.outbox.findByMutationId(owner, original.mutationId)!!
        assertEquals(original.mutationId, storedOriginal.mutationId)
        assertEquals(original.payload, storedOriginal.payload)
        assertEquals(original.baseVersion, storedOriginal.baseVersion)
        assertTrue(pending.any { it.mutationId != original.mutationId })

        // Retrying the original still sends the exact same request contents.
        f.api.pushBlock = { call ->
            response(call.mutations.map {
                SyncMutationResultDto(
                    mutationId = it.mutationId, syncId = it.syncId, status = "DUPLICATE",
                    originalStatus = "APPLIED", serverVersion = 2,
                    serverUpdatedAt = "2026-11-24T10:00:00Z",
                )
            })
        }
        val report = f.coordinator.pushOneBatch()

        assertEquals(1, report.duplicate)
        val sent = f.api.pushed.single().mutations.single()
        assertEquals(original.mutationId, sent.mutationId)
        assertEquals(original.operation, sent.operation)
        assertEquals(original.baseVersion, sent.baseVersion)
        assertEquals(Json.parseToJsonElement(original.payload!!), sent.payload)
        assertEquals(SyncOutboxStatus.ACKNOWLEDGED, f.outbox.findByMutationId(owner, original.mutationId)!!.syncStatus)
    }

    @Test
    fun `missing token aborts before the network call`() = runTest {
        val f = Fixture()
        val m = mutation()
        f.outbox.insert(m)
        f.tokens.token = null

        val report = f.coordinator.pushOneBatch()

        assertNotNull(report.error)
        assertTrue(f.api.pushed.isEmpty())
        assertEquals(SyncOutboxStatus.IN_FLIGHT, f.outbox.findByMutationId(owner, m.mutationId)!!.syncStatus)
    }

    @Test
    fun `account switch after token binding still sends the captured user token`() = runTest {
        val f = Fixture()
        val m = mutation()
        f.outbox.insert(m)
        f.api.pushBlock = { call ->
            response(call.mutations.map {
                SyncMutationResultDto(it.mutationId, it.syncId, "APPLIED", serverVersion = 1, serverUpdatedAt = "2026-11-24T10:00:00Z")
            })
        }
        f.tokens.hook = { f.ownerProvider.switchTo("user-b") }

        val report = f.coordinator.pushOneBatch()

        assertEquals(listOf(owner), f.tokens.requestedFor)
        assertTrue(f.api.authorizations.isEmpty())
        // The account switched before transmission: nothing is marked sent and
        // no request is made.
        assertEquals("user-b", f.ownerProvider.uid)
        assertTrue(report.ownerChanged)
        assertEquals(0, report.applied)
        assertEquals(SyncOutboxStatus.IN_FLIGHT, f.outbox.findByMutationId(owner, m.mutationId)!!.syncStatus)
    }

    @Test
    fun `duplicate of a conflict from the server remains blocking`() = runTest {
        val f = Fixture()
        val m = mutation()
        f.outbox.insert(m)
        f.api.pushBlock = {
            response(
                listOf(
                    SyncMutationResultDto(
                        mutationId = m.mutationId,
                        syncId = m.recordSyncId,
                        status = "DUPLICATE",
                        originalStatus = "CONFLICT",
                        errorCode = "VERSION_CONFLICT",
                        serverVersion = 3,
                    ),
                )
            )
        }

        val report = f.coordinator.pushOneBatch()

        assertEquals(1, report.conflicted)
        assertEquals(0, report.applied)
        assertEquals(0, report.duplicate)
        assertEquals(0, report.reconciled)
        assertEquals(SyncOutboxStatus.CONFLICTED, f.outbox.findByMutationId(owner, m.mutationId)!!.syncStatus)
    }

    @Test
    fun `later mutation is not selected while an earlier blocker is unresolved`() {
        listOf(
            SyncOutboxStatus.FAILED,
            SyncOutboxStatus.CONFLICTED,
            SyncOutboxStatus.REJECTED_TERMINAL,
        ).forEach { blockerStatus ->
            runTest {
                val f = Fixture()
                val syncId = UUID.randomUUID().toString()
                val blocker = mutation(syncId = syncId, status = blockerStatus, createdAt = 1L, outboxId = 1L)
                val later = mutation(syncId = syncId, createdAt = 2L, outboxId = 2L)
                f.outbox.insert(blocker)
                f.outbox.insert(later)
                f.api.pushBlock = { response(emptyList()) }

                val report = f.coordinator.pushOneBatch()

                assertEquals(0, report.sent)
                assertTrue(f.api.pushed.isEmpty())
                assertEquals(
                    SyncOutboxStatus.PENDING_UPLOAD,
                    f.outbox.findByMutationId(owner, later.mutationId)!!.syncStatus,
                )
            }
        }
    }

    @Test
    fun `blocking mutation on another record does not prevent an unrelated push`() = runTest {
        val f = Fixture()
        val blocker = mutation(status = SyncOutboxStatus.CONFLICTED)
        val unrelated = mutation()
        f.outbox.insert(blocker)
        f.outbox.insert(unrelated)
        f.api.pushBlock = { call ->
            response(call.mutations.map {
                SyncMutationResultDto(it.mutationId, it.syncId, "APPLIED", serverVersion = 1, serverUpdatedAt = "2026-11-24T10:00:00Z")
            })
        }

        val report = f.coordinator.pushOneBatch()

        assertEquals(1, report.sent)
        assertEquals(listOf(unrelated.mutationId), f.api.pushed.single().mutations.map { it.mutationId })
    }

    @Test
    fun `unmappable mutation becomes terminal and diagnosable without data loss`() = runTest {
        val f = Fixture()
        val broken = mutation(payload = "not-json")
        f.outbox.insert(broken)

        val report = f.coordinator.pushOneBatch()

        assertEquals(1, report.rejected)
        assertTrue(f.api.pushed.isEmpty())
        val stored = f.outbox.findByMutationId(owner, broken.mutationId)!!
        assertEquals(SyncOutboxStatus.REJECTED_TERMINAL, stored.syncStatus)
        assertEquals("INVALID_LOCAL_MUTATION", stored.terminalErrorCode)
        assertNotNull(stored.lastError)
        assertEquals(broken.payload, stored.payload)
        assertEquals(broken.baseVersion, stored.baseVersion)

        f.api.pushBlock = { call ->
            response(call.mutations.map {
                SyncMutationResultDto(it.mutationId, it.syncId, "APPLIED", serverVersion = 1, serverUpdatedAt = "2026-11-24T10:00:00Z")
            })
        }
        val second = f.coordinator.pushOneBatch()
        assertEquals(0, second.sent)
        assertEquals(0, f.api.pushed.size)
    }

    @Test
    fun `owner change during reconciliation is reported in push result`() = runTest {
        // Calls: 1 capture, 2 select, 3 pre-request, 4 acknowledge,
        // 5 unresolved check, 6 reconciliation transaction.
        val switching = TestOwner(owner).apply { switchAtCall = 7 }
        val f = Fixture(switching)
        val syncId = UUID.randomUUID().toString()
        val m = mutation(syncId = syncId)
        f.outbox.insert(m)
        f.staged.upsertIfNewer(
            owner, SyncEntityTypes.WORKOUT, syncId, 1, 1_700_000_000_000L, false,
            """{"schemaVersion":1,"name":"Remote","notes":"remote"}""", SyncStagedStatus.LOCAL_PENDING, 1L,
        )
        f.api.pushBlock = { response(listOf(result(m))) }

        val report = f.coordinator.pushOneBatch()

        assertEquals(1, report.applied)
        assertTrue(report.ownerChanged)
        assertEquals(0, report.reconciled)
        assertEquals(SyncOutboxStatus.ACKNOWLEDGED, f.outbox.findByMutationId(owner, m.mutationId)!!.syncStatus)
        assertEquals(
            SyncStagedStatus.LOCAL_PENDING,
            f.staged.find(owner, SyncEntityTypes.WORKOUT, syncId)!!.processingStatus,
        )
    }

    @Test
    fun `association is not pushed while its parent has an unresolved mutation`() = runTest {
        val f = Fixture()
        val workoutSyncId = UUID.randomUUID().toString()
        f.outbox.insert(
            mutation(syncId = workoutSyncId, status = SyncOutboxStatus.CONFLICTED, outboxId = 1L)
        )
        f.outbox.insert(
            mutation(
                syncId = UUID.randomUUID().toString(),
                entityType = SyncEntityTypes.WORKOUT_EXERCISE,
                outboxId = 2L,
                payload = """{"schemaVersion":1,"workoutSyncId":"$workoutSyncId","exerciseRef":{"kind":"PUBLIC","exerciseId":42},"orderIndex":0}""",
            )
        )
        f.api.pushBlock = { response(emptyList()) }

        val report = f.coordinator.pushOneBatch()

        assertEquals(0, report.sent)
        assertTrue(f.api.pushed.isEmpty())
    }

    @Test
    fun `parent records are ordered before associations in one batch`() = runTest {
        val f = Fixture()
        val workoutSyncId = UUID.randomUUID().toString()
        f.outbox.insert(mutation(syncId = workoutSyncId, outboxId = 1L, createdAt = 5L))
        f.outbox.insert(
            mutation(
                syncId = UUID.randomUUID().toString(),
                entityType = SyncEntityTypes.WORKOUT_EXERCISE,
                outboxId = 2L,
                createdAt = 1L,
                payload = """{"schemaVersion":1,"workoutSyncId":"$workoutSyncId","exerciseRef":{"kind":"PUBLIC","exerciseId":42},"orderIndex":0}""",
            )
        )
        f.api.pushBlock = { call ->
            response(call.mutations.map {
                SyncMutationResultDto(it.mutationId, it.syncId, "APPLIED", serverVersion = 1, serverUpdatedAt = "2026-11-24T10:00:00Z")
            })
        }

        val first = f.coordinator.pushOneBatch()

        // The parent is sent first; the association waits until the parent is
        // no longer an unresolved mutation.
        assertEquals(listOf(SyncEntityTypes.WORKOUT), first.sent.let { f.api.pushed.first().mutations.map { m -> m.entityType } })

        f.coordinator.pushOneBatch()
        assertEquals(SyncEntityTypes.WORKOUT_EXERCISE, f.api.pushed.last().mutations.single().entityType)
    }
}
