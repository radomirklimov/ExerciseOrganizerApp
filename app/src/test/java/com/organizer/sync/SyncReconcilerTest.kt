package com.organizer.sync

import com.organizer.auth.OwnerProvider
import com.organizer.data.local.db.entities.SyncOutboxEntity
import com.organizer.data.local.db.entities.SyncStagedRecordEntity
import com.organizer.data.local.db.entities.SyncStagedStatus
import com.organizer.fakes.FakeExerciseDao
import com.organizer.fakes.FakeSyncOutboxDao
import com.organizer.fakes.FakeSyncStagedDao
import com.organizer.fakes.FakeWorkoutDao
import com.organizer.fakes.ImmediateSyncTransactionRunner
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class SyncReconcilerTest {

    private val owner = "user-a"

    private class TestOwner(initial: String) : OwnerProvider() {
        @Volatile var uid = initial
        var calls = 0
        var changeAtCall = -1

        override fun currentOwner(): String {
            calls++
            if (calls == changeAtCall) uid = "user-b"
            return uid
        }
    }

    private class Fixture(val ownerProvider: TestOwner = TestOwner("user-a")) {
        val outbox = FakeSyncOutboxDao()
        val staged = FakeSyncStagedDao()
        val workouts = FakeWorkoutDao()
        val exercises = FakeExerciseDao()
        val runner = ImmediateSyncTransactionRunner()
        val applier = SyncStagedApplier(ownerProvider, workouts, exercises, outbox, com.organizer.fakes.FakeWorkoutExerciseDao(), com.organizer.fakes.FakeFavoriteExerciseDao(), null, null, null, staged, runner, Json { ignoreUnknownKeys = true })
        val reconciler = SyncReconciler(ownerProvider, outbox, staged, applier, runner)
    }

    private fun stagedRecord(
        syncId: String = UUID.randomUUID().toString(),
        status: String = SyncStagedStatus.LOCAL_PENDING,
        deleted: Boolean = false,
        version: Long = 1,
    ) = SyncStagedRecordEntity(
        ownerFirebaseUid = owner,
        entityType = SyncEntityTypes.WORKOUT,
        syncId = syncId,
        serverVersion = version,
        serverUpdatedAt = 1_700_000_000_000L,
        deleted = deleted,
        payload = """{"schemaVersion":1,"name":"Remote","notes":"remote"}""",
        processingStatus = status,
    )

    private fun mutation(syncId: String, status: String) = SyncOutboxEntity(
        mutationId = UUID.randomUUID().toString(),
        ownerFirebaseUid = owner,
        entityType = SyncEntityTypes.WORKOUT,
        recordSyncId = syncId,
        operation = "UPSERT",
        syncStatus = status,
    )

    private suspend fun Fixture.stage(record: SyncStagedRecordEntity) {
        staged.upsertIfNewer(
            record.ownerFirebaseUid, record.entityType, record.syncId,
            record.serverVersion, record.serverUpdatedAt, record.deleted,
            record.payload, record.processingStatus, record.createdAt,
        )
    }

    @Test
    fun `record with no remaining blocker is reconciled`() = runTest {
        val f = Fixture()
        val record = stagedRecord()
        f.stage(record)

        val result = f.reconciler.reconcile()

        assertEquals(1, result.applied)
        assertEquals(1, result.reconsidered)
        assertEquals(SyncStagedStatus.APPLIED, f.staged.find(owner, SyncEntityTypes.WORKOUT, record.syncId)!!.processingStatus)
        assertNotNull(f.workouts.findBySyncId(owner, record.syncId))
    }

    @Test
    fun `blocking statuses keep records local pending`() = runTest {
        listOf(
            SyncOutboxStatus.PENDING_UPLOAD,
            SyncOutboxStatus.IN_FLIGHT,
            SyncOutboxStatus.FAILED,
            SyncOutboxStatus.CONFLICTED,
            SyncOutboxStatus.REJECTED_TERMINAL,
        ).forEach { status ->
            val f = Fixture()
            val record = stagedRecord()
            f.stage(record)
            f.outbox.insert(mutation(record.syncId, status))

            val result = f.reconciler.reconcile()

            assertEquals(1, result.stillBlocked)
            assertEquals(SyncStagedStatus.LOCAL_PENDING, f.staged.find(owner, SyncEntityTypes.WORKOUT, record.syncId)!!.processingStatus)
        }
    }

    @Test
    fun `acknowledged mutation does not block reconciliation`() = runTest {
        val f = Fixture()
        val record = stagedRecord()
        f.stage(record)
        f.outbox.insert(mutation(record.syncId, SyncOutboxStatus.ACKNOWLEDGED))

        assertEquals(1, f.reconciler.reconcile().applied)
    }

    @Test
    fun `reconciliation is idempotent and ignores non local pending records`() = runTest {
        val f = Fixture()
        val record = stagedRecord()
        f.stage(record)
        assertEquals(1, f.reconciler.reconcile().applied)
        assertEquals(0, f.reconciler.reconcile().reconsidered)

        f.stage(stagedRecord(status = SyncStagedStatus.FAILED))
        f.stage(stagedRecord(status = SyncStagedStatus.UNSUPPORTED))
        f.stage(stagedRecord(status = SyncStagedStatus.UNSUPPORTED_SCHEMA))
        assertEquals(0, f.reconciler.reconcile().reconsidered)
    }

    @Test
    fun `tombstone is reconciled safely`() = runTest {
        val f = Fixture()
        val record = stagedRecord(deleted = true, version = 3)
        f.stage(record)
        f.workouts.seed(
            com.organizer.data.local.db.entities.WorkoutEntity(
                workoutId = 1L, name = "Local", ownerFirebaseUid = owner,
                syncId = record.syncId, serverVersion = 1,
            )
        )

        val result = f.reconciler.reconcile()

        assertEquals(1, result.applied)
        val workout = f.workouts.findBySyncId(owner, record.syncId)!!
        assertNotNull(workout.deletedAt)
        assertEquals(null, workout.normalizedName)
        assertEquals(3L, workout.serverVersion)
    }

    @Test
    fun `guest reconciliation is rejected`() = runTest {
        val f = Fixture(TestOwner(OwnerProvider.GUEST))
        val record = stagedRecord()
        f.stage(record)
        assertTrue(f.reconciler.reconcile().ownerChanged == false)
        assertEquals(SyncStagedStatus.LOCAL_PENDING, f.staged.find(owner, SyncEntityTypes.WORKOUT, record.syncId)!!.processingStatus)
    }

    @Test
    fun `account switch inside transaction aborts reconciliation`() = runTest {
        val switching = TestOwner(owner).apply { changeAtCall = 2 }
        val f = Fixture(switching)
        val record = stagedRecord()
        f.stage(record)

        val result = f.reconciler.reconcile()

        assertTrue(result.ownerChanged)
        assertEquals(SyncStagedStatus.LOCAL_PENDING, f.staged.find(owner, SyncEntityTypes.WORKOUT, record.syncId)!!.processingStatus)
    }
}