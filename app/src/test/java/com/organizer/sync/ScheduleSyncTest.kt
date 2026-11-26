package com.organizer.sync

import com.organizer.auth.OwnerProvider
import com.organizer.data.local.db.entities.SyncStagedRecordEntity
import com.organizer.data.local.db.entities.SyncStagedStatus
import com.organizer.data.local.db.entities.WorkoutEntity
import com.organizer.fakes.FakeExerciseDao
import com.organizer.fakes.FakeReminderScheduler
import com.organizer.fakes.FakeScheduledWorkoutDao
import com.organizer.fakes.FakeSyncOutboxDao
import com.organizer.fakes.FakeSyncStagedDao
import com.organizer.fakes.FakeWorkoutDao
import com.organizer.fakes.FakeWorkoutExerciseDao
import com.organizer.fakes.ImmediateSyncTransactionRunner
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleSyncTest {
    private val owner = "user-a"
    private val syncId = "schedule-sync-1"
    private val workoutSyncId = "workout-sync-1"

    private inner class Fixture {
        val workouts = FakeWorkoutDao()
        val exercises = FakeExerciseDao()
        val outbox = FakeSyncOutboxDao()
        val staged = FakeSyncStagedDao()
        val schedules = FakeScheduledWorkoutDao()
        val reminders = FakeReminderScheduler()
        val applier = SyncStagedApplier(
            object : OwnerProvider() { override fun currentOwner() = "user-a" },
            workouts, exercises, outbox, FakeWorkoutExerciseDao(), null, schedules, reminders,
            staged, ImmediateSyncTransactionRunner(), Json { ignoreUnknownKeys = true },
        )

        suspend fun seedWorkout(id: Long = 1L) {
            workouts.insert(WorkoutEntity(workoutId = id, name = "W", ownerFirebaseUid = "user-a", syncId = "workout-sync-1"))
        }

        suspend fun stage(version: Long, deleted: Boolean, payload: String?, status: String = SyncStagedStatus.PENDING) {
            staged.upsertIfNewer("user-a", SyncEntityTypes.SCHEDULED_WORKOUT, syncId, version, 100L * version, deleted, payload, status, 100L * version)
        }

        fun record() = staged.records.value.single()
    }

    private fun payload(workoutSyncId: String = "workout-sync-1") =
        """{"schemaVersion":1,"workoutSyncId":"$workoutSyncId","dayOfWeek":1,"scheduledDateKey":20260105,"startMinutes":450,"reminderEnabled":true,"reminderMinutesBefore":30,"reminderNote":"n"}"""

    @Test
    fun `missing workout leaves schedule waiting then applies when workout arrives`() = runTest {
        val f = Fixture()
        f.stage(1, false, payload())
        assertEquals(StagedApplyResult.WAITING_DEPENDENCY, f.applier.process("user-a", f.record()))
        assertEquals(SyncStagedStatus.WAITING_DEPENDENCY, f.record().processingStatus)
        assertTrue(f.schedules.entries.value.isEmpty())

        f.seedWorkout()
        f.staged.resetToPending("user-a", setOf(SyncStagedStatus.WAITING_DEPENDENCY))
        assertEquals(StagedApplyResult.APPLIED, f.applier.process("user-a", f.record()))
        assertNotNull(f.schedules.findBySyncId("user-a", syncId))
        assertTrue(f.outbox.entries.value.isEmpty())
    }

    @Test
    fun `remote delete by syncId applies when local row absent`() = runTest {
        val f = Fixture()
        f.seedWorkout()
        f.stage(2, true, payload())
        assertEquals(StagedApplyResult.TOMBSTONE_ONLY, f.applier.process("user-a", f.record()))
        val row = f.schedules.findBySyncId("user-a", syncId)!!
        assertNotNull(row.deletedAt)
        assertEquals(2L, row.serverVersion)
    }

    @Test
    fun `durable tombstone prevents stale upsert resurrection`() = runTest {
        val f = Fixture()
        f.seedWorkout()
        f.stage(3, true, payload())
        assertEquals(StagedApplyResult.TOMBSTONE_ONLY, f.applier.process("user-a", f.record()))
        f.staged.upsertIfNewer("user-a", SyncEntityTypes.SCHEDULED_WORKOUT, syncId, 2, 50L, false, payload(), SyncStagedStatus.PENDING, 50L)
        // older delivery cannot replace the newer staged tombstone row
        assertEquals(3L, f.record().serverVersion)
    }

    @Test
    fun `older or equal server version cannot overwrite newer local state`() = runTest {
        val f = Fixture()
        f.seedWorkout()
        f.stage(5, false, payload())
        assertEquals(StagedApplyResult.APPLIED, f.applier.process("user-a", f.record()))
        val before = f.schedules.findBySyncId("user-a", syncId)!!
        f.staged.upsertIfNewer("user-a", SyncEntityTypes.SCHEDULED_WORKOUT, syncId, 4, 50L, false, payload(), SyncStagedStatus.PENDING, 50L)
        // staged row not replaced by older version; re-stage same-version tombstone not possible
        assertEquals(5L, f.record().serverVersion)
        assertEquals(before, f.schedules.findBySyncId("user-a", syncId))
    }

    @Test
    fun `same syncId with different workout reference is conflict blocked`() = runTest {
        val f = Fixture()
        f.seedWorkout()
        f.stage(1, false, payload())
        assertEquals(StagedApplyResult.APPLIED, f.applier.process("user-a", f.record()))
        f.workouts.insert(WorkoutEntity(workoutId = 2L, name = "W2", ownerFirebaseUid = "user-a", syncId = "workout-sync-2"))
        f.staged.upsertIfNewer("user-a", SyncEntityTypes.SCHEDULED_WORKOUT, syncId, 2, 200L, false, payload("workout-sync-2"), SyncStagedStatus.PENDING, 200L)
        assertEquals(StagedApplyResult.CONFLICT_BLOCKED, f.applier.process("user-a", f.record()))
    }

    @Test
    fun `remote apply schedules alarm and remote delete cancels it`() = runTest {
        val f = Fixture()
        f.seedWorkout()
        f.stage(1, false, payload())
        assertEquals(StagedApplyResult.APPLIED, f.applier.process("user-a", f.record()))
        val row = f.schedules.findBySyncId("user-a", syncId)!!
        assertTrue(row.scheduleId in f.reminders.scheduled)
        f.stage(2, true, payload())
        assertEquals(StagedApplyResult.APPLIED, f.applier.process("user-a", f.record()))
        assertTrue(row.scheduleId in f.reminders.cancelled)
    }

    @Test
    fun `disabled reminder cancels alarm on remote apply`() = runTest {
        val f = Fixture()
        f.seedWorkout()
        val disabled = """{"schemaVersion":1,"workoutSyncId":"workout-sync-1","dayOfWeek":1,"startMinutes":450,"reminderEnabled":false,"reminderMinutesBefore":30,"reminderNote":"n"}"""
        f.stage(1, false, disabled)
        assertEquals(StagedApplyResult.APPLIED, f.applier.process("user-a", f.record()))
        val row = f.schedules.findBySyncId("user-a", syncId)!!
        assertTrue(row.scheduleId in f.reminders.cancelled)
    }
}
