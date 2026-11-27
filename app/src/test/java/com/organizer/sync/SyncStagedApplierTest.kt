package com.organizer.sync

import com.organizer.auth.OwnerProvider
import com.organizer.data.local.db.entities.SyncOutboxEntity
import com.organizer.data.local.db.entities.SyncStagedRecordEntity
import com.organizer.data.local.db.entities.SyncStagedStatus
import com.organizer.fakes.FakeExerciseDao
import com.organizer.fakes.FakeSyncOutboxDao
import com.organizer.fakes.FakeSyncStagedDao
import com.organizer.fakes.FakeWorkoutExerciseDao
import com.organizer.fakes.FakeWorkoutDao
import com.organizer.fakes.ImmediateSyncTransactionRunner
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class SyncStagedApplierTest {

    private val owner = "user-a"

    private class MutableOwner : OwnerProvider() {
        @Volatile var uid = "user-a"
        override fun currentOwner(): String = uid
    }

    private class Fixture(val ownerProvider: MutableOwner = MutableOwner()) {
        val workouts = FakeWorkoutDao()
        val exercises = FakeExerciseDao()
        val outbox = FakeSyncOutboxDao()
        val staged = FakeSyncStagedDao()
        val workoutExercises = FakeWorkoutExerciseDao()
        val applier = SyncStagedApplier(
            ownerProvider, workouts, exercises, outbox, workoutExercises, com.organizer.fakes.FakeFavoriteExerciseDao(), null, null, null, null, staged,
            ImmediateSyncTransactionRunner(), Json { ignoreUnknownKeys = true },
        )

        suspend fun stage(record: SyncStagedRecordEntity) {
            staged.upsertIfNewer(
                owner = record.ownerFirebaseUid,
                entityType = record.entityType,
                syncId = record.syncId,
                serverVersion = record.serverVersion,
                serverUpdatedAt = record.serverUpdatedAt,
                deleted = record.deleted,
                payload = record.payload,
                processingStatus = record.processingStatus,
                createdAt = record.createdAt,
            )
        }

        suspend fun process(record: SyncStagedRecordEntity): StagedApplyResult {
            stage(record)
            val stored = staged.find(record.ownerFirebaseUid, record.entityType, record.syncId)!!
            return applier.process(record.ownerFirebaseUid, stored)
        }
    }

    private fun staged(
        entityType: String,
        syncId: String,
        version: Long = 1,
        deleted: Boolean = false,
        payload: String? = """{"schemaVersion":1,"name":"Push Day","notes":""}""",
        ownerUid: String = owner,
    ) = SyncStagedRecordEntity(
        stagedId = 1L,
        ownerFirebaseUid = ownerUid,
        entityType = entityType,
        syncId = syncId,
        serverVersion = version,
        serverUpdatedAt = 1_700_000_000_000L,
        deleted = deleted,
        payload = payload,
        processingStatus = SyncStagedStatus.PENDING,
    )

    @Test
    fun `workout upsert is applied without creating outbox mutation`() = runTest {
        val f = Fixture()
        val syncId = UUID.randomUUID().toString()

        val result = f.process(staged(SyncEntityTypes.WORKOUT, syncId))

        assertEquals(StagedApplyResult.APPLIED, result)
        val workout = f.workouts.findBySyncId(owner, syncId)!!
        assertEquals("Push Day", workout.name)
        assertEquals("SYNCED", workout.syncStatus)
        assertEquals(1L, workout.serverVersion)
        assertTrue(f.outbox.entries.value.isEmpty())
        assertEquals(SyncStagedStatus.APPLIED, f.staged.records.value.single().processingStatus)
    }

    @Test
    fun `workout delete clears normalized name and preserves row`() = runTest {
        val f = Fixture()
        val syncId = UUID.randomUUID().toString()
        f.process(staged(SyncEntityTypes.WORKOUT, syncId))

        val result = f.process(staged(SyncEntityTypes.WORKOUT, syncId, version = 2, deleted = true))

        assertEquals(StagedApplyResult.APPLIED, result)
        val workout = f.workouts.findBySyncId(owner, syncId)!!
        assertNotNull(workout.deletedAt)
        assertNull(workout.normalizedName)
        assertEquals(2L, workout.serverVersion)
    }

    @Test
    fun `custom exercise upsert and delete are applied`() = runTest {
        val f = Fixture()
        val syncId = UUID.randomUUID().toString()
        val payload = """{"schemaVersion":1,"name":"My Lift","instructions":["do it"],"imageUrls":[],"exerciseType":"REPS_WEIGHT","isCustom":true}"""

        assertEquals(StagedApplyResult.APPLIED, f.process(staged(SyncEntityTypes.CUSTOM_EXERCISE, syncId, payload = payload)))
        val exercise = f.exercises.findCustomBySyncId(owner, syncId)!!
        assertEquals("My Lift", exercise.name)
        assertEquals("SYNCED", exercise.syncStatus)

        assertEquals(
            StagedApplyResult.APPLIED,
            f.process(staged(SyncEntityTypes.CUSTOM_EXERCISE, syncId, version = 2, deleted = true, payload = payload)),
        )
        assertNotNull(f.exercises.findCustomBySyncId(owner, syncId)!!.deletedAt)
    }

    @Test
    fun `unsupported schema and invalid payload are retained`() = runTest {
        val f = Fixture()

        assertEquals(
            StagedApplyResult.UNSUPPORTED_SCHEMA,
            f.process(staged(SyncEntityTypes.WORKOUT, UUID.randomUUID().toString(), payload = """{"schemaVersion":2,"name":"X"}""")),
        )
        assertEquals(
            StagedApplyResult.FAILED,
            f.process(staged(SyncEntityTypes.WORKOUT, UUID.randomUUID().toString(), payload = "not json")),
        )
        assertEquals(2, f.staged.records.value.size)
    }

    @Test
    fun `local pending mutation blocks application`() = runTest {
        val f = Fixture()
        val syncId = UUID.randomUUID().toString()
        f.outbox.insert(
            SyncOutboxEntity(
                mutationId = UUID.randomUUID().toString(),
                ownerFirebaseUid = owner,
                entityType = SyncEntityTypes.WORKOUT,
                recordSyncId = syncId,
                operation = "UPSERT",
            )
        )

        val result = f.process(staged(SyncEntityTypes.WORKOUT, syncId))

        assertEquals(StagedApplyResult.LOCAL_PENDING, result)
        assertNull(f.workouts.findBySyncId(owner, syncId))
        assertEquals(SyncStagedStatus.LOCAL_PENDING, f.staged.records.value.single().processingStatus)
    }

    @Test
    fun `only active outbox statuses block remote application`() = runTest {
        val syncId = UUID.randomUUID().toString()
        listOf(SyncOutboxStatus.PENDING_UPLOAD, SyncOutboxStatus.IN_FLIGHT, SyncOutboxStatus.FAILED).forEach { status ->
            val f = Fixture()
            f.outbox.insert(
                SyncOutboxEntity(
                    mutationId = UUID.randomUUID().toString(),
                    ownerFirebaseUid = owner,
                    entityType = SyncEntityTypes.WORKOUT,
                    recordSyncId = syncId,
                    operation = "UPSERT",
                    syncStatus = status,
                )
            )
            assertEquals(StagedApplyResult.LOCAL_PENDING, f.process(staged(SyncEntityTypes.WORKOUT, syncId)))
        }

        val terminal = Fixture()
        terminal.outbox.insert(
            SyncOutboxEntity(
                mutationId = UUID.randomUUID().toString(),
                ownerFirebaseUid = owner,
                entityType = SyncEntityTypes.WORKOUT,
                recordSyncId = syncId,
                operation = "UPSERT",
                syncStatus = SyncOutboxStatus.ACKNOWLEDGED,
            )
        )
        assertEquals(StagedApplyResult.APPLIED, terminal.process(staged(SyncEntityTypes.WORKOUT, syncId)))
    }

    @Test
    fun `outbox entries for other owner entity or record do not block`() = runTest {
        val syncId = UUID.randomUUID().toString()
        val cases = listOf(
            Triple("user-b", SyncEntityTypes.WORKOUT, syncId),
            Triple(owner, SyncEntityTypes.CUSTOM_EXERCISE, syncId),
            Triple(owner, SyncEntityTypes.WORKOUT, UUID.randomUUID().toString()),
        )
        cases.forEach { (outboxOwner, entityType, recordId) ->
            val f = Fixture()
            f.outbox.insert(
                SyncOutboxEntity(
                    mutationId = UUID.randomUUID().toString(),
                    ownerFirebaseUid = outboxOwner,
                    entityType = entityType,
                    recordSyncId = recordId,
                    operation = "UPSERT",
                )
            )
            assertEquals(StagedApplyResult.APPLIED, f.process(staged(SyncEntityTypes.WORKOUT, syncId)))
        }
    }

    @Test
    fun `older server version cannot overwrite newer local state`() = runTest {
        val f = Fixture()
        val syncId = UUID.randomUUID().toString()
        f.process(staged(SyncEntityTypes.WORKOUT, syncId, version = 5))

        f.process(staged(SyncEntityTypes.WORKOUT, syncId, version = 3, payload = """{"schemaVersion":1,"name":"Old"}"""))

        assertEquals("Push Day", f.workouts.findBySyncId(owner, syncId)!!.name)
        assertEquals(5L, f.workouts.findBySyncId(owner, syncId)!!.serverVersion)
    }

    @Test
    fun `tombstone prevents stale upsert resurrection`() = runTest {
        val f = Fixture()
        val syncId = UUID.randomUUID().toString()
        val tombstone = staged(SyncEntityTypes.WORKOUT, syncId, version = 4, deleted = true, payload = null)
        f.stage(tombstone)
        assertEquals(StagedApplyResult.TOMBSTONE_ONLY, f.applier.process(owner, tombstone))

        // A stale snapshot must not overwrite the newer staged tombstone:
        // the applier re-reads the current row by stagedId inside its transaction.
        val stale = staged(SyncEntityTypes.WORKOUT, syncId, version = 3)
        val result = f.applier.process(owner, stale)

        assertEquals(StagedApplyResult.TOMBSTONE_ONLY, result)
        assertEquals(4L, f.staged.find(owner, SyncEntityTypes.WORKOUT, syncId)!!.serverVersion)
        assertNull(f.workouts.findBySyncId(owner, syncId))
    }

    @Test
    fun `cross owner is rejected and unknown entity is unsupported`() = runTest {
        val f = Fixture()
        val syncId = UUID.randomUUID().toString()

        val foreign = staged(SyncEntityTypes.WORKOUT, syncId, ownerUid = "user-b")
        f.stage(foreign)
        assertEquals(StagedApplyResult.FAILED, f.applier.process(owner, foreign))
        assertEquals(SyncStagedStatus.PENDING, f.staged.records.value.single().processingStatus)

        val unsupported = staged("UNKNOWN_ENTITY", syncId)
        assertEquals(StagedApplyResult.UNSUPPORTED, f.process(unsupported))
        assertEquals(SyncStagedStatus.UNSUPPORTED, f.staged.records.value.first { it.entityType == "UNKNOWN_ENTITY" }.processingStatus)

        // WORKOUT_EXERCISE is now applicable; unresolved references are staged.
        val link = staged(
            SyncEntityTypes.WORKOUT_EXERCISE,
            UUID.randomUUID().toString(),
            payload = """{"schemaVersion":1,"workoutSyncId":"missing","exerciseRef":{"kind":"PUBLIC","exerciseId":1},"orderIndex":0}""",
        )
        assertEquals(StagedApplyResult.WAITING_DEPENDENCY, f.process(link))
    }

    @Test
    fun `blocker created before the write transaction prevents remote overwrite`() = runTest {
        val f = Fixture()
        val syncId = UUID.randomUUID().toString()
        val record = staged(SyncEntityTypes.WORKOUT, syncId, version = 3)
        f.stage(record)
        val stored = f.staged.find(owner, SyncEntityTypes.WORKOUT, syncId)!!

        // Simulates a local write landing after the caller captured the staged
        // row but before the applier's write transaction executes.
        val injectingRunner = object : SyncTransactionRunner {
            override suspend fun <T> run(block: suspend () -> T): T {
                f.outbox.insert(
                    SyncOutboxEntity(
                        mutationId = UUID.randomUUID().toString(),
                        ownerFirebaseUid = owner,
                        entityType = SyncEntityTypes.WORKOUT,
                        recordSyncId = syncId,
                        operation = "UPSERT",
                        syncStatus = SyncOutboxStatus.PENDING_UPLOAD,
                    )
                )
                return block()
            }
        }
        val applier = SyncStagedApplier(
            f.ownerProvider, f.workouts, f.exercises, f.outbox, FakeWorkoutExerciseDao(), com.organizer.fakes.FakeFavoriteExerciseDao(), null, null, null, null, f.staged,
            injectingRunner, Json { ignoreUnknownKeys = true },
        )

        assertEquals(StagedApplyResult.LOCAL_PENDING, applier.process(owner, stored))
        assertNull(f.workouts.findBySyncId(owner, syncId))
        assertEquals(
            SyncStagedStatus.LOCAL_PENDING,
            f.staged.find(owner, SyncEntityTypes.WORKOUT, syncId)!!.processingStatus,
        )
    }

    @Test
    fun `account switch inside application transaction aborts without writing`() = runTest {
        val f = Fixture(MutableOwner().apply { uid = "user-b" })
        val syncId = UUID.randomUUID().toString()
        val record = staged(SyncEntityTypes.WORKOUT, syncId, version = 1)
        f.stage(record)
        val stored = f.staged.find(owner, SyncEntityTypes.WORKOUT, syncId)!!

        assertEquals(StagedApplyResult.OWNER_CHANGED, f.applier.process(owner, stored))
        assertNull(f.workouts.findBySyncId(owner, syncId))
        assertEquals(
            SyncStagedStatus.PENDING,
            f.staged.find(owner, SyncEntityTypes.WORKOUT, syncId)!!.processingStatus,
        )
    }

    private suspend fun fixtureWithParents(): Fixture {
        val f = Fixture()
        f.workouts.insert(
            com.organizer.data.local.db.entities.WorkoutEntity(
                workoutId = 5L, name = "Remote", ownerFirebaseUid = owner,
                syncId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
            )
        )
        f.exercises.seed(
            com.organizer.data.local.db.entities.ExerciseEntity(
                exerciseId = 10L, name = "Catalog", instructions = emptyList(), imageUrls = emptyList(),
                isCustom = false, ownerFirebaseUid = "public", publicExerciseId = 42,
            ),
            com.organizer.data.local.db.entities.ExerciseEntity(
                exerciseId = 20L, name = "Mine", instructions = emptyList(), imageUrls = emptyList(),
                isCustom = true, ownerFirebaseUid = owner, syncId = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
            ),
        )
        return f
    }

    @Test
    fun `association with public exercise ref is applied without an outbox mutation`() = runTest {
        val f = fixtureWithParents()
        val linkSyncId = UUID.randomUUID().toString()
        val record = staged(
            SyncEntityTypes.WORKOUT_EXERCISE,
            linkSyncId,
            version = 2,
            payload = """{"schemaVersion":1,"workoutSyncId":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa","exerciseRef":{"kind":"PUBLIC","exerciseId":42},"orderIndex":1,"updatedAt":"2026-11-25T10:00:00.000Z","deletedAt":null}""",
        )

        assertEquals(StagedApplyResult.APPLIED, f.process(record))

        val link = f.workoutExercises.findBySyncId(owner, linkSyncId)!!
        assertEquals(5L, link.workoutId)
        assertEquals(10L, link.exerciseId)
        assertEquals(1, link.orderIndex)
        assertEquals(2L, link.serverVersion)
        assertEquals("SYNCED", link.syncStatus)
        assertTrue(f.outbox.entries.value.isEmpty())
    }

    @Test
    fun `association with custom exercise ref resolves by syncId`() = runTest {
        val f = fixtureWithParents()
        val linkSyncId = UUID.randomUUID().toString()
        val record = staged(
            SyncEntityTypes.WORKOUT_EXERCISE,
            linkSyncId,
            payload = """{"schemaVersion":1,"workoutSyncId":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa","exerciseRef":{"kind":"CUSTOM","syncId":"bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"},"orderIndex":0,"updatedAt":"2026-11-25T10:00:00.000Z"}""",
        )

        assertEquals(StagedApplyResult.APPLIED, f.process(record))
        assertEquals(20L, f.workoutExercises.findBySyncId(owner, linkSyncId)!!.exerciseId)
    }

    @Test
    fun `association with missing dependency stays waiting`() = runTest {
        val f = fixtureWithParents()
        val linkSyncId = UUID.randomUUID().toString()
        val record = staged(
            SyncEntityTypes.WORKOUT_EXERCISE,
            linkSyncId,
            payload = """{"schemaVersion":1,"workoutSyncId":"missing-workout","exerciseRef":{"kind":"PUBLIC","exerciseId":42},"orderIndex":0}""",
        )

        assertEquals(StagedApplyResult.WAITING_DEPENDENCY, f.process(record))
        assertEquals(
            SyncStagedStatus.WAITING_DEPENDENCY,
            f.staged.find(owner, SyncEntityTypes.WORKOUT_EXERCISE, linkSyncId)!!.processingStatus,
        )
        assertNull(f.workoutExercises.findBySyncId(owner, linkSyncId))
    }
}