package com.organizer.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.organizer.auth.OwnerProvider
import com.organizer.data.local.db.entities.SyncOutboxEntity
import com.organizer.data.local.db.entities.SyncStagedRecordEntity
import com.organizer.data.local.db.entities.SyncStagedStatus
import com.organizer.data.local.db.entities.WorkoutEntity
import com.organizer.data.remote.model.SyncMutationResultDto
import com.organizer.sync.SyncOutboxAcknowledger
import com.organizer.sync.SyncReconciler
import com.organizer.sync.SyncStagedApplier
import com.organizer.sync.SyncTransactionRunner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SyncAcknowledgementInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val owner = "user-a"

    private class TestOwner : OwnerProvider() {
        override fun currentOwner(): String = "user-a"
    }

    private class FailingRunner(private val db: AppDatabase) : SyncTransactionRunner {
        override suspend fun <T> run(block: suspend () -> T): T = db.withTransaction {
            block()
            throw IllegalStateException("simulated crash")
        }
    }

    private fun openDb() = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()

    private fun result(mutationId: String, syncId: String, status: String, version: Long) = SyncMutationResultDto(
        mutationId = mutationId,
        syncId = syncId,
        status = status,
        serverVersion = version,
        serverUpdatedAt = "2026-11-24T10:00:00Z",
    )

    private fun mutation(syncId: String) = SyncOutboxEntity(
        mutationId = UUID.randomUUID().toString(),
        ownerFirebaseUid = owner,
        entityType = "WORKOUT",
        recordSyncId = syncId,
        operation = "UPSERT",
        baseVersion = 0,
    )

    @Test
    fun migrationPreservesOutboxRowsAndAddsMetadata() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name("outbox-ack-migration-test.db")
                .callback(object : SupportSQLiteOpenHelper.Callback(16) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """CREATE TABLE sync_outbox (
                               outboxId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                               mutationId TEXT NOT NULL,
                               ownerFirebaseUid TEXT NOT NULL,
                               entityType TEXT NOT NULL,
                               recordSyncId TEXT NOT NULL,
                               operation TEXT NOT NULL,
                               payload TEXT,
                               baseVersion INTEGER NOT NULL,
                               createdAt INTEGER NOT NULL,
                               retryCount INTEGER NOT NULL,
                               lastError TEXT,
                               syncStatus TEXT NOT NULL)"""
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        val db = helper.writableDatabase
        try {
            db.execSQL(
                """INSERT INTO sync_outbox
                   (mutationId, ownerFirebaseUid, entityType, recordSyncId, operation,
                    baseVersion, createdAt, retryCount, syncStatus)
                   VALUES ('m1', 'user-a', 'WORKOUT', 's1', 'UPSERT', 0, 1000, 0, 'PENDING_UPLOAD')"""
            )

            OUTBOX_ACKNOWLEDGEMENT_MIGRATION_16_17.migrate(db)

            val columns = db.query("PRAGMA table_info('sync_outbox')").use { c ->
                val names = mutableListOf<String>()
                while (c.moveToNext()) names += c.getString(c.getColumnIndex("name"))
                names
            }
            assertTrue(columns.containsAll(listOf("serverVersion", "serverUpdatedAt", "acknowledgedAt", "terminalErrorCode")))

            val indexes = db.query("PRAGMA index_list('sync_outbox')").use { c ->
                val names = mutableListOf<String>()
                while (c.moveToNext()) names += c.getString(c.getColumnIndex("name"))
                names
            }
            assertTrue(indexes.contains("index_sync_outbox_ownerFirebaseUid_entityType_recordSyncId"))

            db.query("SELECT mutationId, syncStatus, serverVersion FROM sync_outbox").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("m1", c.getString(0))
                assertEquals("PENDING_UPLOAD", c.getString(1))
                assertTrue(c.isNull(2))
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun acknowledgementIsAtomic() = runBlocking {
        val db = openDb()
        try {
            val syncId = UUID.randomUUID().toString()
            val m = mutation(syncId)
            db.syncOutboxDao.insert(m)
            db.workoutDao.insert(
                WorkoutEntity(workoutId = 1L, name = "Local", ownerFirebaseUid = owner, syncId = syncId)
            )

            val acknowledger = SyncOutboxAcknowledger(
                TestOwner(), db.syncOutboxDao, db.workoutDao, db.exerciseDao, db.workoutExerciseDao, db.favoriteExerciseDao, null, null, null,
                com.organizer.sync.RoomSyncTransactionRunner(db),
            )
            acknowledger.acknowledge(owner, listOf(result(m.mutationId, syncId, "APPLIED", 4)))

            assertEquals("ACKNOWLEDGED", db.syncOutboxDao.findByMutationId(owner, m.mutationId)!!.syncStatus)
            val workout = db.workoutDao.findBySyncId(owner, syncId)!!
            assertEquals(4L, workout.serverVersion)
            assertEquals("SYNCED", workout.syncStatus)

            val failing = SyncOutboxAcknowledger(
                TestOwner(), db.syncOutboxDao, db.workoutDao, db.exerciseDao, db.workoutExerciseDao, db.favoriteExerciseDao, null, null, null, FailingRunner(db),
            )
            val m2 = mutation(UUID.randomUUID().toString())
            db.syncOutboxDao.insert(m2)
            val thrown = runCatching {
                failing.acknowledge(owner, listOf(result(m2.mutationId, m2.recordSyncId, "APPLIED", 9)))
            }.exceptionOrNull()
            assertNotNull(thrown)
            assertEquals("PENDING_UPLOAD", db.syncOutboxDao.findByMutationId(owner, m2.mutationId)!!.syncStatus)
        } finally {
            db.close()
        }
    }

    @Test
    fun reconciliationRecoversAfterAcknowledgementWithoutRedownload() = runBlocking {
        val db = openDb()
        try {
            val syncId = UUID.randomUUID().toString()
            db.syncStagedDao.upsertIfNewer(
                owner, "WORKOUT", syncId, 2, 1_700_000_000_000L, false,
                """{"schemaVersion":1,"name":"Remote","notes":""}""", SyncStagedStatus.LOCAL_PENDING, 1_700_000_000_000L,
            )
            assertEquals(SyncStagedStatus.LOCAL_PENDING, db.syncStagedDao.find(owner, "WORKOUT", syncId)!!.processingStatus)

            val applier = SyncStagedApplier(
                TestOwner(), db.workoutDao, db.exerciseDao, db.syncOutboxDao, db.workoutExerciseDao, db.favoriteExerciseDao, null, null, null, null, db.syncStagedDao,
                com.organizer.sync.RoomSyncTransactionRunner(db), Json { ignoreUnknownKeys = true },
            )
            val reconciler = SyncReconciler(
                TestOwner(), db.syncOutboxDao, db.syncStagedDao, applier,
                com.organizer.sync.RoomSyncTransactionRunner(db),
            )

            val report = reconciler.reconcile()
            assertEquals(1, report.applied)
            assertEquals(SyncStagedStatus.APPLIED, db.syncStagedDao.find(owner, "WORKOUT", syncId)!!.processingStatus)
            assertEquals("Remote", db.workoutDao.getAll(owner).first().single().name)
            assertEquals(2L, db.workoutDao.findBySyncId(owner, syncId)!!.serverVersion)
        } finally {
            db.close()
        }
    }
}