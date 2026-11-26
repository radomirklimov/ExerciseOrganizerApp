package com.organizer.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.organizer.auth.OwnerProvider
import com.organizer.data.local.db.entities.SyncOutboxEntity
import com.organizer.data.remote.SyncApi
import com.organizer.data.remote.model.SyncChangeRecordDto
import com.organizer.data.remote.model.SyncChangesResponseDto
import com.organizer.data.remote.model.SyncPushRequestDto
import com.organizer.data.remote.model.SyncPushResponseDto
import com.organizer.sync.RoomSyncTransactionRunner
import com.organizer.sync.SyncApiClient
import com.organizer.sync.SyncContractMapper
import com.organizer.sync.SyncManager
import com.organizer.sync.SyncOperationLocks
import com.organizer.sync.SyncOutboxAcknowledger
import com.organizer.sync.SyncOutboxStatus
import com.organizer.sync.SyncPushCoordinator
import com.organizer.sync.SyncReconciler
import com.organizer.sync.SyncStagedApplier
import com.organizer.sync.SyncTokenProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SyncMutualExclusionInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val owner = "user-a"
    private val json = Json { ignoreUnknownKeys = true }

    private class TestOwner : OwnerProvider() {
        override fun currentOwner(): String = "user-a"
    }

    private class Token : SyncTokenProvider {
        override suspend fun idTokenFor(owner: String): String? = "captured-token"
    }

    private class FakeApi : SyncApi {
        var changesBlock: suspend (String?, Int) -> SyncChangesResponseDto = { _, _ -> emptyPage() }
        var pushBlock: suspend (SyncPushRequestDto) -> SyncPushResponseDto = { response(it) }

        override suspend fun changes(cursor: String?, limit: Int, authorization: String): SyncChangesResponseDto =
            changesBlock(cursor, limit)

        override suspend fun push(request: SyncPushRequestDto, authorization: String): SyncPushResponseDto =
            pushBlock(request)

        private fun emptyPage() = SyncChangesResponseDto(
            emptyList(), "cursor-1", false, "2026-11-24T10:00:01Z",
        )

        private fun response(request: SyncPushRequestDto) = SyncPushResponseDto(
            results = request.mutations.map {
                com.organizer.data.remote.model.SyncMutationResultDto(
                    mutationId = it.mutationId,
                    syncId = it.syncId,
                    status = "APPLIED",
                    serverVersion = 1,
                    serverUpdatedAt = "2026-11-24T10:00:00Z",
                )
            },
            serverTime = "2026-11-24T10:00:00Z",
        )
    }

    private fun openDb() = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()

    private fun mutation() = SyncOutboxEntity(
        mutationId = UUID.randomUUID().toString(),
        ownerFirebaseUid = owner,
        entityType = "WORKOUT",
        recordSyncId = UUID.randomUUID().toString(),
        operation = "UPSERT",
        payload = """{"schemaVersion":1,"name":"Local"}""",
        syncStatus = SyncOutboxStatus.PENDING_UPLOAD,
    )

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
    fun pushAndPullAreSerializedForTheSameOwner() = runBlocking {
        val db = openDb()
        try {
            db.syncOutboxDao.insert(mutation())
            val pushApi = FakeApi()
            val pullApi = FakeApi()
            val pushEntered = CompletableDeferred<Unit>()
            val releasePush = CompletableDeferred<Unit>()
            val pullEntered = CompletableDeferred<Unit>()
            pushApi.pushBlock = {
                pushEntered.complete(Unit)
                releasePush.await()
                it.let { request ->
                    SyncPushResponseDto(
                        results = request.mutations.map { m ->
                            com.organizer.data.remote.model.SyncMutationResultDto(
                                mutationId = m.mutationId, syncId = m.syncId, status = "APPLIED",
                                serverVersion = 1, serverUpdatedAt = "2026-11-24T10:00:00Z",
                            )
                        },
                        serverTime = "2026-11-24T10:00:00Z",
                    )
                }
            }
            pullApi.changesBlock = { _, _ ->
                pullEntered.complete(Unit)
                page(UUID.randomUUID().toString())
            }

            val locks = SyncOperationLocks()
            val ownerProvider = TestOwner()
            val runner = RoomSyncTransactionRunner(db)
            val acknowledger = SyncOutboxAcknowledger(
                ownerProvider, db.syncOutboxDao, db.workoutDao, db.exerciseDao, db.workoutExerciseDao, db.favoriteExerciseDao, null, null, runner,
            )
            val applier = SyncStagedApplier(
                ownerProvider, db.workoutDao, db.exerciseDao, db.syncOutboxDao,
                db.workoutExerciseDao, db.favoriteExerciseDao, null, null, null, db.syncStagedDao, runner, json,
            )
            val reconciler = SyncReconciler(
                ownerProvider, db.syncOutboxDao, db.syncStagedDao, applier, runner,
            )
            val coordinator = SyncPushCoordinator(
                ownerProvider, Token(), db.syncOutboxDao, SyncApiClient(pushApi, json),
                SyncContractMapper(), acknowledger, reconciler, runner, locks,
            )
            val manager = SyncManager(
                ownerProvider, Token(), SyncApiClient(pullApi, json), SyncContractMapper(),
                runner, db.syncCursorDao, db.syncStagedDao, applier, locks,
            )

            val pushJob = async { coordinator.pushOneBatch() }
            pushEntered.await()
            // UNDISPATCHED runs pullOnePage immediately on this thread until it
            // suspends on the shared mutex. Reaching this line therefore proves
            // pull actually attempted lock acquisition while push held the lock.
            val pullJob = async(start = CoroutineStart.UNDISPATCHED) { manager.pullOnePage() }
            assertFalse(pullEntered.isCompleted)
            releasePush.complete(Unit)

            val pushResult = pushJob.await()
            val pullResult = pullJob.await()
            assertEquals(1, pushResult.sent)
            assertEquals(1, pullResult.applied)
            assertTrue(pullEntered.isCompleted)
        } finally {
            db.close()
        }
    }

    @Test
    fun blockerRowPreventsSameRecordPush() = runBlocking {
        val db = openDb()
        try {
            val syncId = UUID.randomUUID().toString()
            db.syncOutboxDao.insert(
                mutation().copy(
                    recordSyncId = syncId,
                    syncStatus = SyncOutboxStatus.CONFLICTED,
                )
            )
            db.syncOutboxDao.insert(mutation().copy(recordSyncId = syncId))

            val api = FakeApi()
            val runner = RoomSyncTransactionRunner(db)
            val ownerProvider = TestOwner()
            val acknowledger = SyncOutboxAcknowledger(
                ownerProvider, db.syncOutboxDao, db.workoutDao, db.exerciseDao, db.workoutExerciseDao, db.favoriteExerciseDao, null, null, runner,
            )
            val applier = SyncStagedApplier(
                ownerProvider, db.workoutDao, db.exerciseDao, db.syncOutboxDao,
                db.workoutExerciseDao, db.favoriteExerciseDao, null, null, null, db.syncStagedDao, runner, json,
            )
            val coordinator = SyncPushCoordinator(
                ownerProvider, Token(), db.syncOutboxDao, SyncApiClient(api, json),
                SyncContractMapper(), acknowledger,
                SyncReconciler(ownerProvider, db.syncOutboxDao, db.syncStagedDao, applier, runner),
                runner, SyncOperationLocks(),
            )

            val result = coordinator.pushOneBatch()

            assertEquals(0, result.sent)
            assertEquals(2, db.syncOutboxDao.getPending(owner).size)
        } finally {
            db.close()
        }
    }
}
