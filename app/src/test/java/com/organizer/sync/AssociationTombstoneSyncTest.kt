package com.organizer.sync

import com.organizer.auth.OwnerProvider
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.local.db.entities.SyncStagedStatus
import com.organizer.data.local.db.entities.WorkoutEntity
import com.organizer.data.local.db.entities.WorkoutExerciseEntity
import com.organizer.data.repo.AssociationChange
import com.organizer.data.repo.WorkoutExerciseRepository
import com.organizer.fakes.FakeExerciseDao
import com.organizer.fakes.FakeSyncOutboxDao
import com.organizer.fakes.FakeSyncStagedDao
import com.organizer.fakes.FakeWorkoutDao
import com.organizer.fakes.FakeWorkoutExerciseDao
import com.organizer.fakes.ImmediateSyncTransactionRunner
import com.organizer.fakes.fakeOutboxWriter
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssociationTombstoneSyncTest {

    private val owner = "user-a"
    private val workoutSyncId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
    private val linkSyncId = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"

    private class Fixture {
        val links = FakeWorkoutExerciseDao()
        val workouts = FakeWorkoutDao()
        val exercises = FakeExerciseDao()
        val staged = FakeSyncStagedDao()
        val outbox = FakeSyncOutboxDao()
        val json = Json { ignoreUnknownKeys = true }
        val applier = SyncStagedApplier(
            object : OwnerProvider() { override fun currentOwner() = "user-a" },
            workouts, exercises, outbox, links, com.organizer.fakes.FakeFavoriteExerciseDao(), null, null, null, null, staged, ImmediateSyncTransactionRunner(), json,
        )
        val repository = WorkoutExerciseRepository(
            links, workouts, exercises, outbox, staged,
            object : OwnerProvider() { override fun currentOwner() = "user-a" },
            ImmediateSyncTransactionRunner(),
            fakeOutboxWriter(outbox).first,
            SyncPayloadFactory(json),
            SyncOperationLocks(),
        )

        suspend fun seedParents() {
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
                )
            )
        }

        suspend fun stageDelete(payload: String?, version: Long = 3) {
            staged.upsertIfNewer(
                "user-a", SyncEntityTypes.WORKOUT_EXERCISE, "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb", version, 100L, true,
                payload, SyncStagedStatus.PENDING, 100L,
            )
        }
    }

    private suspend fun Fixture.applyStaged(): StagedApplyResult =
        applier.process("user-a", staged.records.value.single())

    @Test
    fun `null payload tombstone with existing local association is applied by syncId`() = runTest {
        val f = Fixture()
        f.seedParents()
        f.links.insert(
            WorkoutExerciseEntity(
                workoutId = 1L, exerciseId = 10L, orderIndex = 0, ownerFirebaseUid = owner,
                syncId = linkSyncId, serverVersion = 1,
            )
        )
        f.stageDelete(null)

        assertEquals(StagedApplyResult.APPLIED, f.applyStaged())

        val link = f.links.findBySyncId(owner, linkSyncId)!!
        assertNotNull(link.deletedAt)
        assertEquals(3L, link.serverVersion)
    }

    @Test
    fun `null payload tombstone with no local association stays retryable`() = runTest {
        val f = Fixture()
        f.seedParents()
        f.stageDelete(null)

        assertEquals(StagedApplyResult.WAITING_DEPENDENCY, f.applyStaged())

        assertEquals(
            SyncStagedStatus.WAITING_DEPENDENCY,
            f.staged.records.value.single().processingStatus,
        )
        assertNull(f.links.findBySyncId(owner, linkSyncId))
    }

    @Test
    fun `tombstone before parents in one page is retried after parents exist`() = runTest {
        val f = Fixture()
        f.stageDelete(
            """{"schemaVersion":1,"workoutSyncId":"$workoutSyncId","exerciseRef":{"kind":"PUBLIC","exerciseId":42},"orderIndex":0}"""
        )
        assertEquals(StagedApplyResult.WAITING_DEPENDENCY, f.applyStaged())

        f.seedParents()
        f.staged.resetToPending(owner, setOf(SyncStagedStatus.WAITING_DEPENDENCY))
        assertEquals(StagedApplyResult.TOMBSTONE_ONLY, f.applyStaged())

        val tombstone = f.links.findBySyncId(owner, linkSyncId)!!
        assertNotNull(tombstone.deletedAt)
        assertEquals(3L, tombstone.serverVersion)
    }

    @Test
    fun `tombstone survives another pull and reconciliation cycle`() = runTest {
        val f = Fixture()
        f.seedParents()
        f.stageDelete(
            """{"schemaVersion":1,"workoutSyncId":"$workoutSyncId","exerciseRef":{"kind":"PUBLIC","exerciseId":42},"orderIndex":0}"""
        )
        f.applyStaged()
        f.staged.resetToPending(owner, setOf(SyncStagedStatus.FAILED, SyncStagedStatus.WAITING_DEPENDENCY))

        // Neither retry nor reconciliation touches a durable tombstone.
        assertEquals(0, f.staged.records.value.count { it.processingStatus == SyncStagedStatus.PENDING })
        assertNotNull(f.links.findBySyncId(owner, linkSyncId)!!.deletedAt)
    }

    @Test
    fun `stale upsert cannot resurrect a tombstoned association`() = runTest {
        val f = Fixture()
        f.seedParents()
        f.links.insert(
            WorkoutExerciseEntity(
                workoutId = 1L, exerciseId = 10L, orderIndex = 0, ownerFirebaseUid = owner,
                syncId = linkSyncId, serverVersion = 5, deletedAt = 100L, syncStatus = "SYNCED",
            )
        )
        f.staged.upsertIfNewer(
            owner, SyncEntityTypes.WORKOUT_EXERCISE, linkSyncId, 4, 200L, false,
            """{"schemaVersion":1,"workoutSyncId":"$workoutSyncId","exerciseRef":{"kind":"PUBLIC","exerciseId":42},"orderIndex":0}""",
            SyncStagedStatus.PENDING, 200L,
        )

        assertEquals(StagedApplyResult.APPLIED, f.applyStaged())

        assertNotNull(f.links.findBySyncId(owner, linkSyncId)!!.deletedAt)
        assertEquals(5L, f.links.findBySyncId(owner, linkSyncId)!!.serverVersion)
    }

    @Test
    fun `re-add after remote tombstone restores the same identity`() = runTest {
        val f = Fixture()
        f.seedParents()
        f.links.insert(
            WorkoutExerciseEntity(
                workoutId = 1L, exerciseId = 10L, orderIndex = 0, ownerFirebaseUid = owner,
                syncId = linkSyncId, serverVersion = 3, deletedAt = 100L, syncStatus = "SYNCED",
            )
        )

        assertEquals(AssociationChange.RESTORED, f.repository.addExerciseToWorkout(1L, 10L))

        val restored = f.links.findBySyncId(owner, linkSyncId)!!
        assertEquals(linkSyncId, restored.syncId)
        assertNull(restored.deletedAt)
        val mutation = f.outbox.entries.value.single()
        assertEquals("RESTORE", mutation.operation)
        assertEquals(linkSyncId, mutation.recordSyncId)
        assertEquals(3L, mutation.baseVersion)
    }

    @Test
    fun `remote upsert identity collision is durably blocked`() = runTest {
        val f = Fixture()
        f.seedParents()
        f.links.insert(
            WorkoutExerciseEntity(
                workoutId = 1L, exerciseId = 10L, orderIndex = 0, ownerFirebaseUid = owner, syncId = "local-link",
            )
        )
        f.staged.upsertIfNewer(
            owner, SyncEntityTypes.WORKOUT_EXERCISE, "remote-link", 2, 100L, false,
            """{"schemaVersion":1,"workoutSyncId":"$workoutSyncId","exerciseRef":{"kind":"PUBLIC","exerciseId":42},"orderIndex":0}""",
            SyncStagedStatus.PENDING, 100L,
        )

        assertEquals(StagedApplyResult.CONFLICT_BLOCKED, f.applyStaged())

        assertEquals(SyncStagedStatus.CONFLICT_BLOCKED, f.staged.records.value.single().processingStatus)
        assertNotNull(f.staged.records.value.single().lastError)
        assertEquals("local-link", f.links.findByPair(owner, 1L, 10L)!!.syncId)
        f.staged.resetToPending(owner, setOf(SyncStagedStatus.FAILED, SyncStagedStatus.WAITING_DEPENDENCY))
        assertEquals(SyncStagedStatus.CONFLICT_BLOCKED, f.staged.records.value.single().processingStatus)
    }

    @Test
    fun `remote delete identity collision is durably blocked`() = runTest {
        val f = Fixture()
        f.seedParents()
        f.links.insert(
            WorkoutExerciseEntity(
                workoutId = 1L, exerciseId = 10L, orderIndex = 0, ownerFirebaseUid = owner, syncId = "local-link",
            )
        )
        f.staged.upsertIfNewer(
            owner, SyncEntityTypes.WORKOUT_EXERCISE, "remote-link", 2, 100L, true,
            """{"schemaVersion":1,"workoutSyncId":"$workoutSyncId","exerciseRef":{"kind":"PUBLIC","exerciseId":42},"orderIndex":0,"deletedAt":"2026-11-25T10:00:00.000Z"}""",
            SyncStagedStatus.PENDING, 100L,
        )

        assertEquals(StagedApplyResult.CONFLICT_BLOCKED, f.applyStaged())

        assertEquals(SyncStagedStatus.CONFLICT_BLOCKED, f.staged.records.value.single().processingStatus)
        assertNull(f.links.findBySyncId(owner, "local-link")!!.deletedAt)
    }

    @Test
    fun `fake insert enforces duplicate identity and parent constraints`() = runTest {
        val f = Fixture()
        f.links.validWorkoutIds = setOf(1L)
        f.links.validExerciseIds = setOf(10L)
        f.links.insert(
            WorkoutExerciseEntity(workoutId = 1L, exerciseId = 10L, orderIndex = 0, ownerFirebaseUid = owner, syncId = "link-1")
        )

        val duplicatePair = runCatching {
            f.links.insert(
                WorkoutExerciseEntity(workoutId = 1L, exerciseId = 10L, orderIndex = 1, ownerFirebaseUid = owner, syncId = "link-2")
            )
        }.exceptionOrNull()
        val duplicateSync = runCatching {
            f.links.insert(
                WorkoutExerciseEntity(workoutId = 2L, exerciseId = 10L, orderIndex = 0, ownerFirebaseUid = owner, syncId = "link-1")
            )
        }.exceptionOrNull()
        val missingParent = runCatching {
            f.links.insert(
                WorkoutExerciseEntity(workoutId = 99L, exerciseId = 10L, orderIndex = 0, ownerFirebaseUid = owner, syncId = "link-3")
            )
        }.exceptionOrNull()

        assertNotNull(duplicatePair)
        assertNotNull(duplicateSync)
        assertNotNull(missingParent)
        assertEquals(1, f.links.data.value.size)
    }

    @Test
    fun `payload lookup is owner and public scoped`() = runTest {
        val f = Fixture()
        f.exercises.seed(
            ExerciseEntity(
                exerciseId = 10L, name = "Catalog", instructions = emptyList(), imageUrls = emptyList(),
                isCustom = false, ownerFirebaseUid = "public", publicExerciseId = 42,
            ),
            ExerciseEntity(
                exerciseId = 20L, name = "Foreign", instructions = emptyList(), imageUrls = emptyList(),
                isCustom = true, ownerFirebaseUid = "user-b", syncId = "foreign-sync",
            ),
        )

        assertNotNull(f.exercises.getByIdOnce(owner, 10L))
        assertNull(f.exercises.getByIdOnce(owner, 20L))
    }

    @Test
    fun `tombstone identity parsing is formatting independent`() = runTest {
        val compact = WorkoutExerciseIdentityParser.parse(
            """{"schemaVersion":1,"workoutSyncId":"$workoutSyncId","exerciseRef":{"kind":"PUBLIC","exerciseId":42}}""",
            linkSyncId,
        )
        val formatted = WorkoutExerciseIdentityParser.parse(
            """
            { "exerciseRef" : { "exerciseId" : 42 , "kind" : "PUBLIC" },
              "workoutSyncId" : "$workoutSyncId", "schemaVersion" : 1 }
            """.trimIndent(),
            linkSyncId,
        )

        assertEquals(compact, formatted)
        assertEquals(linkSyncId, formatted.associationSyncId)
        assertEquals(ExerciseReference.Public(42), formatted.exerciseRef)
    }

    @Test
    fun `parser handles escaped strings and decimal exerciseId`() = runTest {
        val uuid = workoutSyncId
        val compact = WorkoutExerciseIdentityParser.parse(
            """{"schemaVersion":1,"workoutSyncId":"$uuid","exerciseRef":{"kind":"public","exerciseId":42.0}}""",
            linkSyncId,
        )
        // kind case-insensitive normalization
        assertEquals(ExerciseReference.Public(42), compact.exerciseRef)
        assertEquals(uuid, compact.workoutSyncId)

        val customUuid = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
        // Escaped string values round-trip through JSON.
        val escaped = WorkoutExerciseIdentityParser.parse(
            """{"schemaVersion":1,"workoutSyncId":"$uuid","exerciseRef":{"kind":"CUSTOM","syncId":"$customUuid"}}""",
            linkSyncId,
        )
        assertEquals(ExerciseReference.Custom(customUuid), escaped.exerciseRef)
    }

    @Test
    fun `structured pair matching blocks re-add with reformatted tombstone`() = runTest {
        val f = Fixture()
        f.seedParents()
        f.staged.upsertIfNewer(
            owner, SyncEntityTypes.WORKOUT_EXERCISE, "other-link", 4, 100L, true,
            """
            { "exerciseRef" : { "exerciseId" : 42 , "kind" : "PUBLIC" },
              "workoutSyncId" : "$workoutSyncId", "deletedAt" : "2026-11-25T10:00:00.000Z" }
            """.trimIndent(),
            SyncStagedStatus.WAITING_DEPENDENCY, 100L,
        )

        assertEquals(AssociationChange.BLOCKED, f.repository.addExerciseToWorkout(1L, 10L))
        assertEquals(0, f.links.data.value.size)
        assertTrue(f.outbox.entries.value.isEmpty())
    }

    @Test
    fun `null payload tombstone with no local row blocks re-add`() = runTest {
        val f = Fixture()
        f.seedParents()
        f.staged.upsertIfNewer(
            owner, SyncEntityTypes.WORKOUT_EXERCISE, linkSyncId, 4, 100L, true,
            null, SyncStagedStatus.WAITING_DEPENDENCY, 100L,
        )

        assertEquals(AssociationChange.BLOCKED, f.repository.addExerciseToWorkout(1L, 10L))
        assertEquals(0, f.links.data.value.size)
        assertTrue(f.outbox.entries.value.isEmpty())
    }

    @Test
    fun `malformed tombstone does not create a fresh identity`() = runTest {
        val f = Fixture()
        f.seedParents()
        f.staged.upsertIfNewer(
            owner, SyncEntityTypes.WORKOUT_EXERCISE, linkSyncId, 4, 100L, true,
            """{ "workoutSyncId" : 12345, "exerciseRef" : "PUBLIC" }""",
            SyncStagedStatus.FAILED, 100L,
        )

        assertEquals(AssociationChange.BLOCKED, f.repository.addExerciseToWorkout(1L, 10L))
        assertTrue(f.links.data.value.isEmpty())
        assertTrue(f.outbox.entries.value.isEmpty())
    }

    @Test
    fun `explicit restore reuses tombstone syncId and base version`() = runTest {
        val f = Fixture()
        f.seedParents()
        f.staged.upsertIfNewer(
            owner, SyncEntityTypes.WORKOUT_EXERCISE, linkSyncId, 6, 100L, true,
            """{"schemaVersion":1,"workoutSyncId":"$workoutSyncId","exerciseRef":{"kind":"PUBLIC","exerciseId":42}}""",
            SyncStagedStatus.WAITING_DEPENDENCY, 100L,
        )

        assertEquals(AssociationChange.RESTORED, f.repository.restoreTombstonedAssociation(linkSyncId))

        val restored = f.links.findBySyncId(owner, linkSyncId)!!
        assertNull(restored.deletedAt)
        assertEquals(6L, restored.serverVersion)
        val mutation = f.outbox.entries.value.single()
        assertEquals("RESTORE", mutation.operation)
        assertEquals(linkSyncId, mutation.recordSyncId)
        assertEquals(6L, mutation.baseVersion)
        assertEquals(
            SyncStagedStatus.APPLIED,
            f.staged.find(owner, SyncEntityTypes.WORKOUT_EXERCISE, linkSyncId)!!.processingStatus,
        )
    }

    @Test
    fun `foreign and non association tombstones do not block re-add`() = runTest {
        val f = Fixture()
        f.seedParents()
        f.staged.upsertIfNewer(
            "user-b", SyncEntityTypes.WORKOUT_EXERCISE, "foreign-link", 4, 100L, true,
            null, SyncStagedStatus.WAITING_DEPENDENCY, 100L,
        )
        f.staged.upsertIfNewer(
            owner, SyncEntityTypes.WORKOUT, "workout-tombstone", 4, 100L, true,
            null, SyncStagedStatus.WAITING_DEPENDENCY, 100L,
        )

        assertEquals(AssociationChange.CREATED, f.repository.addExerciseToWorkout(1L, 10L))
        assertEquals(1, f.links.data.value.size)
        assertEquals(1, f.outbox.entries.value.size)
    }
    @Test
    fun `resolved tombstone does not block subsequent re-add`() = runTest {
        val f = Fixture()
        f.seedParents()
        f.staged.upsertIfNewer(
            owner, SyncEntityTypes.WORKOUT_EXERCISE, linkSyncId, 6, 100L, true,
            """{"schemaVersion":1,"workoutSyncId":"$workoutSyncId","exerciseRef":{"kind":"PUBLIC","exerciseId":42}}""",
            SyncStagedStatus.WAITING_DEPENDENCY, 100L,
        )

        assertEquals(AssociationChange.RESTORED, f.repository.restoreTombstonedAssociation(linkSyncId))
        // After resolution the local association is active; re-add is a no-op, not blocked.
        assertEquals(AssociationChange.NOOP, f.repository.addExerciseToWorkout(1L, 10L))
        assertEquals(1, f.links.data.value.size)
        assertNull(f.links.data.value.single().deletedAt)
    }

    @Test
    fun `payload-less tombstone remains blocking after staged reload`() = runTest {
        val f = Fixture()
        f.seedParents()
        f.staged.upsertIfNewer(
            owner, SyncEntityTypes.WORKOUT_EXERCISE, linkSyncId, 4, 100L, true,
            null, SyncStagedStatus.WAITING_DEPENDENCY, 100L,
        )

        assertEquals(AssociationChange.BLOCKED, f.repository.addExerciseToWorkout(1L, 10L))
        // Reload staged records (simulating a fresh pull/reconciliation read).
        val reloaded = f.staged.getAll(owner)
        assertEquals(1, reloaded.size)
        assertEquals(AssociationChange.BLOCKED, f.repository.addExerciseToWorkout(1L, 10L))
        assertEquals(0, f.links.data.value.size)
        assertTrue(f.outbox.entries.value.isEmpty())
    }

    @Test
    fun `stable syncId restores even when tombstone payload is absent`() = runTest {
        val f = Fixture()
        f.seedParents()
        // A local tombstone row exists with the association syncId; staged payload is null.
        f.links.insert(
            WorkoutExerciseEntity(
                workoutId = 1L, exerciseId = 10L, orderIndex = 0, ownerFirebaseUid = owner,
                syncId = linkSyncId, serverVersion = 3, deletedAt = 100L, syncStatus = "SYNCED",
            )
        )
        f.staged.upsertIfNewer(
            owner, SyncEntityTypes.WORKOUT_EXERCISE, linkSyncId, 3, 100L, true,
            null, SyncStagedStatus.WAITING_DEPENDENCY, 100L,
        )

        assertEquals(AssociationChange.RESTORED, f.repository.addExerciseToWorkout(1L, 10L))
        val restored = f.links.findBySyncId(owner, linkSyncId)!!
        assertNull(restored.deletedAt)
        assertEquals(linkSyncId, restored.syncId)
    }

}
