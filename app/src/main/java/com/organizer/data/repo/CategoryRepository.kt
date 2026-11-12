package com.organizer.data.repo

import com.organizer.data.local.dao.CategoryDao
import com.organizer.data.local.db.entities.CategoryEntity
import com.organizer.data.remote.repo.CategoryRemoteDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao,
    private val remoteDataSource: CategoryRemoteDataSource,
    private val fileRepository: FileRepository,
) {
    fun observeSports(): Flow<List<CategoryEntity>> {
        return categoryDao.getSports()
    }

    fun observeCategories(): Flow<List<CategoryEntity>> {
        return categoryDao.getCategories()
    }

    fun observeSubcategories(categoryId: Long): Flow<List<CategoryEntity>> {
        return categoryDao.getSubcategories(categoryId)
    }

    suspend fun refreshSports() {
        val remoteSports = remoteDataSource.getSports()
        val localSports = categoryDao.getSportsOnce()
        val otherIcons = categoryDao.getCategoriesOnce().mapNotNull { it.iconUrl }.toSet()
        syncCategories(remoteSports, localSports, otherIcons)
    }

    suspend fun refreshCategories() {
        val remoteCategories = remoteDataSource.getCategories()
        val localCategories = categoryDao.getCategoriesOnce()
        val otherIcons = categoryDao.getSportsOnce().mapNotNull { it.iconUrl }.toSet()
        syncCategories(remoteCategories, localCategories, otherIcons)
    }

    private suspend fun syncCategories(
        remoteCategories: List<CategoryEntity>,
        localCategories: List<CategoryEntity>,
        otherReferencedIcons: Set<String>,
    ) {
        val remoteIds = remoteCategories.map { it.categoryId }
        val localIds = localCategories.map { it.categoryId }

        val toInsert = remoteCategories.filter { it.categoryId !in localIds }

        val toUpdate = remoteCategories.filter { remote ->
            localCategories.any { local ->
                local.categoryId == remote.categoryId && local != remote
            }
        }

        val toDelete = localCategories.filter { local ->
            local.categoryId !in remoteIds
        }

        // Database first: the UI can observe new data immediately, without
        // waiting for any icon download.
        toDelete.forEach { categoryDao.delete(it) }
        categoryDao.insertAll(toInsert)
        categoryDao.updateAll(toUpdate)

        // Delete only icons no other record still references, including icons
        // replaced on updated records.
        val referencedIcons = remoteCategories.mapNotNull { it.iconUrl }.toSet() + otherReferencedIcons
        val replacedIcons = toUpdate.mapNotNull { remote ->
            localCategories.find { it.categoryId == remote.categoryId }
                ?.iconUrl?.takeIf { it != remote.iconUrl }
        }
        (toDelete.mapNotNull { it.iconUrl } + replacedIcons)
            .filter { it !in referencedIcons }
            .forEach { fileRepository.deleteIcon(it) }

        // Changed icons download in the background via the media queue.
        fileRepository.enqueueIcons((toInsert + toUpdate).map { it.iconUrl })
    }

    /**
     * Queue every missing icon referenced by local data. Called after data
     * sync; returns as soon as everything is queued.
     */
    suspend fun enqueueMissingIcons() {
        val sports = categoryDao.getSportsOnce()
        val categories = categoryDao.getCategoriesOnce()
        fileRepository.enqueueIcons((sports + categories).map { it.iconUrl })
    }

    suspend fun observeCategoryByIdOnce(id: Long): CategoryEntity? {
        return categoryDao.getByIdOnce(id)
    }

    fun getCategoryPath(categoryId: Long): Flow<List<CategoryEntity>> = flow {
        val path = mutableListOf<CategoryEntity>()

        var current = observeCategoryByIdOnce(categoryId)

        while (current != null) {
            path.add(current)
            current = current.parentCategoryId?.let {
                observeCategoryByIdOnce(it)
            }
        }

        emit(path.reversed())
    }
}
