package com.organizer.sync

import com.organizer.auth.OwnerProvider
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.local.db.entities.SyncOutboxEntity
import com.organizer.data.local.db.entities.WorkoutEntity
import com.organizer.data.local.db.entities.WorkoutExerciseEntity
import com.organizer.data.repo.AssociationChange
import com.organizer.data.repo.WorkoutExerciseRepository
import com.organizer.fakes.FakeExerciseDao
import com.organizer.fakes.FakeSyncOutboxDao
import com.organizer.fakes.FakeWorkoutDao
import com.organizer.fakes.FakeWorkoutExerciseDao
import com.organizer.fakes.ImmediateSyncTransactionRunner
import com.organizer.fakes.fakeOutboxWriter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutExerciseSyncTest {

    private val owner = "user-a"
    private val workoutSyncId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
    private val customSyncId = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"

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

    private class Fixture(val ownerProvider: TestOwner = TestOwner()) {
        val links = FakeWorkoutExerciseDao()
        val workouts = FakeWorkoutDao()
        val exercises = FakeExerciseDao()
        val outbox = FakeSyncOutboxDao()
        val repo = WorkoutExerciseRepository(
            links,
            workouts,
            exercises,
            outbox,
            com.organizer.fakes.FakeSyncStagedDao(),
            ownerProvider,
            ImmediateSyncTransactionRunner(),
            fakeOutboxWriter(outbox).first,
            SyncPayloadFactory(Json { ignoreUnknownKeys = true }),
            com.organizer.sync.SyncOperationLocks(),
        )

        fun markAllSent() {
            outbox.entries.update { list -> list.map { it.copy(everTransmitted = true) } }
        }

        fun markLast(status: String, transmitted: Boolean) {
            outbox.entries.update { list ->
                list.dropLast(1) + list.last().copy(syncStatus = status, everTransmitted = transmitted)
            }
        }

        fun setServerVersion(version: Long) {
            links.data.update { list -> list.map { it.copy(serverVersion = version) } }
        }

        suspend fun seedWorkoutAndExercises() {
            workouts.insert(
                WorkoutEntity(
                    workoutId = 1L, name = "Routine", ownerFirebaseUid = "user-a",
                    syncId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                )
            )
            exercises.seed(
                ExerciseEntity(
                    exerciseId = 10L, name = "Catalog", instructions = emptyList(), imageUrls = emptyList(),
                    isCustom = false, ownerFirebaseUid = "public", publicExerciseId = 42,
                ),
                ExerciseEntity(
                    exerciseId = 20L, name = "Mine", instructions = emptyList(), imageUrls = emptyList(),
                    isCustom = true, ownerFirebaseUid = "user-a", syncId = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
                ),
            )
        }
    }

    private fun payloadOf(entry: SyncOutboxEntity) = Json.parseToJsonElement(entry.payload!!).jsonObject

    @Test
    fun `duplicate add is a no-op with stable identity and order`() = runTest {
        val f = Fixture()
        f.seedWorkoutAndExercises()
        assertEquals(AssociationChange.CREATED, f.repo.addExerciseToWorkout(1L, 10L))
        val first = f.links.data.value.single()
        val firstOutbox = f.outbox.entries.value.single()

        assertEquals(AssociationChange.NOOP, f.repo.addExerciseToWorkout(1L, 10L))

        assertEquals(1, f.links.data.value.size)
        assertEquals(first.copy(), f.links.data.value.single())
        assertEquals(1, f.outbox.entries.value.size)
        assertEquals(firstOutbox.mutationId, f.outbox.entries.value.single().mutationId)
        assertEquals(0, payloadOf(firstOutbox)["orderIndex"]!!.jsonPrimitive.content.toInt())
        assertEquals("PUBLIC", payloadOf(firstOutbox)["exerciseRef"]!!.jsonObject["kind"]!!.jsonPrimitive.content)
        assertEquals("42", payloadOf(firstOutbox)["exerciseRef"]!!.jsonObject["exerciseId"]!!.jsonPrimitive.content)
        assertEquals(workoutSyncId, payloadOf(firstOutbox)["workoutSyncId"]!!.jsonPrimitive.content)
    }

    @Test
    fun `custom exercise reference uses syncId`() = runTest {
        val f = Fixture()
        f.seedWorkoutAndExercises()
        f.repo.addExerciseToWorkout(1L, 20L)
        val ref = payloadOf(f.outbox.entries.value.single())["exerciseRef"]!!.jsonObject
        assertEquals("CUSTOM", ref["kind"]!!.jsonPrimitive.content)
        assertEquals(customSyncId, ref["syncId"]!!.jsonPrimitive.content)
        assertNull(ref["exerciseId"])
    }

    @Test
    fun `guest add stays local with no outbox`() = runTest {
        val f = Fixture(TestOwner().apply { uid = OwnerProvider.GUEST })
        assertEquals(AssociationChange.CREATED, f.repo.addExerciseToWorkout(1L, 10L))
        assertEquals(1, f.links.data.value.size)
        assertTrue(f.outbox.entries.value.isEmpty())
    }

    @Test
    fun `owner switch inside transaction aborts add`() = runTest {
        val f = Fixture(TestOwner().apply { switchAtCall = 2 })
        val thrown = runCatching { f.repo.addExerciseToWorkout(1L, 10L) }.exceptionOrNull()
        assertTrue(thrown is OwnerChangedDuringLocalWriteException)
        assertTrue(f.links.data.value.isEmpty())
        assertTrue(f.outbox.entries.value.isEmpty())
    }

    @Test
    fun `reorder preserves syncId and produces final payload`() = runTest {
        val f = Fixture()
        f.seedWorkoutAndExercises()
        f.repo.addExerciseToWorkout(1L, 10L)
        f.repo.addExerciseToWorkout(1L, 20L)
        val before = f.links.data.value.map { it.syncId }.toSet()
        val outboxBeforeReorder = f.outbox.entries.value.size
        f.markLast(SyncOutboxStatus.ACKNOWLEDGED, true)

        val reordered = listOf(
            ExerciseEntity(20L, "Mine", emptyList(), emptyList(), isCustom = true, ownerFirebaseUid = "user-a", syncId = customSyncId),
            ExerciseEntity(10L, "Catalog", emptyList(), emptyList(), isCustom = false, ownerFirebaseUid = "public", publicExerciseId = 42),
        )
        f.repo.updateExerciseOrder(1L, reordered)

        assertEquals(before, f.links.data.value.map { it.syncId }.toSet())
        assertEquals(0, f.links.data.value.first { it.exerciseId == 20L }.orderIndex)
        assertEquals(1, f.links.data.value.first { it.exerciseId == 10L }.orderIndex)
        val newEntry = f.outbox.entries.value[outboxBeforeReorder]
        assertFalse(newEntry.everTransmitted)
        assertEquals("UPSERT", newEntry.operation)
        assertEquals(0, payloadOf(newEntry)["orderIndex"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `soft delete retains row and enqueues self contained delete`() = runTest {
        val f = Fixture()
        f.seedWorkoutAndExercises()
        f.repo.addExerciseToWorkout(1L, 10L)
        f.markLast(SyncOutboxStatus.ACKNOWLEDGED, true)

        assertEquals(AssociationChange.DELETED, f.repo.deleteExerciseFromWorkout(1L, 10L))

        val link = f.links.data.value.single()
        assertNotNull(link.deletedAt)
        assertEquals("PENDING_DELETE", link.syncStatus)
        val delete = f.outbox.entries.value.last()
        assertEquals("DELETE", delete.operation)
        assertNotNull(payloadOf(delete)["deletedAt"])
        assertEquals(link.syncId, delete.recordSyncId)
    }

    @Test
    fun `delete of never transmitted association supersedes pending upsert`() = runTest {
        val f = Fixture()
        f.seedWorkoutAndExercises()
        f.repo.addExerciseToWorkout(1L, 10L)

        f.repo.deleteExerciseFromWorkout(1L, 10L)

        assertEquals(SyncOutboxStatus.SUPERSEDED, f.outbox.entries.value.single().syncStatus)
        assertNotNull(f.links.data.value.single().deletedAt)
    }

    @Test
    fun `re-add after acknowledged delete restores same identity`() = runTest {
        val f = Fixture()
        f.seedWorkoutAndExercises()
        f.repo.addExerciseToWorkout(1L, 10L)
        val original = f.links.data.value.single()
        f.markLast(SyncOutboxStatus.ACKNOWLEDGED, true)
        f.repo.deleteExerciseFromWorkout(1L, 10L)
        f.markLast(SyncOutboxStatus.ACKNOWLEDGED, true)
        f.setServerVersion(3L)

        assertEquals(AssociationChange.RESTORED, f.repo.addExerciseToWorkout(1L, 10L))

        val restored = f.links.data.value.single()
        assertEquals(original.syncId, restored.syncId)
        assertNull(restored.deletedAt)
        val restore = f.outbox.entries.value.last()
        assertEquals("RESTORE", restore.operation)
        assertEquals(original.syncId, restore.recordSyncId)
        assertEquals(3L, restore.baseVersion)
    }

    @Test
    fun `re-add with transmitted unresolved delete is blocked`() = runTest {
        val f = Fixture()
        f.seedWorkoutAndExercises()
        f.repo.addExerciseToWorkout(1L, 10L)
        f.markLast(SyncOutboxStatus.ACKNOWLEDGED, true)
        f.repo.deleteExerciseFromWorkout(1L, 10L)
        f.markLast(SyncOutboxStatus.IN_FLIGHT, true)
        val count = f.outbox.entries.value.size

        assertEquals(AssociationChange.BLOCKED, f.repo.addExerciseToWorkout(1L, 10L))

        assertNotNull(f.links.data.value.single().deletedAt)
        assertEquals(count, f.outbox.entries.value.size)
    }
}
