package com.organizer.sync

import com.organizer.data.local.db.entities.ScheduledWorkoutEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ScheduledWorkoutPayloadTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `payload matches contract and excludes local ids and owner`() {
        val schedule = ScheduledWorkoutEntity(
            scheduleId = 99L,
            workoutId = 7L,
            dayOfWeek = 1,
            scheduledDateKey = 20260101,
            startMinutes = 450,
            reminderEnabled = true,
            reminderMinutesBefore = 30,
            reminderNote = "Bring towel",
            ownerFirebaseUid = "user-a",
            syncId = "sync-1",
        )
        val raw = SyncPayloadFactory(json).scheduledWorkout(schedule, "workout-sync-1")
        val obj = json.parseToJsonElement(raw).jsonObject
        assertEquals(1, obj["schemaVersion"]!!.toString().toInt())
        assertEquals("workout-sync-1", obj["workoutSyncId"]!!.toString().trim('"'))
        assertEquals("1", obj["dayOfWeek"]!!.toString())
        assertEquals("20260101", obj["scheduledDateKey"]!!.toString())
        assertEquals("450", obj["startMinutes"]!!.toString())
        assertEquals("true", obj["reminderEnabled"]!!.toString())
        assertEquals("30", obj["reminderMinutesBefore"]!!.toString())
        assertEquals("Bring towel", obj["reminderNote"]!!.toString().trim('"'))
        assertFalse(obj.containsKey("addedAt"))
        assertFalse(obj.containsKey("scheduleId"))
        assertFalse(obj.containsKey("workoutId"))
        assertFalse(obj.containsKey("ownerFirebaseUid"))
    }
}