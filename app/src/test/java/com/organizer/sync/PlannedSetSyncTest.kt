package com.organizer.sync

import com.organizer.auth.OwnerProvider
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.local.db.entities.PlannedSetEntity
import com.organizer.data.local.db.entities.SyncStagedStatus
import com.organizer.data.local.db.entities.WorkoutEntity
import com.organizer.data.repo.PlannedSetRepository
import com.organizer.fakes.FakeExerciseDao
import com.organizer.fakes.FakePlannedSetDao
import com.organizer.fakes.FakeSyncOutboxDao
import com.organizer.fakes.FakeWorkoutDao
import com.organizer.fakes.ImmediateSyncTransactionRunner
import com.organizer.fakes.fakeOutboxWriter
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannedSetSyncTest {
    private class Owner : OwnerProvider() {
        @Volatile var uid = "user-a"
        var calls = 0
        var switchAtCall = -1
        override fun currentOwner(): String { calls++; if (calls == switchAtCall) uid = "user-b"; return uid }
    }

    private class Fixture(val owner: Owner = Owner()) {
        val dao = FakePlannedSetDao()
        val workouts = FakeWorkoutDao()
        val exercises = FakeExerciseDao()
        val outbox = FakeSyncOutboxDao()
        val repo = PlannedSetRepository(
            dao, workouts, exercises, outbox, owner, ImmediateSyncTransactionRunner(),
            fakeOutboxWriter(outbox).first, SyncPayloadFactory(Json { ignoreUnknownKeys = true }), SyncOperationLocks(),
        )
        suspend fun seed(ownerUid: String = "user-a") {
            workouts.insert(WorkoutEntity(workoutId = 1L, name = "W", ownerFirebaseUid = ownerUid, syncId = "workout-sync-1"))
            exercises.seed(ExerciseEntity(exerciseId = 10L, name = "E", instructions = emptyList(), imageUrls = emptyList(), isCustom = false, ownerFirebaseUid = "public", publicExerciseId = 42))
        }
    }

    @Test
    fun `payload uses tagged exercise ref and excludes local ids and owner`() {
        val set = PlannedSetEntity(workoutId = 1L, exerciseId = 10L, setNumber = 1, reps = 10, weight = 20.0, ownerFirebaseUid = "user-a")
        val exercise = ExerciseEntity(exerciseId = 10L, name = "E", instructions = emptyList(), imageUrls = emptyList(), isCustom = false, ownerFirebaseUid = "public", publicExerciseId = 42)
        val raw = SyncPayloadFactory(Json { ignoreUnknownKeys = true }).plannedSet(set, "workout-sync-1", exercise)
        val obj = Json.parseToJsonElement(raw).jsonObject
        assertEquals(setOf("schemaVersion", "workoutSyncId", "exerciseRef", "setNumber", "reps", "weight"), obj.keys)
        assertEquals("PUBLIC", obj["exerciseRef"]!!.jsonObject["kind"]!!.toString().trim('"'))
        assertTrue(!obj.containsKey("workoutId"))
        assertTrue(!obj.containsKey("exerciseId"))
        assertTrue(!obj.containsKey("ownerFirebaseUid"))
    }

    @Test
    fun `add produces one row and one upsert with stable syncId`() = runTest {
        val f = Fixture(); f.seed()
        f.repo.addSet(1L, 10L, 1, 10, 20.0, null)
        assertEquals(1, f.dao.rows.value.size)
        assertEquals(1, f.outbox.entries.value.size)
        val row = f.dao.rows.value.single()
        assertNotNull(row.syncId)
        assertEquals("UPSERT", f.outbox.entries.value.single().operation)
        assertEquals(row.syncId, f.outbox.entries.value.single().recordSyncId)
    }

    @Test
    fun `update preserves syncId and reorder keeps identities`() = runTest {
        val f = Fixture(); f.seed()
        f.repo.addSet(1L, 10L, 1, 10, 20.0, null)
        f.repo.addSet(1L, 10L, 2, 8, 25.0, null)
        val ids = f.dao.rows.value.map { it.syncId }.toSet()
        f.repo.reorderSets(1L, 10L, f.dao.rows.value.sortedBy { it.setNumber }.reversed())
        assertEquals(ids, f.dao.rows.value.map { it.syncId }.toSet())
        assertEquals(2, f.dao.rows.value.size)
    }

    @Test
    fun `guest writes remain local only`() = runTest {
        val f = Fixture(Owner().apply { uid = OwnerProvider.GUEST }); f.seed(OwnerProvider.GUEST)
        f.repo.addSet(1L, 10L, 1, 10, 20.0, null)
        f.repo.deleteSet(1L, 10L, 1)
        assertEquals(0, f.outbox.entries.value.size)
        assertEquals(1, f.dao.rows.value.size)
    }

    @Test
    fun `account switch aborts write`() = runTest {
        val f = Fixture(Owner().apply { switchAtCall = 2 }); f.seed()
        val thrown = runCatching { f.repo.addSet(1L, 10L, 1, 10, 20.0, null) }.exceptionOrNull()
        assertTrue(thrown is OwnerChangedDuringLocalWriteException)
        assertTrue(f.dao.rows.value.isEmpty())
        assertTrue(f.outbox.entries.value.isEmpty())
    }

    private class ApplierFixture {
        val dao = FakePlannedSetDao()
        val workouts = FakeWorkoutDao()
        val exercises = FakeExerciseDao()
        val links = com.organizer.fakes.FakeWorkoutExerciseDao()
        val outbox = FakeSyncOutboxDao()
        val staged = com.organizer.fakes.FakeSyncStagedDao()
        val applier = SyncStagedApplier(
            object : OwnerProvider() { override fun currentOwner() = "user-a" },
            workouts, exercises, outbox, links, null, null, null, null, dao,
            staged, ImmediateSyncTransactionRunner(), Json { ignoreUnknownKeys = true },
        )
        suspend fun seed(withLink: Boolean = true) {
            workouts.insert(WorkoutEntity(workoutId = 1L, name = "W", ownerFirebaseUid = "user-a", syncId = "workout-sync-1"))
            exercises.seed(ExerciseEntity(exerciseId = 10L, name = "E", instructions = emptyList(), imageUrls = emptyList(), isCustom = false, ownerFirebaseUid = "public", publicExerciseId = 42))
            if (withLink) links.insert(com.organizer.data.local.db.entities.WorkoutExerciseEntity(workoutId = 1L, exerciseId = 10L, ownerFirebaseUid = "user-a", syncId = "link-1", orderIndex = 0))
        }
        suspend fun stage(version: Long, deleted: Boolean, payload: String?) {
            staged.upsertIfNewer("user-a", SyncEntityTypes.PLANNED_SET, "ps-1", version, 100L * version, deleted, payload, SyncStagedStatus.PENDING, 100L * version)
        }
        fun record() = staged.records.value.single()
    }

    private val plannedPayload = """{"schemaVersion":1,"workoutSyncId":"workout-sync-1","exerciseRef":{"kind":"PUBLIC","exerciseId":42},"setNumber":1,"reps":10,"weight":20.0}"""

    @Test
    fun `pull applies valid planned set when dependencies exist and creates no outbox`() = runTest {
        val f = ApplierFixture(); f.seed(); f.stage(1, false, plannedPayload)
        assertEquals(StagedApplyResult.APPLIED, f.applier.process("user-a", f.record()))
        assertEquals(1, f.dao.rows.value.size)
        assertEquals(0, f.outbox.entries.value.size)
    }

    @Test
    fun `missing association leaves durable waiting`() = runTest {
        val f = ApplierFixture(); f.seed(withLink = false); f.stage(1, false, plannedPayload)
        assertEquals(StagedApplyResult.WAITING_DEPENDENCY, f.applier.process("user-a", f.record()))
        assertEquals(SyncStagedStatus.WAITING_DEPENDENCY, f.record().processingStatus)
        assertTrue(f.dao.rows.value.isEmpty())
    }

    @Test
    fun `tombstone applies by syncId and stale upsert cannot resurrect`() = runTest {
        val f = ApplierFixture(); f.seed()
        f.dao.upsert(com.organizer.data.local.db.entities.PlannedSetEntity(workoutId = 1L, exerciseId = 10L, setNumber = 1, ownerFirebaseUid = "user-a", syncId = "ps-1", serverVersion = 3))
        f.stage(4, true, plannedPayload)
        assertEquals(StagedApplyResult.APPLIED, f.applier.process("user-a", f.record()))
        assertNotNull(f.dao.rows.value.single().deletedAt)
        f.stage(2, false, plannedPayload)
        assertNotNull(f.dao.rows.value.single().deletedAt)
    }
}
