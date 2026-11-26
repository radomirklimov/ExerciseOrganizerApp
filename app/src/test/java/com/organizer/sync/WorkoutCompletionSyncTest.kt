package com.organizer.sync

import com.organizer.auth.OwnerProvider
import com.organizer.data.local.db.entities.WorkoutEntity
import com.organizer.data.repo.WorkoutCompletionRepository
import com.organizer.fakes.FakeExerciseDao
import com.organizer.fakes.FakeSyncOutboxDao
import com.organizer.fakes.FakeWorkoutCompletionDao
import com.organizer.fakes.FakeWorkoutDao
import com.organizer.fakes.ImmediateSyncTransactionRunner
import com.organizer.fakes.fakeOutboxWriter
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutCompletionSyncTest {
    @Test
    fun `deterministic identity stable for same account workout and date`() {
        val a = WorkoutCompletionIdentity.syncId("user-a", "workout-sync-1", 20260101)
        val b = WorkoutCompletionIdentity.syncId("user-a", "workout-sync-1", 20260101)
        assertEquals(a, b)
        assertNotEquals(a, WorkoutCompletionIdentity.syncId("user-b", "workout-sync-1", 20260101))
        assertNotEquals(a, WorkoutCompletionIdentity.syncId("user-a", "workout-sync-2", 20260101))
        assertNotEquals(a, WorkoutCompletionIdentity.syncId("user-a", "workout-sync-1", 20260102))
    }

    @Test
    fun `merge rule selects one complete snapshot`() {
        val s1 = WorkoutCompletionIdentity.Snapshot(5, 10, 100L)
        val s2 = WorkoutCompletionIdentity.Snapshot(8, 12, 200L)
        assertEquals(s2, WorkoutCompletionIdentity.selectWinner(s1, s2))
        assertEquals(s2, WorkoutCompletionIdentity.selectWinner(s2, s1))
        // tie by completedAt -> larger completedExercises
        assertEquals(WorkoutCompletionIdentity.Snapshot(8, 10, 100L), WorkoutCompletionIdentity.selectWinner(s1.copy(completedExercises = 8), s1))
    }

    @Test
    fun `payload matches contract and excludes ids and owner`() {
        val payload = SyncPayloadFactory(Json { ignoreUnknownKeys = true }).workoutCompletion(
            com.organizer.data.local.db.entities.WorkoutCompletionEntity(
                dateKey = 20260101, workoutId = 7L, completedExercises = 8, totalExercises = 10,
                completedAt = 1000L, ownerFirebaseUid = "user-a", syncId = "x",
            ),
            "workout-sync-1",
        )
        val obj = Json.parseToJsonElement(payload).jsonObject
        assertEquals(setOf("schemaVersion", "workoutSyncId", "dateKey", "completedExercises", "totalExercises", "completedAt"), obj.keys)
        assertEquals("workout-sync-1", obj["workoutSyncId"]!!.toString().trim('"'))
        assertTrue(!obj.containsKey("workoutId"))
        assertTrue(!obj.containsKey("ownerFirebaseUid"))
    }

    @Test
    fun `record completion is owner scoped and produces one upsert`() = runTest {
        val dao = FakeWorkoutCompletionDao()
        val workouts = FakeWorkoutDao()
        val outbox = FakeSyncOutboxDao()
        val owner = object : OwnerProvider() { override fun currentOwner() = "user-a" }
        val repo = WorkoutCompletionRepository(
            dao, workouts, outbox, owner, ImmediateSyncTransactionRunner(),
            fakeOutboxWriter(outbox).first, SyncPayloadFactory(Json { ignoreUnknownKeys = true }), SyncOperationLocks(),
        )
        workouts.insert(WorkoutEntity(workoutId = 1L, name = "W", ownerFirebaseUid = "user-a", syncId = "workout-sync-1"))
        repo.recordCompletion(20260101, 1L, 8, 10)
        assertEquals(1, dao.rows.value.size)
        assertEquals(1, outbox.entries.value.size)
        val syncId = WorkoutCompletionIdentity.syncId("user-a", "workout-sync-1", 20260101)
        assertEquals(syncId, dao.rows.value.single().syncId)
        assertEquals(syncId, outbox.entries.value.single().recordSyncId)
    }

    private suspend fun fixture(): Triple<FakeWorkoutCompletionDao, FakeSyncOutboxDao, SyncOutboxAcknowledger> {
        val dao = FakeWorkoutCompletionDao()
        val outbox = FakeSyncOutboxDao()
        val workouts = FakeWorkoutDao()
        workouts.insert(WorkoutEntity(workoutId = 1L, name = "W", ownerFirebaseUid = "user-a", syncId = "workout-sync-1"))
        val acker = SyncOutboxAcknowledger(
            object : OwnerProvider() { override fun currentOwner() = "user-a" },
            outbox, workouts, FakeExerciseDao(), com.organizer.fakes.FakeWorkoutExerciseDao(),
            com.organizer.fakes.FakeFavoriteExerciseDao(), null, dao, ImmediateSyncTransactionRunner(),
        )
        return Triple(dao, outbox, acker)
    }

    private fun conflictResult(mutationId: String, syncId: String, version: Long, payload: String) =
        com.organizer.data.remote.model.SyncMutationResultDto(
            mutationId = mutationId, syncId = syncId, status = "CONFLICT",
            serverVersion = version, serverPayload = Json.parseToJsonElement(payload).jsonObject,
        )

    @Test
    fun `conflict resolver local snapshot wins with new mutationId`() = runTest {
        val (dao, outbox, acker) = fixture()
        val syncId = WorkoutCompletionIdentity.syncId("user-a", "workout-sync-1", 20260101)
        dao.upsert(com.organizer.data.local.db.entities.WorkoutCompletionEntity(
            dateKey = 20260101, workoutId = 1L, completedExercises = 8, totalExercises = 10,
            completedAt = 300L, ownerFirebaseUid = "user-a", syncId = syncId,
        ))
        val m = com.organizer.data.local.db.entities.SyncOutboxEntity(
            mutationId = "m1", ownerFirebaseUid = "user-a", entityType = SyncEntityTypes.WORKOUT_COMPLETION,
            recordSyncId = syncId, operation = "UPSERT", payload = "{}", baseVersion = 0,
            syncStatus = SyncOutboxStatus.IN_FLIGHT, everTransmitted = true,
        )
        outbox.insert(m)
        val server = """{"schemaVersion":1,"workoutSyncId":"workout-sync-1","dateKey":20260101,"completedExercises":5,"totalExercises":10,"completedAt":"1970-01-01T00:00:00.100Z"}"""
        acker.acknowledge("user-a", listOf(conflictResult("m1", syncId, 2L, server)))
        val resolved = outbox.entries.value.first { it.mutationId != "m1" }
        assertTrue(resolved.mutationId != "m1")
        assertEquals(2L, resolved.baseVersion)
        assertEquals(8, Json.parseToJsonElement(resolved.payload!!).jsonObject["completedExercises"]!!.toString().toInt())
        val original = outbox.entries.value.first { it.mutationId == "m1" }
        assertEquals(SyncOutboxStatus.SUPERSEDED, original.syncStatus)
        assertEquals(true, original.everTransmitted)
        assertEquals(1, dao.rows.value.size)
    }

    @Test
    fun `conflict resolver server snapshot wins and updates local history`() = runTest {
        val (dao, outbox, acker) = fixture()
        val syncId = WorkoutCompletionIdentity.syncId("user-a", "workout-sync-1", 20260101)
        dao.upsert(com.organizer.data.local.db.entities.WorkoutCompletionEntity(
            dateKey = 20260101, workoutId = 1L, completedExercises = 5, totalExercises = 10,
            completedAt = 100L, ownerFirebaseUid = "user-a", syncId = syncId,
        ))
        outbox.insert(com.organizer.data.local.db.entities.SyncOutboxEntity(
            mutationId = "m2", ownerFirebaseUid = "user-a", entityType = SyncEntityTypes.WORKOUT_COMPLETION,
            recordSyncId = syncId, operation = "UPSERT", payload = "{}", baseVersion = 0,
            syncStatus = SyncOutboxStatus.IN_FLIGHT, everTransmitted = true,
        ))
        val server = """{"schemaVersion":1,"workoutSyncId":"workout-sync-1","dateKey":20260101,"completedExercises":9,"totalExercises":10,"completedAt":"1970-01-01T00:00:00.300Z"}"""
        acker.acknowledge("user-a", listOf(conflictResult("m2", syncId, 3L, server)))
        assertEquals(9, dao.rows.value.single().completedExercises)
        assertEquals(3L, dao.rows.value.single().serverVersion)
        assertEquals(2, outbox.entries.value.size)
    }

    @Test
    fun `malformed server payload preserves local history`() = runTest {
        val (dao, outbox, acker) = fixture()
        val syncId = WorkoutCompletionIdentity.syncId("user-a", "workout-sync-1", 20260101)
        dao.upsert(com.organizer.data.local.db.entities.WorkoutCompletionEntity(
            dateKey = 20260101, workoutId = 1L, completedExercises = 5, totalExercises = 10,
            completedAt = 100L, ownerFirebaseUid = "user-a", syncId = syncId,
        ))
        outbox.insert(com.organizer.data.local.db.entities.SyncOutboxEntity(
            mutationId = "m3", ownerFirebaseUid = "user-a", entityType = SyncEntityTypes.WORKOUT_COMPLETION,
            recordSyncId = syncId, operation = "UPSERT", payload = "{}", baseVersion = 0,
            syncStatus = SyncOutboxStatus.IN_FLIGHT, everTransmitted = true,
        ))
        val malformed = """{"schemaVersion":1,"workoutSyncId":"workout-sync-1","dateKey":20260101,"completedExercises":-1,"totalExercises":10,"completedAt":"bad"}"""
        acker.acknowledge("user-a", listOf(conflictResult("m3", syncId, 3L, malformed)))
        assertEquals(5, dao.rows.value.single().completedExercises)
        assertEquals(SyncOutboxStatus.CONFLICTED, outbox.entries.value.first { it.mutationId == "m3" }.syncStatus)
        assertEquals(1, outbox.entries.value.size)
    }
}
