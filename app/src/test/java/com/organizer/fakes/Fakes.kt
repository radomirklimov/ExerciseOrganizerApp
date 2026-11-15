package com.organizer.fakes

import com.organizer.data.local.dao.CategoryDao
import com.organizer.data.local.dao.ExerciseCategoryDao
import com.organizer.data.local.dao.ExerciseDao
import com.organizer.data.local.dao.FavoriteExerciseDao
import com.organizer.data.local.db.entities.CategoryEntity
import com.organizer.data.local.db.entities.ExerciseCategoryEntity
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.local.db.entities.FavoriteExerciseEntity
import com.organizer.data.local.storage.FileStore
import com.organizer.data.remote.ApiService
import com.organizer.data.remote.model.Category
import com.organizer.data.remote.model.Exercise
import com.organizer.data.remote.model.ExerciseCategory
import com.organizer.data.remote.model.WorkoutTemplate
import com.organizer.data.remote.repo.FileRemoteDataSource
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import okhttp3.ResponseBody
import retrofit2.Response

/** In-memory [ApiService]. Pagination slices [exercises]; records page calls. */
class FakeApiService(
    var sports: List<Category> = emptyList(),
    var categories: List<Category> = emptyList(),
    var exercises: List<Exercise> = emptyList(),
    var links: List<ExerciseCategory> = emptyList(),
    var templates: List<WorkoutTemplate> = emptyList(),
) : ApiService {
    val pageCalls = mutableListOf<Pair<Int, Int>>()

    override suspend fun getSports(): List<Category> = sports

    override suspend fun getCategories(): List<Category> = categories

    override suspend fun getExercises(page: Int, size: Int): List<Exercise> {
        pageCalls.add(page to size)
        val from = page * size
        if (from >= exercises.size) return emptyList()
        return exercises.subList(from, minOf(from + size, exercises.size))
    }

    override suspend fun getExerciseCategory(): List<ExerciseCategory> = links

    override suspend fun getStarterWorkouts(): List<WorkoutTemplate> = templates

    override suspend fun downloadFile(url: String): Response<ResponseBody> =
        throw UnsupportedOperationException("FakeApiService.downloadFile is never called in tests")
}

/**
 * In-memory [FileRemoteDataSource]. Programmable per-file bytes, failures and
 * gates; records attempts and completion order. Never touches the network.
 */
open class FakeFileRemote : FileRemoteDataSource(FakeApiService()) {
    /** Key "folder/fileName" -> bytes. Missing entry + not failed = null (HTTP error). */
    val files = mutableMapOf<String, ByteArray>()

    /** Keys that fail (simulated HTTP/transport failure -> null). */
    val failures = mutableSetOf<String>()

    /** Optional per-key gates; downloads suspend until completed. */
    val gates = mutableMapOf<String, CompletableDeferred<Unit>>()

    val attempts = mutableListOf<String>()
    val downloadOrder = mutableListOf<String>()

    override suspend fun downloadFile(subfolder: String, fileName: String): ByteArray? {
        val key = "$subfolder/$fileName"
        attempts.add(key)
        gates[key]?.await()
        if (key in failures) return null
        val bytes = files[key]
        if (bytes != null) downloadOrder.add(key)
        return bytes
    }
}

/** In-memory [FileStore]. No Android Context, no real filesystem writes. */
class FakeFileStore : FileStore {
    val files = mutableMapOf<Pair<String, String>, ByteArray>()

    fun preload(subfolder: String, fileName: String, bytes: ByteArray = byteArrayOf(1)) {
        files[subfolder to fileName] = bytes
    }

    override fun dir(subfolder: String): File = File(subfolder)

    override fun exists(subfolder: String, fileName: String): Boolean =
        files.containsKey(subfolder to fileName)

    override suspend fun saveBytes(subfolder: String, fileName: String, bytes: ByteArray) {
        files[subfolder to fileName] = bytes
    }

    override fun deleteLocalFile(subfolder: String, fileName: String) {
        files.remove(subfolder to fileName)
    }

    override fun saveUriToStorage(subfolder: String, uri: android.net.Uri): String? =
        throw UnsupportedOperationException("not used in tests")
}

class FakeCategoryDao : CategoryDao {
    val data = MutableStateFlow<List<CategoryEntity>>(emptyList())

    fun seed(vararg items: CategoryEntity) {
        data.value = items.toList()
    }

    override suspend fun insertAll(categories: List<CategoryEntity>) {
        data.update { current ->
            val ids = categories.map { it.categoryId }.toSet()
            current.filter { it.categoryId !in ids } + categories
        }
    }

    override suspend fun updateAll(categories: List<CategoryEntity>) {
        data.update { current ->
            current.map { local -> categories.find { it.categoryId == local.categoryId } ?: local }
        }
    }

    override fun getSports(): Flow<List<CategoryEntity>> =
        data.map { it.filter { c -> c.parentCategoryId == null } }

    override suspend fun getSportsOnce(): List<CategoryEntity> =
        data.value.filter { it.parentCategoryId == null }

