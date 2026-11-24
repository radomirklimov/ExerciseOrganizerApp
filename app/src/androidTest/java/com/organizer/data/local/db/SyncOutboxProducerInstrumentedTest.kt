package com.organizer.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.organizer.auth.OwnerProvider
import com.organizer.data.repo.WorkoutRepository
import com.organizer.sync.RoomSyncTransactionRunner
import com.organizer.sync.SyncOutboxWriter
import com.organizer.sync.SyncPayloadFactory
import com.organizer.sync.SyncTransactionRunner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncOutboxProducerInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val owner = "user-a"

    private class FixedOwner(private val uid: String) : OwnerProvider() {
        override fun currentOwner(): String = uid
    }

    private class ThrowingRunner(private val database: AppDatabase) : SyncTransactionRunner {
        override suspend fun <T> run(block: suspend () -> T): T = database.withTransaction {
            block()
            throw IllegalStateException("simulated failure after entity write")
        }
    }

    private fun openDb(): AppDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    private fun repository(db: AppDatabase, runner: SyncTransactionRunner = RoomSyncTransactionRunner(db)) =
        WorkoutRepository(
            db.workoutDao,
            FixedOwner(owner),
            runner,
            SyncOutboxWriter(db.syncOutboxDao, SyncPayloadFactory(Json { ignoreUnknownKeys = true })),
        )

    @Test
    fun outboxProducerPersistsAndCoalescesUnsentEdits() = runBlocking {
        val db = openDb()
        try {
            val repo = repository(db)
            val id = repo.createWorkout("Push Day", "notes")

            val first = db.syncOutboxDao.getPending(owner).single()
            assertEquals("WORKOUT", first.entityType)
            assertEquals("UPSERT", first.operation)
            assertEquals(0L, first.baseVersion)
            assertTrue(id > 0L)
            val payload = Json.parseToJsonElement(first.payload!!).jsonObject
            assertEquals(1, payload["schemaVersion"]!!.jsonPrimitive.content.toInt())
            assertEquals("Push Day", payload["name"]!!.jsonPrimitive.content)

            repo.updateWorkoutNotes(id, "updated")
            val coalesced = db.syncOutboxDao.getPending(owner).single()
            assertEquals(first.mutationId, coalesced.mutationId)
            assertEquals("updated", Json.parseToJsonElement(coalesced.payload!!).jsonObject["notes"]!!.jsonPrimitive.content)

            db.syncOutboxDao.updateRetry(owner, first.mutationId, 1, "sent", "SENT")
            repo.updateWorkoutNotes(id, "after send")
            assertEquals(2, db.syncOutboxDao.getPending(owner).size)
        } finally {
            db.close()
        }
    }

    @Test
    fun entityWriteRollsBackWhenOutboxTransactionFails() = runBlocking {
        val db = openDb()
        try {
            val repo = repository(db, ThrowingRunner(db))
            val thrown = runCatching { repo.createWorkout("Doomed") }.exceptionOrNull()
            assertTrue(thrown is IllegalStateException)

            assertTrue(db.workoutDao.getAll(owner).first().isEmpty())
            assertTrue(db.syncOutboxDao.getPending(owner).isEmpty())
        } finally {
            db.close()
        }
    }
}