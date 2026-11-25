package com.organizer.data.repo

import com.organizer.auth.OwnerProvider
import com.organizer.data.local.dao.ExerciseDao
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.remote.repo.ExerciseRemoteDataSource
import com.organizer.sync.OwnerChangedDuringLocalWriteException
import com.organizer.sync.SyncOutboxWriter
import com.organizer.sync.SyncTransactionRunner
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Outcome of a catalog refresh; skipped names are reported, never logged. */
data class CatalogRefreshResult(
    val inserted: Int = 0,
    val updated: Int = 0,
    val skippedNameCollision: Int = 0,
)

class ExerciseRepository @Inject constructor(
    private val exerciseDao: ExerciseDao,
    private val remoteDataSource: ExerciseRemoteDataSource,
    private val fileRepository: FileRepository,
    private val ownerProvider: OwnerProvider,
    private val transactions: SyncTransactionRunner,
    private val outboxWriter: SyncOutboxWriter,
) {
    suspend fun addCustomExercise(
        name: String,
        instructions: List<String>,
        imageName: String,
        exerciseType: String = "REPS_WEIGHT",
    ) {
        val owner = ownerProvider.currentOwner()
        val cleanedInstructions = instructions.filter { it.isNotBlank() }
        val exercise = ExerciseEntity(
            name = name,
            instructions = cleanedInstructions,
            imageUrls = listOf(imageName),
            isCustom = true,
            exerciseType = exerciseType,
            ownerFirebaseUid = owner,
            syncId = java.util.UUID.randomUUID().toString(),
        )
        transactions.run {
            if (ownerProvider.currentOwner() != owner) throw OwnerChangedDuringLocalWriteException()
            exerciseDao.insert(exercise)
            outboxWriter.upsertCustomExercise(owner, exercise)
        }
    }

    fun observeAllExercises(): Flow<List<ExerciseEntity>> {
        return exerciseDao.getAll(ownerProvider.currentOwner())
    }

    fun observeCustomExercises(): Flow<List<ExerciseEntity>> {
        return exerciseDao.getCustomExercises(ownerProvider.currentOwner())
    }

    fun observeExercisesByCategory(categoryId: Long): Flow<List<ExerciseEntity>> {
        return exerciseDao.observeExercisesByCategory(categoryId, ownerProvider.currentOwner())
    }

    fun observeExercisesByWorkout(workoutId: Long) =
        exerciseDao.observeExercisesByWorkout(workoutId, ownerProvider.currentOwner())

    fun observeExercise(id: Long): Flow<ExerciseEntity?> {
        return exerciseDao.getById(ownerProvider.currentOwner(), id)
    }

    suspend fun refreshExercises(): CatalogRefreshResult {
        // Catalog identity is publicExerciseId; local exerciseId is a private
        // Room PK allocated on insert and never taken from the catalog DTO.
        val catalog = remoteDataSource.getAll()
        val localPublic = exerciseDao.getAllPublicOnce()
        val byCatalogId = localPublic.associateBy { it.publicExerciseId }
        // The name index is global and covers soft-deleted and other-owner
        // rows. Any collision is skipped instead of aborting the whole refresh.
        val usedNames = exerciseDao.getAllNamesOnce().toMutableSet()

        val toUpdate = mutableListOf<ExerciseEntity>()
        val toInsert = mutableListOf<ExerciseEntity>()
        var skippedNameCollision = 0
        catalog.forEach { remote ->
            val catalogId = remote.publicExerciseId ?: return@forEach
            val existing = byCatalogId[catalogId]
            if (existing != null) {
                if (existing.name != remote.name ||
                    existing.instructions != remote.instructions ||
                    existing.imageUrls != remote.imageUrls ||
                    existing.exerciseType != remote.exerciseType
                ) {
                    if (remote.name != existing.name && remote.name in usedNames) {
                        skippedNameCollision++
                        return@forEach
                    }
                    // In-place update by local PK; local metadata is untouched.
                    toUpdate += existing.copy(
                        name = remote.name,
                        instructions = remote.instructions,
                        imageUrls = remote.imageUrls,
                        exerciseType = remote.exerciseType,
                    )
                    usedNames += remote.name
                    // Names are globally unique, so the old name is free after
                    // this in-place rename.
                    usedNames -= existing.name
                }
            } else if (toInsert.none { it.publicExerciseId == catalogId }) {
                if (remote.name in usedNames) {
                    skippedNameCollision++
                    return@forEach
                }
                // A catalog ID colliding with a custom PK is harmless: Room
                // allocates a fresh local PK.
                toInsert += remote
                usedNames += remote.name
            }
        }

        // Apply updates first: an unchanged name for the same catalog row is
        // not a collision, and doing this before inserts avoids transient
        // conflicts when the catalog renames rows.
        exerciseDao.updateAll(toUpdate)
        exerciseDao.insertPublicAll(toInsert)

        // Entries missing from this refresh are deliberately left in place:
        // deleting a public row would cascade-delete user-owned children.
        fileRepository.enqueueImages((toInsert + toUpdate).flatMap { it.imageUrls })
        return CatalogRefreshResult(toInsert.size, toUpdate.size, skippedNameCollision)
    }

    suspend fun enqueueMissingImages() {
        val localExercises = exerciseDao.getAllOnce(ownerProvider.currentOwner())
        fileRepository.enqueueImages(
            localExercises.filter { !it.isCustom }.flatMap { it.imageUrls }
        )
    }

    /**
     * Soft-deletes a custom exercise only. The row and all of its relationships
     * are preserved so workout history, planned sets and favorites can still
     * resolve the exercise.
     */
    suspend fun deleteCustomExercise(id: Long) {
        val owner = ownerProvider.currentOwner()
        val now = System.currentTimeMillis()
        transactions.run {
            if (ownerProvider.currentOwner() != owner) throw OwnerChangedDuringLocalWriteException()
            val exercise = exerciseDao.getByIdOnce(owner, id)
            if (exercise?.isCustom == true && exercise.ownerFirebaseUid == owner && exercise.deletedAt == null) {
                exerciseDao.softDeleteCustom(owner, id, now, now)
                outboxWriter.deleteCustomExercise(owner, exercise.copy(deletedAt = now))
            }
        }
    }
}