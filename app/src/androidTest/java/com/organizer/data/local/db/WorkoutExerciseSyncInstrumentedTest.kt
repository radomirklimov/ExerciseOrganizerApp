package com.organizer.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.organizer.auth.OwnerProvider
import com.organizer.data.local.dao.SyncOutboxDao
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.local.db.entities.SyncOutboxEntity
import com.organizer.data.local.db.entities.SyncStagedRecordEntity
import com.organizer.data.local.db.entities.SyncStagedStatus
import com.organizer.data.local.db.entities.WorkoutEntity
import com.organizer.data.local.db.entities.WorkoutExerciseEntity
import com.organizer.data.repo.WorkoutExerciseRepository
import com.organizer.sync.RoomSyncTransactionRunner
import com.organizer.sync.SyncOutboxWriter
import com.organizer.sync.SyncPayloadFactory
import com.organizer.sync.SyncStagedApplier
import com.organizer.sync.SyncTransactionRunner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutExerciseSyncInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val owner = "user-a"

    private fun openDb() = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()

    private suspend fun seedParents(db: AppDatabase) {
        db.workoutDao.insert(
            WorkoutEntity(workoutId = 1L, name = "Routine", ownerFirebaseUid = owner, syncId = "workout-sync")
        )
        db.exerciseDao.insert(
            ExerciseEntity(
                exerciseId = 10L, name = "Catalog", instructions = emptyList(), imageUrls = emptyList(),
                isCustom = false, ownerFirebaseUid = OwnerProvider.PUBLIC, publicExerciseId = 42,
            )
        )
    }

    @Test
    fun remoteUpsertInsertsMissingAssociation() = runBlocking {
        val db = openDb()
        try {
            seedParents(db)
            db.workoutExerciseDao.insertRemote(
                WorkoutExerciseEntity(
                    workoutId = 1L, exerciseId = 10L, orderIndex = 2, ownerFirebaseUid = owner,
                    syncId = "remote-link", serverVersion = 3, syncStatus = "SYNCED",
                )
            )
            val stored = db.workoutExerciseDao.findByPair(owner, 1L, 10L)!!
            assertEquals("remote-link", stored.syncId)
            assertEquals(2, stored.orderIndex)
            assertEquals(3L, stored.serverVersion)
        } finally {
            db.close()
        }
    }

    @Test
    fun remoteUpsertPreservesLocalSyncIdAndIsVersionMonotonic() = runBlocking {
        val db = openDb()
        try {
            seedParents(db)
            db.workoutExerciseDao.insert(
                WorkoutExerciseEntity(
                    workoutId = 1L, exerciseId = 10L, orderIndex = 0, ownerFirebaseUid = owner,
                    syncId = "local-link", serverVersion = 5, syncStatus = "SYNCED",
                )
            )
            db.workoutExerciseDao.applyRemoteUpsert(owner, 1L, 10L, 9, 2, 100L)
            db.workoutExerciseDao.applyRemoteUpsert(owner, 1L, 10L, 1, 6, 200L)

            val stored = db.workoutExerciseDao.findByPair(owner, 1L, 10L)!!
            assertEquals("local-link", stored.syncId)
            assertEquals(6L, stored.serverVersion)
            assertEquals(1, stored.orderIndex)
        } finally {
            db.close()
        }
    }

    @Test
    fun acknowledgementMetadataIsVersionMonotonic() = runBlocking {
        val db = openDb()
        try {
            seedParents(db)
            db.workoutExerciseDao.insert(
                WorkoutExerciseEntity(
                    workoutId = 1L, exerciseId = 10L, orderIndex = 0, ownerFirebaseUid = owner,
                    syncId = "link", serverVersion = 5,
                )
            )
            db.workoutExerciseDao.applyAcknowledgementMetadata(owner, "link", 2, 100L, "SYNCED")
            assertEquals(5L, db.workoutExerciseDao.findBySyncId(owner, "link")!!.serverVersion)
            db.workoutExerciseDao.applyAcknowledgementMetadata(owner, "link", 7, 200L, "SYNCED")
            assertEquals(7L, db.workoutExerciseDao.findBySyncId(owner, "link")!!.serverVersion)
        } finally {
            db.close()
        }
    }

    @Test
    fun softDeleteAndRestorePreserveIdentityAndStatus() = runBlocking {
        val db = openDb()
        try {
            seedParents(db)
            db.workoutExerciseDao.insert(
                WorkoutExerciseEntity(
                    workoutId = 1L, exerciseId = 10L, orderIndex = 0, ownerFirebaseUid = owner, syncId = "link",
                )
            )
            assertEquals(1, db.workoutExerciseDao.softDelete(owner, 1L, 10L, 100L, 100L))
            assertEquals(100L, db.workoutExerciseDao.findByPair(owner, 1L, 10L)!!.deletedAt!!)
            assertEquals(1, db.workoutExerciseDao.restoreLocal(owner, 1L, 10L, 200L))
            val restored = db.workoutExerciseDao.findByPair(owner, 1L, 10L)!!
            assertEquals("link", restored.syncId)
            assertNull(restored.deletedAt)
            assertEquals("PENDING_UPLOAD", restored.syncStatus)
        } finally {
            db.close()
        }
    }

    @Test
    fun ownerScopedMutationsDoNotAffectAnotherOwner() = runBlocking {
        val db = openDb()
        try {
            seedParents(db)
            db.workoutExerciseDao.insert(
                WorkoutExerciseEntity(
                    workoutId = 1L, exerciseId = 10L, orderIndex = 0, ownerFirebaseUid = owner, syncId = "link",
                )
            )
            assertEquals(0, db.workoutExerciseDao.softDelete("user-b", 1L, 10L, 100L, 100L))
            assertNull(db.workoutExerciseDao.findByPair(owner, 1L, 10L)!!.deletedAt)
        } finally {
            db.close()
        }
    }

    @Test
    fun entityAndOutboxRollBackTogetherOnFailure() = runBlocking {
        val db = openDb()
        try {
            seedParents(db)
            val failingRunner = object : SyncTransactionRunner {
                override suspend fun <T> run(block: suspend () -> T): T = db.withTransaction {
                    block()
                    throw IllegalStateException("simulated failure")
                }
            }
            val writer = SyncOutboxWriter(db.syncOutboxDao, SyncPayloadFactory(Json { ignoreUnknownKeys = true }))
            val repo = WorkoutExerciseRepository(
                db.workoutExerciseDao, db.workoutDao, db.exerciseDao, db.syncOutboxDao, db.syncStagedDao,
                object : OwnerProvider() { override fun currentOwner() = owner },
                failingRunner, writer, SyncPayloadFactory(Json { ignoreUnknownKeys = true }),
                com.organizer.sync.SyncOperationLocks(),
            )
            val thrown = runCatching { repo.addExerciseToWorkout(1L, 10L) }.exceptionOrNull()
            assertNotNull(thrown)
            assertNull(db.workoutExerciseDao.findByPair(owner, 1L, 10L))
            assertTrue(db.syncOutboxDao.getPending(owner).isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun waitingAssociationCanBeRetriedAfterParentsExist() = runBlocking {
        val db = openDb()
        try {
            val runner = RoomSyncTransactionRunner(db)
            val applier = SyncStagedApplier(
                object : OwnerProvider() { override fun currentOwner() = owner },
                db.workoutDao, db.exerciseDao, db.syncOutboxDao, db.workoutExerciseDao,
                db.syncStagedDao, runner, Json { ignoreUnknownKeys = true },
            )
            val stagedId = 1L
            db.syncStagedDao.upsertIfNewer(
                owner, "WORKOUT_EXERCISE", "link", 1, 100L, false,
                """{"schemaVersion":1,"workoutSyncId":"workout-sync","exerciseRef":{"kind":"PUBLIC","exerciseId":42},"orderIndex":0}""",
                SyncStagedStatus.PENDING, 100L,
            )
            val waiting = applier.process(owner, db.syncStagedDao.findByStagedId(owner, stagedId)!!)
            assertEquals(
                com.organizer.sync.StagedApplyResult.WAITING_DEPENDENCY,
                waiting,
            )
            assertNull(db.workoutExerciseDao.findByPair(owner, 1L, 10L))

            seedParents(db)
            db.syncStagedDao.resetToPending(owner, setOf(SyncStagedStatus.WAITING_DEPENDENCY))
            assertEquals(
                com.organizer.sync.StagedApplyResult.APPLIED,
                applier.process(owner, db.syncStagedDao.findByStagedId(owner, stagedId)!!),
            )
            assertNotNull(db.workoutExerciseDao.findBySyncId(owner, "link"))
            assertTrue(db.syncOutboxDao.getPending(owner).isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun roomEnforcesDuplicateSyncIdAndForeignKeys() = runBlocking {
        val db = openDb()
        try {
            seedParents(db)
            db.workoutExerciseDao.insertRemote(
                WorkoutExerciseEntity(
                    workoutId = 1L, exerciseId = 10L, orderIndex = 0, ownerFirebaseUid = owner, syncId = "link",
                )
            )
            val duplicateSync = runCatching {
                db.workoutExerciseDao.insertRemote(
                    WorkoutExerciseEntity(
                        workoutId = 1L, exerciseId = 10L, orderIndex = 0, ownerFirebaseUid = owner,
                        syncId = "link", serverVersion = 2,
                    )
                )
            }.exceptionOrNull()
            val missingParent = runCatching {
                db.workoutExerciseDao.insertRemote(
                    WorkoutExerciseEntity(
                        workoutId = 999L, exerciseId = 10L, orderIndex = 0, ownerFirebaseUid = owner,
                        syncId = "orphan",
                    )
                )
            }.exceptionOrNull()

            assertNotNull(duplicateSync)
            assertNotNull(missingParent)
            assertEquals(1, db.workoutExerciseDao.getAll(owner).first().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun tombstonePersistsAcrossDatabaseReopen() = runBlocking {
        val name = "association-tombstone-test.db"
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        if (file.exists()) assertTrue(file.delete())
        val first = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().build()
        try {
            seedParents(first)
            first.workoutExerciseDao.insertRemote(
                WorkoutExerciseEntity(
                    workoutId = 1L, exerciseId = 10L, orderIndex = 0, ownerFirebaseUid = owner,
                    syncId = "tombstone", serverVersion = 4, deletedAt = 100L, syncStatus = "SYNCED",
                )
            )
        } finally {
            first.close()
        }

        val reopened = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().build()
        try {
            val stored = reopened.workoutExerciseDao.findBySyncId(owner, "tombstone")!!
            assertNotNull(stored.deletedAt)
            assertEquals(4L, stored.serverVersion)
        } finally {
            reopened.close()
        }
    }

    @Test
    fun identityCollisionIsDurableWithRealRoom() = runBlocking {
        val db = openDb()
        try {
            seedParents(db)
            db.workoutExerciseDao.insert(
                WorkoutExerciseEntity(
                    workoutId = 1L, exerciseId = 10L, orderIndex = 0, ownerFirebaseUid = owner, syncId = "local-link",
                )
            )
            val applier = SyncStagedApplier(
                object : OwnerProvider() { override fun currentOwner() = owner },
                db.workoutDao, db.exerciseDao, db.syncOutboxDao, db.workoutExerciseDao,
                db.syncStagedDao, RoomSyncTransactionRunner(db), Json { ignoreUnknownKeys = true },
            )
            db.syncStagedDao.upsertIfNewer(
                owner, "WORKOUT_EXERCISE", "remote-link", 2, 100L, false,
                """{"schemaVersion":1,"workoutSyncId":"workout-sync","exerciseRef":{"kind":"PUBLIC","exerciseId":42},"orderIndex":0}""",
                SyncStagedStatus.PENDING, 100L,
            )

            assertEquals(
                com.organizer.sync.StagedApplyResult.CONFLICT_BLOCKED,
                applier.process(owner, db.syncStagedDao.findByStagedId(owner, 1L)!!),
            )
            assertEquals(
                SyncStagedStatus.CONFLICT_BLOCKED,
                db.syncStagedDao.findByStagedId(owner, 1L)!!.processingStatus,
            )
        } finally {
            db.close()
        }
    }
}
