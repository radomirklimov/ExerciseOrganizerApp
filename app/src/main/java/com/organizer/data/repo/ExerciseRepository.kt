package com.organizer.data.repo

import com.organizer.auth.OwnerProvider
import com.organizer.data.local.dao.ExerciseDao
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.remote.repo.ExerciseRemoteDataSource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ExerciseRepository @Inject constructor(
    private val exerciseDao: ExerciseDao,
    private val remoteDataSource: ExerciseRemoteDataSource,
    private val fileRepository: FileRepository,
    private val ownerProvider: OwnerProvider,
) {
    suspend fun addCustomExercise(
        name: String,
        instructions: List<String>,
        imageName: String,
        exerciseType: String = "REPS_WEIGHT",
    ) {
        val cleanedInstructions = instructions.filter { it.isNotBlank() }
        exerciseDao.insert(
            ExerciseEntity(
                name = name,
                instructions = cleanedInstructions,
                imageUrls = listOf(imageName),
                isCustom = true,
                exerciseType = exerciseType,
                ownerFirebaseUid = ownerProvider.currentOwner(),
                syncId = java.util.UUID.randomUUID().toString(),
            )
        )
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

    suspend fun refreshExercises() {
        val owner = ownerProvider.currentOwner()
        val remoteExercises = remoteDataSource.getAll()
        val remoteIds = remoteExercises.map { it.exerciseId }

        val localExercises = exerciseDao.getAllOnce(owner)
        val localIds = localExercises.map { it.exerciseId }

        val toInsert = remoteExercises.filter { it.exerciseId !in localIds }

        val toUpdate = remoteExercises.filter { remote ->
            localExercises.any { local ->
                local.exerciseId == remote.exerciseId && local != remote
            }
        }

        val toDelete = localExercises.filter { local ->
            local.exerciseId !in remoteIds && !local.isCustom
        }

        toDelete.forEach { exerciseDao.delete(it) }
        exerciseDao.insertAll(toInsert)
        exerciseDao.updateAll(toUpdate)

        val referencedImages =
            (remoteExercises + localExercises.filter { it.isCustom })
                .flatMap { it.imageUrls }
                .toSet()
        val localById = localExercises.associateBy { it.exerciseId }
        val replacedImages = toUpdate.flatMap { remote ->
            localById[remote.exerciseId]?.imageUrls.orEmpty() - remote.imageUrls.toSet()
        }
        fileRepository.deleteImage(
            (toDelete.flatMap { it.imageUrls } + replacedImages)
                .filter { it !in referencedImages }
        )

        fileRepository.enqueueImages((toInsert + toUpdate).flatMap { it.imageUrls })
    }

    suspend fun enqueueMissingImages() {
        val localExercises = exerciseDao.getAllOnce(ownerProvider.currentOwner())
        fileRepository.enqueueImages(
            localExercises.filter { !it.isCustom }.flatMap { it.imageUrls }
        )
    }

    suspend fun deleteCustomExercise(id: Long) {
        val exercise = exerciseDao.getByIdOnce(ownerProvider.currentOwner(), id)
        if (exercise?.isCustom == true && exercise.ownerFirebaseUid == ownerProvider.currentOwner()) {
            exerciseDao.delete(exercise)
        }
    }
}