package com.organizer.data.repo

import com.organizer.data.local.dao.ExerciseDao
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.remote.repo.ExerciseRemoteDataSource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ExerciseRepository @Inject constructor(
    private val exerciseDao: ExerciseDao,
    private val remoteDataSource: ExerciseRemoteDataSource,
    private val fileRepository: FileRepository,
) {
    suspend fun addCustomExercise(name: String, instructions: List<String>, imageName: String) {
        exerciseDao.insert(
            ExerciseEntity(
                name = name,
                instructions = instructions,
                imageUrls = listOf(imageName),
                isCustom = true
            )
        )
    }

    fun observeAllExercises(): Flow<List<ExerciseEntity>> {
        return exerciseDao.getAll()
    }

    fun observeCustomExercises(): Flow<List<ExerciseEntity>> {
        return exerciseDao.getCustomExercises()
    }

    fun observeExercisesByCategory(categoryId: Long): Flow<List<ExerciseEntity>> {
        return exerciseDao.observeExercisesByCategory(categoryId)
    }

    fun observeExercisesByWorkout(workoutId: Long) =
        exerciseDao.observeExercisesByWorkout(workoutId)

    fun observeExercise(id: Long): Flow<ExerciseEntity?> {
        return exerciseDao.getById(id)
    }

    suspend fun refreshExercises() {
        val remoteExercises = remoteDataSource.getAll()
        val remoteIds = remoteExercises.map { it.exerciseId }

        val localExercises = exerciseDao.getAllOnce()
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

        // Database first: the UI can observe new data immediately, without
        // waiting for any image download.
        toDelete.forEach { exerciseDao.delete(it) }
        exerciseDao.insertAll(toInsert)
        exerciseDao.updateAll(toUpdate)

        // Delete only image files no remaining record still references,
        // including images replaced on updated records.
        // Custom exercises always survive sync, so their images stay referenced.
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

        // Changed images download in the background via the media queue.
        // Custom images are local files and are never queued.
        fileRepository.enqueueImages((toInsert + toUpdate).flatMap { it.imageUrls })
    }

    /**
     * Queue every missing image referenced by local non-custom exercises.
     * Called after data sync; returns as soon as everything is queued.
     */
    suspend fun enqueueMissingImages() {
        val localExercises = exerciseDao.getAllOnce()
        fileRepository.enqueueImages(
            localExercises.filter { !it.isCustom }.flatMap { it.imageUrls }
        )
    }

    suspend fun deleteCustomExercise(id: Long) {
        val exercise = exerciseDao.getByIdOnce(id)
        if (exercise?.isCustom == true) {
            exerciseDao.delete(exercise)
        }
    }
}
