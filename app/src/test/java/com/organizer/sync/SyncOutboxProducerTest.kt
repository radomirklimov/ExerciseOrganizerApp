package com.organizer.sync

import com.organizer.auth.OwnerProvider
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.local.db.entities.SyncOutboxEntity
import com.organizer.data.local.db.entities.WorkoutEntity
import com.organizer.data.media.MediaDownloadQueue
import com.organizer.data.remote.repo.ExerciseRemoteDataSource
import com.organizer.data.repo.ExerciseRepository
import com.organizer.data.repo.FileRepository
import com.organizer.data.repo.WorkoutRepository
import com.organizer.fakes.FakeApiService
import com.organizer.fakes.FakeExerciseDao
import com.organizer.fakes.FakeFileRemote
import com.organizer.fakes.FakeFileStore
import com.organizer.fakes.FakeSyncOutboxDao
import com.organizer.fakes.FakeWorkoutDao
import com.organizer.fakes.ImmediateSyncTransactionRunner
import com.organizer.fakes.fakeOutboxWriter
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncOutboxProducerTest {

    private val json = Json { ignoreUnknownKeys = true }

    private class FixedOwner(private val uid: String) : OwnerProvider() {
        override fun currentOwner(): String = uid
    }

    private class MutableOwner(initial: String) : OwnerProvider() {
        @Volatile var uid = initial
        override fun currentOwner(): String = uid
    }

    private class SwitchInsideRunner(private val owner: MutableOwner) : SyncTransactionRunner {
        override suspend fun <T> run(block: suspend () -> T): T {
            owner.uid = "user-b"
            return block()
        }
    }

    private fun exerciseRepo(
        dao: FakeExerciseDao,
        owner: String,
        outboxDao: FakeSyncOutboxDao,
        scope: TestScope,
    ): ExerciseRepository {
        val store = FakeFileStore()
        val remote = FakeFileRemote()
        val queue = MediaDownloadQueue(store, remote, scope.backgroundScope, 3)
        return ExerciseRepository(
            dao,
            ExerciseRemoteDataSource(FakeApiService()),
            FileRepository(store, queue),
            FixedOwner(owner),
            ImmediateSyncTransactionRunner(),
            fakeOutboxWriter(outboxDao).first,
        )
    }

    private fun workoutRepo(dao: FakeWorkoutDao, owner: String, outboxDao: FakeSyncOutboxDao): WorkoutRepository =
        WorkoutRepository(
            dao,
            FixedOwner(owner),
            ImmediateSyncTransactionRunner(),
            fakeOutboxWriter(outboxDao).first,
        )

    private fun payloadOf(entry: com.organizer.data.local.db.entities.SyncOutboxEntity) =
        json.parseToJsonElement(entry.payload!!).jsonObject

    @Test
    fun `custom exercise create enqueues UPSERT payload`() = runTest {
        val exerciseDao = FakeExerciseDao()
        val outboxDao = FakeSyncOutboxDao()
        val repo = exerciseRepo(exerciseDao, "user-a", outboxDao, this)

        repo.addCustomExercise("My Lift", listOf("do it"), "mine.jpg", "REPS_WEIGHT")

        val entry = outboxDao.entries.value.single()
        assertEquals("user-a", entry.ownerFirebaseUid)
        assertEquals(SyncEntityTypes.CUSTOM_EXERCISE, entry.entityType)
        assertEquals("UPSERT", entry.operation)
        assertEquals(0L, entry.baseVersion)
        val exercise = exerciseDao.data.value.single()
        assertEquals(exercise.syncId, entry.recordSyncId)
        val payload = payloadOf(entry)
        assertEquals(1, payload["schemaVersion"]!!.jsonPrimitive.content.toInt())
        assertEquals("My Lift", payload["name"]!!.jsonPrimitive.content)
        assertEquals(true, payload["isCustom"]!!.jsonPrimitive.content.toBoolean())
        assertTrue("payload must not contain local Room IDs", !payload.containsKey("exerciseId"))
        assertTrue("payload must not contain owner", !payload.containsKey("ownerFirebaseUid"))
    }

    @Test
    fun `custom exercise delete enqueues DELETE with tombstone payload`() = runTest {
        val exerciseDao = FakeExerciseDao()
        val outboxDao = FakeSyncOutboxDao()
        val repo = exerciseRepo(exerciseDao, "user-a", outboxDao, this)
        val exercise = ExerciseEntity(
            exerciseId = 7L,
            name = "Old Lift",
            instructions = listOf("lift"),
            imageUrls = listOf("old.jpg"),
            isCustom = true,
            ownerFirebaseUid = "user-a",
            syncId = "sync-7",
            serverVersion = 4,
        )
        exerciseDao.seed(exercise)

        repo.deleteCustomExercise(7L)

        val entry = outboxDao.entries.value.single()
        assertEquals("DELETE", entry.operation)
        assertEquals(4L, entry.baseVersion)
        assertEquals("sync-7", entry.recordSyncId)
        assertNotNull(exerciseDao.data.value.single().deletedAt)
        assertEquals("Old Lift", payloadOf(entry)["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun `workout create update and delete enqueue correct mutations`() = runTest {
        val workoutDao = FakeWorkoutDao()
        val outboxDao = FakeSyncOutboxDao()
        val repo = workoutRepo(workoutDao, "user-a", outboxDao)

        val id = repo.createWorkout("Push Day", "notes")
        repo.updateWorkoutNotes(id, "updated notes")
        repo.deleteWorkout(id)

        val entries = outboxDao.entries.value
        assertEquals(2, entries.size)
        assertEquals(listOf("UPSERT", "DELETE"), entries.map { it.operation })
        assertTrue(entries.all { it.entityType == SyncEntityTypes.WORKOUT })
        assertTrue(entries.all { it.ownerFirebaseUid == "user-a" })
        assertTrue(entries.all { it.baseVersion == 0L })
        assertEquals("updated notes", payloadOf(entries[0])["notes"]!!.jsonPrimitive.content)
        assertEquals("Push Day", payloadOf(entries[1])["name"]!!.jsonPrimitive.content)
        assertTrue(!payloadOf(entries[1]).containsKey("workoutId"))
    }

    @Test
    fun `guest writes never enqueue mutations`() = runTest {
        val workoutDao = FakeWorkoutDao()
        val outboxDao = FakeSyncOutboxDao()
        val repo = workoutRepo(workoutDao, OwnerProvider.GUEST, outboxDao)

        repo.createWorkout("Guest workout")

        assertTrue(outboxDao.entries.value.isEmpty())
        assertEquals(1, workoutDao.data.value.size)
    }

    @Test
    fun `multiple unsent edits coalesce into one mutation with stable id`() = runTest {
        val workoutDao = FakeWorkoutDao()
        val outboxDao = FakeSyncOutboxDao()
        val repo = workoutRepo(workoutDao, "user-a", outboxDao)

        val id = repo.createWorkout("A")
        repo.updateWorkoutNotes(id, "B")
        repo.updateWorkoutNotes(id, "C")

        val entries = outboxDao.entries.value
        assertEquals(1, entries.size)
        assertEquals("C", payloadOf(entries.single())["notes"]!!.jsonPrimitive.content)
    }

    @Test
    fun `no-op update does not enqueue a mutation`() = runTest {
        val workoutDao = FakeWorkoutDao()
        val outboxDao = FakeSyncOutboxDao()
        val repo = workoutRepo(workoutDao, "user-a", outboxDao)
        val id = repo.createWorkout("A")

        repo.updateWorkoutNotes(id, "")

        assertEquals(1, outboxDao.entries.value.size)
    }

    @Test
    fun `potentially sent mutation is never overwritten`() = runTest {
        val workoutDao = FakeWorkoutDao()
        val outboxDao = FakeSyncOutboxDao()
        val repo = workoutRepo(workoutDao, "user-a", outboxDao)
        val id = repo.createWorkout("A")
        val first = outboxDao.entries.value.single()

        outboxDao.updateRetry("user-a", first.mutationId, 1, "sent", "SENT")
        repo.updateWorkoutNotes(id, "B")

        val entries = outboxDao.entries.value
        assertEquals(2, entries.size)
        assertEquals(first.mutationId, entries[0].mutationId)
        assertEquals("A", payloadOf(entries[0])["name"]!!.jsonPrimitive.content)
        assertEquals("B", payloadOf(entries[1])["notes"]!!.jsonPrimitive.content)
    }

    @Test
    fun `cross owner record cannot be enqueued`() = runTest {
        val workoutDao = FakeWorkoutDao()
        val outboxDao = FakeSyncOutboxDao()
        val repo = workoutRepo(workoutDao, "user-a", outboxDao)
        workoutDao.seed(
            WorkoutEntity(workoutId = 9L, name = "Other", ownerFirebaseUid = "user-b", syncId = "other-uuid")
        )

        repo.deleteWorkout(9L)

        assertTrue(outboxDao.entries.value.isEmpty())
    }

    @Test
    fun `owner switch inside local transaction aborts entity and outbox writes`() = runTest {
        val owner = MutableOwner("user-a")
        val workoutDao = FakeWorkoutDao()
        val outboxDao = FakeSyncOutboxDao()
        val repo = WorkoutRepository(
            workoutDao,
            owner,
            SwitchInsideRunner(owner),
            fakeOutboxWriter(outboxDao).first,
        )

        val thrown = runCatching { repo.createWorkout("Switched") }.exceptionOrNull()

        assertTrue(thrown is OwnerChangedDuringLocalWriteException)
        assertTrue(workoutDao.data.value.isEmpty())
        assertTrue(outboxDao.entries.value.isEmpty())
    }

    @Test
    fun `previously transmitted pending mutation is never coalesced`() = runTest {
        val outboxDao = FakeSyncOutboxDao()
        val syncId = "existing-uuid"
        val original = SyncOutboxEntity(
            mutationId = "original-uuid",
            ownerFirebaseUid = "user-a",
            entityType = "WORKOUT",
            recordSyncId = syncId,
            operation = "UPSERT",
            payload = """{"schemaVersion":1,"name":"Original"}""",
            baseVersion = 0,
            syncStatus = SyncOutboxStatus.PENDING_UPLOAD,
            everTransmitted = true,
        )
        outboxDao.insert(original)

        fakeOutboxWriter(outboxDao).first.upsertWorkout(
            "user-a",
            WorkoutEntity(
                name = "Edited",
                ownerFirebaseUid = "user-a",
                syncId = syncId,
                serverVersion = 0,
            ),
        )

        val rows = outboxDao.entries.value
        assertEquals(2, rows.size)
        val stored = rows.first { it.mutationId == original.mutationId }
        assertEquals(original.payload, stored.payload)
        assertEquals(original.baseVersion, stored.baseVersion)
        assertTrue(rows.any { it.mutationId != original.mutationId && it.recordSyncId == syncId })
    }
}