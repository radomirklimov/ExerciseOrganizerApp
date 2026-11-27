package com.organizer.sync

import com.organizer.auth.OwnerProvider
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.local.db.entities.PlannedSetEntity
import com.organizer.data.local.db.entities.SyncOutboxEntity
import com.organizer.data.local.db.entities.WorkoutEntity
import com.organizer.data.remote.model.SyncMutationResultDto
import com.organizer.fakes.FakeExerciseDao
import com.organizer.fakes.FakeFavoriteExerciseDao
import com.organizer.fakes.FakePlannedSetDao
import com.organizer.fakes.FakeSyncOutboxDao
import com.organizer.fakes.FakeWorkoutDao
import com.organizer.fakes.FakeWorkoutExerciseDao
import com.organizer.fakes.ImmediateSyncTransactionRunner
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannedSetAckBlockingTest {
    private class Fixture {
        val dao = FakePlannedSetDao()
        val outbox = FakeSyncOutboxDao()
        val workouts = FakeWorkoutDao()
        val exercises = FakeExerciseDao()
        val acker = SyncOutboxAcknowledger(
            object : OwnerProvider() { override fun currentOwner() = "user-a" },
            outbox, workouts, exercises, FakeWorkoutExerciseDao(), FakeFavoriteExerciseDao(),
            null, null, dao, ImmediateSyncTransactionRunner(),
        )

        suspend fun seed() {
            workouts.insert(WorkoutEntity(workoutId = 1L, name = "W", ownerFirebaseUid = "user-a", syncId = "workout-sync-1"))
            exercises.seed(ExerciseEntity(exerciseId = 10L, name = "E", instructions = emptyList(), imageUrls = emptyList(), isCustom = false, ownerFirebaseUid = "public", publicExerciseId = 42))
            dao.upsert(PlannedSetEntity(workoutId = 1L, exerciseId = 10L, setNumber = 1, ownerFirebaseUid = "user-a", syncId = "ps-1"))
        }

        fun mutation(id: String, status: String) = SyncOutboxEntity(
            mutationId = id, ownerFirebaseUid = "user-a", entityType = SyncEntityTypes.PLANNED_SET,
            recordSyncId = "ps-1", operation = "UPSERT", baseVersion = 0, payload = "{}",
            syncStatus = status,
        )

        fun applied(id: String, version: Long) = SyncMutationResultDto(
            mutationId = id, syncId = "ps-1", status = "APPLIED", serverVersion = version,
        )
    }

    @Test
    fun `acknowledging one mutation leaves entity unsynced while another blocks`() = runTest {
        val f = Fixture(); f.seed()
        f.outbox.insert(f.mutation("m1", SyncOutboxStatus.IN_FLIGHT))
        f.outbox.insert(f.mutation("m2", SyncOutboxStatus.PENDING_UPLOAD))

        assertTrue(
            f.outbox.hasBlockingMutationForRecord(
                "user-a", SyncEntityTypes.PLANNED_SET, "ps-1", SyncOutboxStatus.BLOCKING
            )
        )

        assertEquals("LOCAL_ONLY", f.dao.rows.value.single().syncStatus)
        f.acker.acknowledge("user-a", listOf(f.applied("m1", 4L)))

        assertEquals(SyncOutboxStatus.ACKNOWLEDGED, f.outbox.entries.value.first { it.mutationId == "m1" }.syncStatus)
        assertEquals(SyncOutboxStatus.PENDING_UPLOAD, f.outbox.entries.value.first { it.mutationId == "m2" }.syncStatus)
        assertTrue(
            f.outbox.hasBlockingMutationForRecord(
                "user-a", SyncEntityTypes.PLANNED_SET, "ps-1", SyncOutboxStatus.BLOCKING
            )
        )
        // Blocking mutation remains and entity must not be fully synced.
        assertTrue(f.dao.rows.value.single().syncStatus != "SYNCED")
        assertEquals(4L, f.dao.rows.value.single().serverVersion)
    }

    @Test
    fun `entity becomes synced after all blocking mutations are resolved`() = runTest {
        val f = Fixture(); f.seed()
        f.outbox.insert(f.mutation("m1", SyncOutboxStatus.IN_FLIGHT))
        f.outbox.insert(f.mutation("m2", SyncOutboxStatus.PENDING_UPLOAD))

        f.acker.acknowledge("user-a", listOf(f.applied("m1", 4L)))
        assertTrue(f.dao.rows.value.single().syncStatus != "SYNCED")

        // Resolve the remaining mutation.
        f.acker.acknowledge("user-a", listOf(f.applied("m2", 5L)))
        assertEquals("SYNCED", f.dao.rows.value.single().syncStatus)
        assertEquals(5L, f.dao.rows.value.single().serverVersion)
    }

    @Test
    fun `duplicate acknowledgement is nonblocking and preserves terminal outcome`() = runTest {
        val f = Fixture(); f.seed()
        f.outbox.insert(f.mutation("m1", SyncOutboxStatus.PENDING_UPLOAD))
        val result = SyncMutationResultDto(
            mutationId = "m1", syncId = "ps-1", status = "DUPLICATE", serverVersion = 3,
            originalStatus = "APPLIED",
        )
        f.acker.acknowledge("user-a", listOf(result))
        assertEquals(SyncOutboxStatus.ACKNOWLEDGED, f.outbox.entries.value.single().syncStatus)
        assertEquals(3L, f.dao.rows.value.single().serverVersion)
        // Late conflicting result cannot overwrite terminal state.
        f.acker.acknowledge("user-a", listOf(f.applied("m1", 9L)))
        assertEquals(3L, f.dao.rows.value.single().serverVersion)
    }
}
