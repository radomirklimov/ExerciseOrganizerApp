package com.organizer.sync

import com.organizer.auth.OwnerProvider
import com.organizer.data.local.db.entities.SyncOutboxEntity
import com.organizer.data.local.db.entities.WorkoutEntity
import com.organizer.data.remote.model.SyncMutationResultDto
import com.organizer.fakes.FakeExerciseDao
import com.organizer.fakes.FakeSyncOutboxDao
import com.organizer.fakes.FakeWorkoutDao
import com.organizer.fakes.ImmediateSyncTransactionRunner
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class SyncOutboxAcknowledgerTest {

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

    private class Fixture(val owner: TestOwner = TestOwner("user-a")) {
        val outbox = FakeSyncOutboxDao()
        val workouts = FakeWorkoutDao()
        val exercises = FakeExerciseDao()
        val acknowledger = SyncOutboxAcknowledger(owner, outbox, workouts, exercises, com.organizer.fakes.FakeWorkoutExerciseDao(), com.organizer.fakes.FakeFavoriteExerciseDao(), null, ImmediateSyncTransactionRunner())
    }

    private fun mutation(
        mutationId: String = UUID.randomUUID().toString(),
        syncId: String = UUID.randomUUID().toString(),
        status: String = SyncOutboxStatus.PENDING_UPLOAD,
        baseVersion: Long = 0,
    ) = SyncOutboxEntity(
        mutationId = mutationId,
        ownerFirebaseUid = "user-a",
        entityType = SyncEntityTypes.WORKOUT,
        recordSyncId = syncId,
        operation = "UPSERT",
        baseVersion = baseVersion,
        syncStatus = status,
    )

    private fun result(
        mutationId: String,
        syncId: String,
        status: String,
        version: Long = 1,
        errorCode: String? = null,
        originalStatus: String? = null,
    ) = SyncMutationResultDto(
        mutationId = mutationId,
        syncId = syncId,
        status = status,
        serverVersion = version,
        serverUpdatedAt = "2026-11-24T10:00:00Z",
        errorCode = errorCode,
        originalStatus = originalStatus,
    )

    private fun localWorkout(syncId: String, version: Long = 0, status: String = "PENDING_UPLOAD") =
        WorkoutEntity(
            workoutId = 1L,
            name = "Local",
            ownerFirebaseUid = "user-a",
            syncId = syncId,
            serverVersion = version,
            syncStatus = status,
        )

    @Test
    fun `APPLIED acknowledgement marks terminal and updates local metadata`() = runTest {
        val f = Fixture()
        val m = mutation()
        f.outbox.insert(m)
        f.workouts.seed(localWorkout(m.recordSyncId))

        val report = f.acknowledger.acknowledge("user-a", listOf(result(m.mutationId, m.recordSyncId, "APPLIED")))

        assertEquals(1, report.acknowledged)
        val stored = f.outbox.findByMutationId("user-a", m.mutationId)!!
        assertEquals(SyncOutboxStatus.ACKNOWLEDGED, stored.syncStatus)
        assertNotNull(stored.acknowledgedAt)
        assertEquals(1L, stored.serverVersion)
        val workout = f.workouts.findBySyncId("user-a", m.recordSyncId)!!
        assertEquals(1L, workout.serverVersion)
        assertEquals("SYNCED", workout.syncStatus)
        assertEquals("Local", workout.name)
    }

    @Test
    fun `DUPLICATE acknowledgement is treated as success`() = runTest {
        val f = Fixture()
        val m = mutation()
        f.outbox.insert(m)
        f.workouts.seed(localWorkout(m.recordSyncId))

        val report = f.acknowledger.acknowledge("user-a", listOf(result(m.mutationId, m.recordSyncId, "DUPLICATE")))

        assertEquals(1, report.acknowledged)
        assertEquals(SyncOutboxStatus.ACKNOWLEDGED, f.outbox.findByMutationId("user-a", m.mutationId)!!.syncStatus)
    }

    @Test
    fun `duplicate of APPLIED is acknowledged`() = runTest {
        val f = Fixture()
        val m = mutation()
        f.outbox.insert(m)
        f.workouts.seed(localWorkout(m.recordSyncId))

        val report = f.acknowledger.acknowledge(
            "user-a", listOf(result(m.mutationId, m.recordSyncId, "DUPLICATE", originalStatus = "APPLIED"))
        )

        assertEquals(1, report.acknowledged)
        assertEquals(SyncOutboxStatus.ACKNOWLEDGED, f.outbox.findByMutationId("user-a", m.mutationId)!!.syncStatus)
    }

    @Test
    fun `duplicate of CONFLICT stays blocking`() = runTest {
        val f = Fixture()
        val m = mutation()
        f.outbox.insert(m)
        f.workouts.seed(localWorkout(m.recordSyncId))

        val report = f.acknowledger.acknowledge(
            "user-a",
            listOf(result(m.mutationId, m.recordSyncId, "DUPLICATE", errorCode = "VERSION_CONFLICT", originalStatus = "CONFLICT")),
        )

        assertEquals(1, report.conflicted)
        assertEquals(0, report.acknowledged)
        val stored = f.outbox.findByMutationId("user-a", m.mutationId)!!
        assertEquals(SyncOutboxStatus.CONFLICTED, stored.syncStatus)
        assertEquals("VERSION_CONFLICT", stored.terminalErrorCode)
        assertTrue(
            f.outbox.hasBlockingMutationForRecord(
                "user-a", SyncEntityTypes.WORKOUT, m.recordSyncId, SyncOutboxStatus.BLOCKING
            )
        )
    }

    @Test
    fun `duplicate of REJECTED stays terminal blocking`() = runTest {
        val f = Fixture()
        val m = mutation()
        f.outbox.insert(m)

        val report = f.acknowledger.acknowledge(
            "user-a",
            listOf(result(m.mutationId, m.recordSyncId, "DUPLICATE", errorCode = "PAYLOAD_REQUIRED", originalStatus = "REJECTED")),
        )

        assertEquals(1, report.rejected)
        assertEquals(SyncOutboxStatus.REJECTED_TERMINAL, f.outbox.findByMutationId("user-a", m.mutationId)!!.syncStatus)
    }

    @Test
    fun `duplicate without originalStatus keeps legacy success behavior`() = runTest {
        val f = Fixture()
        val m = mutation()
        f.outbox.insert(m)

        val report = f.acknowledger.acknowledge("user-a", listOf(result(m.mutationId, m.recordSyncId, "DUPLICATE")))

        assertEquals(1, report.acknowledged)
        assertEquals(SyncOutboxStatus.ACKNOWLEDGED, f.outbox.findByMutationId("user-a", m.mutationId)!!.syncStatus)
    }

    @Test
    fun `duplicate with unknown originalStatus is invalid and stays blocking`() = runTest {
        val f = Fixture()
        val m = mutation()
        f.outbox.insert(m)

        val report = f.acknowledger.acknowledge(
            "user-a", listOf(result(m.mutationId, m.recordSyncId, "DUPLICATE", originalStatus = "MYSTERY"))
        )

        assertEquals(1, report.skippedInvalid)
        assertEquals(SyncOutboxStatus.PENDING_UPLOAD, f.outbox.findByMutationId("user-a", m.mutationId)!!.syncStatus)
    }

    @Test
    fun `CONFLICT and REJECTED remain blocking and preserve local content`() = runTest {
        val conflict = Fixture()
        val cm = mutation()
        conflict.outbox.insert(cm)
        conflict.workouts.seed(localWorkout(cm.recordSyncId))
        val conflictReport = conflict.acknowledger.acknowledge(
            "user-a", listOf(result(cm.mutationId, cm.recordSyncId, "CONFLICT", errorCode = "VERSION_CONFLICT"))
        )
        assertEquals(1, conflictReport.conflicted)
        assertEquals(SyncOutboxStatus.CONFLICTED, conflict.outbox.findByMutationId("user-a", cm.mutationId)!!.syncStatus)
        assertEquals("Local", conflict.workouts.findBySyncId("user-a", cm.recordSyncId)!!.name)
        assertTrue(
            conflict.outbox.hasBlockingMutationForRecord(
                "user-a", SyncEntityTypes.WORKOUT, cm.recordSyncId, SyncOutboxStatus.BLOCKING
            )
        )

        val rejected = Fixture()
        val rm = mutation()
        rejected.outbox.insert(rm)
        rejected.workouts.seed(localWorkout(rm.recordSyncId))
        val rejectedReport = rejected.acknowledger.acknowledge(
            "user-a", listOf(result(rm.mutationId, rm.recordSyncId, "REJECTED", errorCode = "INVALID_REQUEST"))
        )
        assertEquals(1, rejectedReport.rejected)
        assertEquals(SyncOutboxStatus.REJECTED_TERMINAL, rejected.outbox.findByMutationId("user-a", rm.mutationId)!!.syncStatus)
    }

    @Test
    fun `unknown mutation and terminal overwrite are skipped`() = runTest {
        val f = Fixture()
        val unknownReport = f.acknowledger.acknowledge("user-a", listOf(result("missing", UUID.randomUUID().toString(), "APPLIED")))
        assertEquals(1, unknownReport.skippedUnknown)

        val m = mutation(status = SyncOutboxStatus.CONFLICTED)
        f.outbox.insert(m)
        val secondReport = f.acknowledger.acknowledge("user-a", listOf(result(m.mutationId, m.recordSyncId, "APPLIED")))
        assertEquals(1, secondReport.skippedTerminal)
        assertEquals(SyncOutboxStatus.CONFLICTED, f.outbox.findByMutationId("user-a", m.mutationId)!!.syncStatus)
    }

    @Test
    fun `duplicate acknowledgement is idempotent`() = runTest {
        val f = Fixture()
        val m = mutation()
        f.outbox.insert(m)
        f.workouts.seed(localWorkout(m.recordSyncId))
        val r = result(m.mutationId, m.recordSyncId, "APPLIED")

        assertEquals(1, f.acknowledger.acknowledge("user-a", listOf(r)).acknowledged)
        val second = f.acknowledger.acknowledge("user-a", listOf(r))
        assertEquals(1, second.skippedTerminal)
        assertEquals(0, second.acknowledged)
    }

    @Test
    fun `older acknowledgement does not mark record synced while newer blocker remains`() = runTest {
        val f = Fixture()
        val syncId = UUID.randomUUID().toString()
        val old = mutation(syncId = syncId, baseVersion = 0)
        val newer = mutation(syncId = syncId, status = SyncOutboxStatus.PENDING_UPLOAD, baseVersion = 2)
        f.outbox.insert(old)
        f.outbox.insert(newer)
        f.workouts.seed(localWorkout(syncId, version = 0, status = "PENDING_UPLOAD"))

        f.acknowledger.acknowledge("user-a", listOf(result(old.mutationId, syncId, "APPLIED", version = 1)))

        val workout = f.workouts.findBySyncId("user-a", syncId)!!
        assertEquals(1L, workout.serverVersion)
        assertEquals("PENDING_UPLOAD", workout.syncStatus)
    }

    @Test
    fun `server version never decreases`() = runTest {
        val f = Fixture()
        val m = mutation()
        f.outbox.insert(m)
        f.workouts.seed(localWorkout(m.recordSyncId, version = 5, status = "SYNCED"))

        f.acknowledger.acknowledge("user-a", listOf(result(m.mutationId, m.recordSyncId, "APPLIED", version = 2)))

        assertEquals(5L, f.workouts.findBySyncId("user-a", m.recordSyncId)!!.serverVersion)
    }

    @Test
    fun `owner isolation and in-transaction owner change abort acknowledgement`() = runTest {
        val foreign = Fixture()
        val fm = mutation().copy(ownerFirebaseUid = "user-b")
        foreign.outbox.insert(fm)
        assertEquals(1, foreign.acknowledger.acknowledge("user-a", listOf(result(fm.mutationId, fm.recordSyncId, "APPLIED"))).skippedUnknown)

        val switchingOwner = TestOwner("user-a").apply { changeAtCall = 1 }
        val f = Fixture(switchingOwner)
        val m = mutation()
        f.outbox.insert(m)
        val report = f.acknowledger.acknowledge("user-a", listOf(result(m.mutationId, m.recordSyncId, "APPLIED")))
        assertTrue(report.ownerChanged)
        assertEquals(SyncOutboxStatus.PENDING_UPLOAD, f.outbox.findByMutationId("user-a", m.mutationId)!!.syncStatus)
    }
}