    override suspend fun getCategoriesOnce(): List<CategoryEntity> =
        data.value.filter { it.parentCategoryId != null }

    override fun getCategories(): Flow<List<CategoryEntity>> =
        data.map { it.filter { c -> c.parentCategoryId != null } }

    override fun getSubcategories(categoryId: Long): Flow<List<CategoryEntity>> =
        data.map { it.filter { c -> c.parentCategoryId == categoryId } }

    override suspend fun getByIdOnce(id: Long): CategoryEntity? =
        data.value.find { it.categoryId == id }

    override suspend fun delete(category: CategoryEntity) {
        data.update { it.filter { c -> c.categoryId != category.categoryId } }
    }
}

class FakeExerciseDao : ExerciseDao {
    val data = MutableStateFlow<List<ExerciseEntity>>(emptyList())

    fun seed(vararg items: ExerciseEntity) {
        data.value = items.toList()
    }

    override suspend fun insertAll(exercises: List<ExerciseEntity>) {
        data.update { current ->
            val ids = exercises.map { it.exerciseId }.toSet()
            current.filter { it.exerciseId !in ids } + exercises
        }
    }

    override suspend fun insert(exercise: ExerciseEntity) {
        data.update { it + exercise }
    }

    override suspend fun updateAll(exercises: List<ExerciseEntity>) {
        data.update { current ->
            current.map { local -> exercises.find { it.exerciseId == local.exerciseId } ?: local }
        }
    }

    override suspend fun getAllOnce(): List<ExerciseEntity> = data.value

    override fun getAll(): Flow<List<ExerciseEntity>> = data

    override fun getCustomExercises(): Flow<List<ExerciseEntity>> =
        data.map { it.filter { e -> e.isCustom } }

    override fun observeExercisesByCategory(categoryId: Long): Flow<List<ExerciseEntity>> =
        throw UnsupportedOperationException("join tested via ExerciseCategoryRepositoryTest")

    override fun observeExercisesByWorkout(workoutId: Long): Flow<List<ExerciseEntity>> =
        throw UnsupportedOperationException("not used in tests")

    override fun getById(id: Long): Flow<ExerciseEntity?> =
        data.map { it.find { e -> e.exerciseId == id } }

    override suspend fun getByIdOnce(id: Long): ExerciseEntity? =
        data.value.find { it.exerciseId == id }

    override suspend fun delete(exercise: ExerciseEntity) {
        data.update { it.filter { e -> e.exerciseId != exercise.exerciseId } }
    }
}

class FakeExerciseCategoryDao : ExerciseCategoryDao {
    val data = MutableStateFlow<List<ExerciseCategoryEntity>>(emptyList())

    fun seed(vararg items: ExerciseCategoryEntity) {
        data.value = items.toList()
    }

    override suspend fun getAllOnce(): List<ExerciseCategoryEntity> = data.value

    override suspend fun insertAll(exerciseCategories: List<ExerciseCategoryEntity>) {
        data.update { current ->
            val ids = exerciseCategories.map { it.exerciseCategoryId }.toSet()
            current.filter { it.exerciseCategoryId !in ids } + exerciseCategories
        }
    }

    override suspend fun updateAll(exerciseCategories: List<ExerciseCategoryEntity>) {
        data.update { current ->
            current.map { local ->
                exerciseCategories.find { it.exerciseCategoryId == local.exerciseCategoryId } ?: local
            }
        }
    }

    override suspend fun getExerciseIdsByCategoryId(categoryId: Long): List<Long> =
        data.value.filter { it.categoryId == categoryId }
            .sortedWith(compareBy<ExerciseCategoryEntity> { it.sortOrder }.thenBy { it.exerciseCategoryId })
            .map { it.exerciseId }

    override suspend fun delete(exerciseCategory: ExerciseCategoryEntity) {
        data.update { it.filter { e -> e.exerciseCategoryId != exerciseCategory.exerciseCategoryId } }
    }
}

class FakeFavoriteExerciseDao : FavoriteExerciseDao {
    val favorites = MutableStateFlow<List<FavoriteExerciseEntity>>(emptyList())

    override fun observeFavoriteIds(): Flow<List<Long>> =
        favorites.map { list -> list.map { it.exerciseId } }

    override fun observeFavoriteExercises(): Flow<List<ExerciseEntity>> =
        throw UnsupportedOperationException("join tested via the real database")

    override fun observeIsFavorite(exerciseId: Long): Flow<Boolean> =
        favorites.map { list -> list.any { it.exerciseId == exerciseId } }

    override suspend fun isFavorite(exerciseId: Long): Boolean =
        favorites.value.any { it.exerciseId == exerciseId }

    override suspend fun insert(favorite: FavoriteExerciseEntity) {
        favorites.update { current ->
            current.filterNot { it.exerciseId == favorite.exerciseId } + favorite
        }
    }

    override suspend fun deleteById(exerciseId: Long) {
        favorites.update { current -> current.filterNot { it.exerciseId == exerciseId } }
    }
}
