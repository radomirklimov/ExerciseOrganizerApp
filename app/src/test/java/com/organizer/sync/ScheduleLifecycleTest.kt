package com.organizer.sync

import com.organizer.auth.OwnerProvider
import com.organizer.data.local.db.entities.ScheduledWorkoutEntity
import com.organizer.data.local.db.entities.WorkoutEntity
import com.organizer.data.repo.ScheduleRepository
import com.organizer.fakes.FakeExerciseDao
import com.organizer.fakes.FakeReminderScheduler
import com.organizer.fakes.FakeScheduledWorkoutDao
import com.organizer.fakes.FakeSyncOutboxDao
import com.organizer.fakes.FakeWorkoutDao
import com.organizer.fakes.ImmediateSyncTransactionRunner
import com.organizer.fakes.fakeOutboxWriter
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleLifecycleTest {
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

    private class Fixture(val owner: TestOwner = TestOwner()) {
        val schedules = FakeScheduledWorkoutDao()
        val workouts = FakeWorkoutDao()
        val exercises = FakeExerciseDao()
        val outbox = FakeSyncOutboxDao()
        val reminders = FakeReminderScheduler()
        val repo = ScheduleRepository(
            schedules, workouts, outbox, owner, ImmediateSyncTransactionRunner(),
            fakeOutboxWriter(outbox).first, SyncPayloadFactory(Json { ignoreUnknownKeys = true }), SyncOperationLocks(), reminders,
        )

        suspend fun seedWorkout(id: Long = 1L, ownerUid: String = "user-a") {
            workouts.insert(
                WorkoutEntity(
                    workoutId = id, name = "Routine", ownerFirebaseUid = ownerUid,
                    syncId = "workout-sync-$id",
                )
            )
        }

        fun entry(id: Long) = schedules.entries.value.firstOrNull { it.scheduleId == id }
        fun outboxFor(syncId: String) = outbox.entries.value.filter { it.recordSyncId == syncId }
    }

    private suspend fun Fixture.add(day: Int = 1, date: Int? = null, start: Int = 450): Long =
        repo.addEntry(1L, day, date, start, true, 30, "note")

    @Test
    fun `create writes one schedule and one upsert atomically with random syncId`() = runTest {
        val f = Fixture()
        f.seedWorkout()
        val id = f.add()
        assertEquals(1, f.schedules.entries.value.size)
        val row = f.entry(id)!!
        assertNotNull(row.syncId)
        assertEquals(1, f.outbox.entries.value.size)
        val m = f.outbox.entries.value.single()
        assertEquals(SyncEntityTypes.SCHEDULED_WORKOUT, m.entityType)
        assertEquals("UPSERT", m.operation)
        assertEquals(row.syncId, m.recordSyncId)
    }

    @Test
    fun `update preserves syncId and produces upsert`() = runTest {
        val f = Fixture()
        f.seedWorkout()
        val id = f.add()
        val before = f.entry(id)!!
        f.repo.updateEntry(before.copy(reminderNote = "changed"))
        val after = f.entry(id)!!
        assertEquals(before.syncId, after.syncId)
        assertEquals("changed", after.reminderNote)
        assertTrue(f.outboxFor(after.syncId).isNotEmpty())
        assertTrue(f.outboxFor(after.syncId).any { it.operation == "UPSERT" })
    }

    @Test
    fun `delete soft-deletes and re-add creates new syncId`() = runTest {
        val f = Fixture()
        f.seedWorkout()
        val id = f.add()
        val original = f.entry(id)!!
        f.repo.deleteEntry(id)
        assertNotNull(f.entry(id)!!.deletedAt)
        // Never-transmitted UPSERT is superseded rather than followed by a DELETE.
        assertTrue(f.outboxFor(original.syncId).all { it.syncStatus == SyncOutboxStatus.SUPERSEDED })
        val newId = f.add()
        val reAdded = f.entry(newId)!!
        assertNotEquals(original.syncId, reAdded.syncId)
        assertNotEquals(original.scheduleId, reAdded.scheduleId)
    }

    @Test
    fun `independent creations produce distinct syncIds`() = runTest {
        val f = Fixture()
        f.seedWorkout()
        val a = f.entry(f.add())!!
        val b = f.entry(f.add(day = 2))!!
        assertNotEquals(a.syncId, b.syncId)
    }

    @Test
    fun `guest writes remain local-only`() = runTest {
        val f = Fixture(TestOwner().apply { uid = OwnerProvider.GUEST })
        f.seedWorkout(ownerUid = OwnerProvider.GUEST)
        val id = f.add()
        f.repo.updateEntry(f.entry(id)!!.copy(reminderNote = "guest"))
        f.repo.deleteEntry(id)
        assertEquals(0, f.outbox.entries.value.size)
    }

    @Test
    fun `owner switch inside transaction aborts`() = runTest {
        val f = Fixture(TestOwner().apply { switchAtCall = 2 })
        f.seedWorkout()
        val thrown = runCatching { f.add() }.exceptionOrNull()
        assertTrue(thrown is OwnerChangedDuringLocalWriteException)
        assertTrue(f.schedules.entries.value.isEmpty())
        assertTrue(f.outbox.entries.value.isEmpty())
    }

    @Test
    fun `invalid fields and inconsistent one-time date are rejected`() = runTest {
        val f = Fixture()
        f.seedWorkout()
        assertTrue(runCatching { f.add(day = 0) }.exceptionOrNull() != null)
        assertTrue(runCatching { f.add(start = 1500) }.exceptionOrNull() != null)
        // 2026-01-01 is Thursday (ISO 4); passing day 1 is inconsistent.
        assertTrue(runCatching { f.add(day = 1, date = 20260101) }.exceptionOrNull() != null)
        // consistent one-time succeeds
        f.add(day = 4, date = 20260101)
        assertEquals(1, f.schedules.entries.value.size)
    }

    @Test
    fun `local create update and delete drive alarms idempotently by scheduleId`() = runTest {
        val f = Fixture()
        f.seedWorkout()
        val id = f.add()
        assertEquals(listOf(id), f.reminders.scheduled)
        f.repo.updateEntry(f.entry(id)!!.copy(reminderNote = "x"))
        assertEquals(2, f.reminders.scheduled.count { it == id })
        f.repo.deleteEntry(id)
        assertTrue(id in f.reminders.cancelled)
    }

    @Test
    fun `disabled reminders cancel alarm`() = runTest {
        val f = Fixture()
        f.seedWorkout()
        val id = f.repo.addEntry(1L, 1, null, 450, false, 30, "note")
        assertTrue(id in f.reminders.cancelled)
    }

    @Test
    fun `reminder failure does not roll back database write`() = runTest {
        val f = Fixture()
        f.seedWorkout()
        f.reminders.failNext = true
        val id = f.add()
        assertEquals(1, f.schedules.entries.value.size)
        assertNotNull(f.entry(id))
        assertTrue(f.outbox.entries.value.isNotEmpty())
    }

    @Test
    fun `delete never physically removes schedule row`() = runTest {
        val f = Fixture()
        f.seedWorkout()
        val id = f.add()
        f.repo.deleteEntry(id)
        assertEquals(1, f.schedules.entries.value.size)
        assertNotNull(f.entry(id))
    }
}
