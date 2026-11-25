package com.organizer.fakes

import com.organizer.data.local.dao.CategoryDao
import com.organizer.data.local.dao.ExerciseCategoryDao
import com.organizer.data.local.dao.ExerciseDao
import com.organizer.data.local.dao.FavoriteExerciseDao
import com.organizer.data.local.dao.SyncCursorDao
import com.organizer.data.local.dao.SyncOutboxDao
import com.organizer.data.local.dao.SyncStagedDao
import com.organizer.data.local.dao.WorkoutDao
import com.organizer.data.local.db.entities.CategoryEntity
import com.organizer.data.local.db.entities.ExerciseCategoryEntity
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.local.db.entities.FavoriteExerciseEntity
import com.organizer.data.local.db.entities.SyncCursorEntity
import com.organizer.data.local.db.entities.SyncOutboxEntity
import com.organizer.data.local.db.entities.SyncStagedRecordEntity
import com.organizer.data.local.db.entities.WorkoutEntity
import com.organizer.data.local.db.entities.WorkoutWithExerciseCount
import com.organizer.data.local.storage.FileStore
import com.organizer.data.remote.ApiService
import com.organizer.sync.SyncOutboxStatus
import com.organizer.sync.SyncOutboxWriter
import com.organizer.sync.SyncPayloadFactory
import com.organizer.sync.SyncTransactionRunner
import kotlinx.serialization.json.Json
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

    override fun saveUriToStorage(
        subfolder: String,
        uri: android.net.Uri,
        crop: com.organizer.data.local.storage.NormalizedCrop?,
    ): String? =
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

    override suspend fun getAllOnce(owner: String): List<ExerciseEntity> = activeFor(owner)

    override fun getAll(owner: String): Flow<List<ExerciseEntity>> = data.map { activeFor(owner) }

    override fun getCustomExercises(owner: String): Flow<List<ExerciseEntity>> =
        data.map { it.filter { e -> e.isCustom && e.ownerFirebaseUid == owner && e.deletedAt == null } }

    override fun observeExercisesByCategory(categoryId: Long, owner: String): Flow<List<ExerciseEntity>> =
        throw UnsupportedOperationException("join tested via ExerciseCategoryRepositoryTest")

    override fun observeExercisesByWorkout(workoutId: Long, owner: String): Flow<List<ExerciseEntity>> =
        throw UnsupportedOperationException("not used in tests")

    override fun getById(owner: String, id: Long): Flow<ExerciseEntity?> =
        data.map { it.find { e -> e.exerciseId == id } }

    override suspend fun getByIdOnce(owner: String, id: Long): ExerciseEntity? =
        data.value.find { it.exerciseId == id }

    override suspend fun findCustomBySyncId(owner: String, syncId: String): ExerciseEntity? =
        data.value.firstOrNull { it.syncId == syncId && it.ownerFirebaseUid == owner && it.isCustom }

    override suspend fun applyRemoteUpsert(
        owner: String,
        syncId: String,
        name: String,
        instructions: List<String>,
        imageUrls: List<String>,
        exerciseType: String,
        serverVersion: Long,
        updatedAt: Long,
    ) {
        data.update { current ->
            current.map {
                if (it.syncId == syncId && it.ownerFirebaseUid == owner && it.isCustom) {
                    it.copy(
                        name = name, instructions = instructions, imageUrls = imageUrls,
                        exerciseType = exerciseType, serverVersion = serverVersion,
                        updatedAt = updatedAt, deletedAt = null, syncStatus = "SYNCED",
                    )
                } else it
            }
        }
    }

    override suspend fun applyRemoteDelete(owner: String, syncId: String, deletedAt: Long, serverVersion: Long, updatedAt: Long) {
        data.update { current ->
            current.map {
                if (it.syncId == syncId && it.ownerFirebaseUid == owner && it.isCustom) {
                    it.copy(deletedAt = deletedAt, serverVersion = serverVersion, updatedAt = updatedAt, syncStatus = "SYNCED")
                } else it
            }
        }
    }

    override suspend fun applyAcknowledgementMetadata(
        owner: String,
        syncId: String,
        serverVersion: Long,
        serverUpdatedAt: Long,
        syncStatus: String,
    ) {
        data.update { current ->
            current.map {
                if (it.syncId == syncId && it.ownerFirebaseUid == owner && it.isCustom &&
                    it.serverVersion < serverVersion
                ) {
                    it.copy(
                        serverVersion = serverVersion,
                        updatedAt = maxOf(it.updatedAt, serverUpdatedAt),
                        syncStatus = syncStatus,
                    )
                } else it
            }
        }
    }

    override suspend fun softDeleteCustom(owner: String, id: Long, deletedAt: Long, updatedAt: Long): Int {
        var changed = 0
        data.update { current ->
            current.map { exercise ->
                if (exercise.exerciseId == id &&
                    exercise.isCustom &&
                    exercise.ownerFirebaseUid == owner &&
                    exercise.deletedAt == null
                ) {
                    changed++
                    exercise.copy(deletedAt = deletedAt, updatedAt = updatedAt, syncStatus = "PENDING_DELETE")
                } else exercise
            }
        }
        return changed
    }

    override suspend fun delete(exercise: ExerciseEntity) {
        data.update { it.filter { e -> e.exerciseId != exercise.exerciseId } }
    }

    private fun activeFor(owner: String): List<ExerciseEntity> = data.value.filter {
        it.ownerFirebaseUid == "public" || (it.ownerFirebaseUid == owner && it.deletedAt == null)
    }
}

class FakeExerciseCategoryDao : ExerciseCategoryDao {
    val data = MutableStateFlow<List<ExerciseCategoryEntity>>(emptyList())

    fun seed(vararg items: ExerciseCategoryEntity) {
        data.value = items.toList()
    }

    override suspend fun getAllOnce(): List<ExerciseCategoryEntity> = data.value

    override fun getAll(): Flow<List<ExerciseCategoryEntity>> = data

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

    override fun observeCategoriesForExercise(exerciseId: Long): Flow<List<CategoryEntity>> =
        throw UnsupportedOperationException("join tested via the real database")

    override suspend fun delete(exerciseCategory: ExerciseCategoryEntity) {
        data.update { it.filter { e -> e.exerciseCategoryId != exerciseCategory.exerciseCategoryId } }
    }
}

class FakeFavoriteExerciseDao : FavoriteExerciseDao {
    val favorites = MutableStateFlow<List<FavoriteExerciseEntity>>(emptyList())

    override fun observeFavoriteIds(owner: String): Flow<List<Long>> =
        favorites.map { list -> list.filter { it.ownerFirebaseUid == owner && it.deletedAt == null }.map { it.exerciseId } }

    override fun observeFavoriteExercises(owner: String): Flow<List<ExerciseEntity>> =
        throw UnsupportedOperationException("join tested via the real database")

    override fun observeIsFavorite(owner: String, exerciseId: Long): Flow<Boolean> =
        favorites.map { list -> list.any { it.ownerFirebaseUid == owner && it.exerciseId == exerciseId && it.deletedAt == null } }

    override suspend fun isFavorite(owner: String, exerciseId: Long): Boolean =
        favorites.value.any { it.ownerFirebaseUid == owner && it.exerciseId == exerciseId && it.deletedAt == null }

    override suspend fun insert(favorite: FavoriteExerciseEntity) {
        favorites.update { current ->
            current.filterNot { it.exerciseId == favorite.exerciseId && it.ownerFirebaseUid == favorite.ownerFirebaseUid } + favorite
        }
    }

    override suspend fun softDelete(owner: String, exerciseId: Long, deletedAt: Long) {
        favorites.update { current ->
            current.map {
                if (it.ownerFirebaseUid == owner && it.exerciseId == exerciseId) {
                    it.copy(deletedAt = deletedAt, syncStatus = "PENDING_DELETE")
                } else it
            }
        }
    }
}

class FakeSyncOutboxDao : SyncOutboxDao {
    val entries = MutableStateFlow<List<SyncOutboxEntity>>(emptyList())

    override suspend fun insert(entry: SyncOutboxEntity) {
        entries.update { it + entry }
    }

    override suspend fun getPending(owner: String): List<SyncOutboxEntity> =
        entries.value.filter { it.ownerFirebaseUid == owner }

    override suspend fun findByStatus(owner: String, status: String): List<SyncOutboxEntity> =
        entries.value.filter { it.ownerFirebaseUid == owner && it.syncStatus == status }
            .sortedWith(compareBy<SyncOutboxEntity> { it.createdAt }.thenBy { it.outboxId })

    override suspend fun recoverInFlight(owner: String): Int {
        var changed = 0
        entries.update { current ->
            current.map {
                if (it.ownerFirebaseUid == owner && it.syncStatus == SyncOutboxStatus.IN_FLIGHT) {
                    changed++
                    it.copy(syncStatus = SyncOutboxStatus.PENDING_UPLOAD, lastError = null)
                } else it
            }
        }
        return changed
    }

    override suspend fun markInFlight(owner: String, mutationIds: Set<String>): Int {
        var changed = 0
        entries.update { current ->
            current.map {
                if (it.ownerFirebaseUid == owner && it.mutationId in mutationIds &&
                    it.syncStatus == SyncOutboxStatus.PENDING_UPLOAD
                ) {
                    changed++
                    it.copy(syncStatus = SyncOutboxStatus.IN_FLIGHT, lastError = null, everTransmitted = true)
                } else it
            }
        }
        return changed
    }

    override suspend fun markSuperseded(owner: String, mutationIds: Set<String>): Int {
        var changed = 0
        entries.update { current ->
            current.map {
                if (it.ownerFirebaseUid == owner && it.mutationId in mutationIds &&
                    it.syncStatus == SyncOutboxStatus.PENDING_UPLOAD
                ) {
                    changed++
                    it.copy(syncStatus = SyncOutboxStatus.SUPERSEDED, lastError = null)
                } else it
            }
        }
        return changed
    }

    override suspend fun resetToPending(
        owner: String,
        mutationIds: Set<String>,
        error: String?,
        allowedStatuses: Set<String>,
    ): Int {
        var changed = 0
        entries.update { current ->
            current.map {
                if (it.ownerFirebaseUid == owner && it.mutationId in mutationIds && it.syncStatus in allowedStatuses) {
                    changed++
                    it.copy(syncStatus = SyncOutboxStatus.PENDING_UPLOAD, lastError = error)
                } else it
            }
        }
        return changed
    }

    override suspend fun markFailedBatch(
        owner: String,
        mutationIds: Set<String>,
        error: String,
        allowedStatuses: Set<String>,
    ): Int {
        var changed = 0
        entries.update { current ->
            current.map {
                if (it.ownerFirebaseUid == owner && it.mutationId in mutationIds && it.syncStatus in allowedStatuses) {
                    changed++
                    it.copy(syncStatus = SyncOutboxStatus.FAILED, lastError = error)
                } else it
            }
        }
        return changed
    }

    override suspend fun markTerminalBatch(
        owner: String,
        mutationIds: Set<String>,
        status: String,
        errorCode: String?,
        error: String?,
        now: Long,
        allowedStatuses: Set<String>,
    ): Int {
        var changed = 0
        entries.update { current ->
            current.map {
                if (it.ownerFirebaseUid == owner && it.mutationId in mutationIds && it.syncStatus in allowedStatuses) {
                    changed++
                    it.copy(
                        syncStatus = status,
                        terminalErrorCode = errorCode,
                        lastError = error,
                        acknowledgedAt = now,
                    )
                } else it
            }
        }
        return changed
    }

    override fun observe(owner: String): Flow<List<SyncOutboxEntity>> =
        entries.map { list -> list.filter { it.ownerFirebaseUid == owner } }

    override suspend fun findByMutationId(owner: String, mutationId: String): SyncOutboxEntity? =
        entries.value.firstOrNull { it.ownerFirebaseUid == owner && it.mutationId == mutationId }

    override suspend fun findOpenMutation(
        owner: String,
        entityType: String,
        recordSyncId: String,
        operation: String,
    ): SyncOutboxEntity? = entries.value.firstOrNull {
        it.ownerFirebaseUid == owner &&
            it.entityType == entityType &&
            it.recordSyncId == recordSyncId &&
            it.operation == operation &&
            it.syncStatus == "PENDING_UPLOAD" &&
            it.retryCount == 0 &&
            it.lastError == null &&
            !it.everTransmitted
    }

    override suspend fun updateOpenPayload(owner: String, mutationId: String, payload: String, createdAt: Long) {
        entries.update { current ->
            current.map {
                if (it.ownerFirebaseUid == owner && it.mutationId == mutationId &&
                    it.syncStatus == "PENDING_UPLOAD" && it.retryCount == 0 && it.lastError == null &&
                        !it.everTransmitted
                ) {
                    it.copy(payload = payload, createdAt = createdAt)
                } else it
            }
        }
    }

    override suspend fun updateRetry(owner: String, mutationId: String, retryCount: Int, error: String?, status: String) {
        entries.update { current ->
            current.map {
                if (it.ownerFirebaseUid == owner && it.mutationId == mutationId) {
                    it.copy(retryCount = retryCount, lastError = error, syncStatus = status)
                } else it
            }
        }
    }

    override suspend fun findByRecord(owner: String, entityType: String, syncId: String): List<SyncOutboxEntity> =
        entries.value.filter {
            it.ownerFirebaseUid == owner && it.entityType == entityType && it.recordSyncId == syncId
        }

    override suspend fun markTerminal(
        owner: String,
        mutationId: String,
        status: String,
        serverVersion: Long?,
        serverUpdatedAt: Long?,
        acknowledgedAt: Long,
        terminalErrorCode: String?,
        allowedStatuses: Set<String>,
    ): Int {
        var updated = 0
        entries.update { current ->
            current.map {
                if (it.ownerFirebaseUid == owner && it.mutationId == mutationId && it.syncStatus in allowedStatuses) {
                    updated++
                    it.copy(
                        syncStatus = status,
                        serverVersion = serverVersion,
                        serverUpdatedAt = serverUpdatedAt,
                        acknowledgedAt = acknowledgedAt,
                        terminalErrorCode = terminalErrorCode,
                    )
                } else it
            }
        }
        return updated
    }

    override suspend fun delete(mutationId: String, owner: String) {
        entries.update { current ->
            current.filterNot { it.ownerFirebaseUid == owner && it.mutationId == mutationId }
        }
    }

    override suspend fun hasBlockingMutationForRecord(
        owner: String,
        entityType: String,
        syncId: String,
        blockingStatuses: Set<String>,
    ): Boolean = entries.value.any {
        it.ownerFirebaseUid == owner && it.entityType == entityType &&
            it.recordSyncId == syncId && it.syncStatus in blockingStatuses
    }
}

class ImmediateSyncTransactionRunner : SyncTransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = block()
}

fun fakeOutboxWriter(outboxDao: FakeSyncOutboxDao = FakeSyncOutboxDao()): Pair<SyncOutboxWriter, FakeSyncOutboxDao> =
    SyncOutboxWriter(outboxDao, SyncPayloadFactory(Json { ignoreUnknownKeys = true })) to outboxDao

class FakeWorkoutDao : WorkoutDao {
    val data = MutableStateFlow<List<WorkoutEntity>>(emptyList())

    fun seed(vararg workouts: WorkoutEntity) {
        data.value = workouts.toList()
    }

    override suspend fun insert(workoutEntity: WorkoutEntity): Long {
        val id = if (workoutEntity.workoutId == 0L) {
            (data.value.maxOfOrNull { it.workoutId } ?: 0L) + 1L
        } else workoutEntity.workoutId
        val normalized = workoutEntity.normalizedName
            ?: com.organizer.data.repo.WorkoutNameNormalizer.normalize(workoutEntity.name)
        data.update { it + workoutEntity.copy(workoutId = id, normalizedName = normalized) }
        return id
    }

    override suspend fun nameExists(normalizedName: String, owner: String): Boolean =
        data.value.any {
            (it.normalizedName ?: com.organizer.data.repo.WorkoutNameNormalizer.normalize(it.name)) == normalizedName &&
                it.ownerFirebaseUid == owner && it.deletedAt == null
        }

    override fun getAll(owner: String): Flow<List<WorkoutEntity>> =
        data.map { list -> list.filter { it.ownerFirebaseUid == owner && it.deletedAt == null } }

    override fun getWorkoutsWithExerciseCount(owner: String): Flow<List<WorkoutWithExerciseCount>> =
        data.map { list ->
            list.filter { it.ownerFirebaseUid == owner && it.deletedAt == null }
                .map { WorkoutWithExerciseCount(it.workoutId, it.name, 0) }
        }

    override fun getById(owner: String, id: Long): Flow<WorkoutEntity?> =
        data.map { list -> list.firstOrNull { it.workoutId == id && it.ownerFirebaseUid == owner && it.deletedAt == null } }

    override suspend fun getByIdOnce(owner: String, id: Long): WorkoutEntity? =
        data.value.firstOrNull { it.workoutId == id && it.ownerFirebaseUid == owner && it.deletedAt == null }

    override suspend fun findBySyncId(owner: String, syncId: String): WorkoutEntity? =
        data.value.firstOrNull { it.syncId == syncId && it.ownerFirebaseUid == owner }

    override suspend fun applyRemoteUpsert(
        owner: String,
        syncId: String,
        name: String,
        notes: String,
        normalizedName: String,
        serverVersion: Long,
        updatedAt: Long,
    ) {
        data.update { current ->
            current.map {
                if (it.syncId == syncId && it.ownerFirebaseUid == owner) {
                    it.copy(
                        name = name, notes = notes, normalizedName = normalizedName,
                        serverVersion = serverVersion, updatedAt = updatedAt,
                        deletedAt = null, syncStatus = "SYNCED",
                    )
                } else it
            }
        }
    }

    override suspend fun applyRemoteDelete(owner: String, syncId: String, deletedAt: Long, serverVersion: Long, updatedAt: Long) {
        data.update { current ->
            current.map {
                if (it.syncId == syncId && it.ownerFirebaseUid == owner) {
                    it.copy(
                        deletedAt = deletedAt, normalizedName = null, serverVersion = serverVersion,
                        updatedAt = updatedAt, syncStatus = "SYNCED",
                    )
                } else it
            }
        }
    }

    override suspend fun applyAcknowledgementMetadata(
        owner: String,
        syncId: String,
        serverVersion: Long,
        serverUpdatedAt: Long,
        syncStatus: String,
    ) {
        data.update { current ->
            current.map {
                if (it.syncId == syncId && it.ownerFirebaseUid == owner && it.serverVersion < serverVersion) {
                    it.copy(
                        serverVersion = serverVersion,
                        updatedAt = maxOf(it.updatedAt, serverUpdatedAt),
                        syncStatus = syncStatus,
                    )
                } else it
            }
        }
    }

    override suspend fun softDelete(owner: String, id: Long, deletedAt: Long) {
        data.update { current ->
            current.map {
                if (it.workoutId == id && it.ownerFirebaseUid == owner) {
                    it.copy(deletedAt = deletedAt, normalizedName = null, syncStatus = "PENDING_DELETE")
                } else it
            }
        }
    }

    override suspend fun updateNotes(owner: String, id: Long, notes: String, updatedAt: Long) {
        data.update { current ->
            current.map {
                if (it.workoutId == id && it.ownerFirebaseUid == owner && it.deletedAt == null) {
                    it.copy(notes = notes, updatedAt = updatedAt, syncStatus = "PENDING_UPLOAD")
                } else it
            }
        }
    }
}

class FakeSyncStagedDao : SyncStagedDao {
    val records = MutableStateFlow<List<SyncStagedRecordEntity>>(emptyList())

    override suspend fun upsertIfNewer(
        owner: String,
        entityType: String,
        syncId: String,
        serverVersion: Long,
        serverUpdatedAt: Long,
        deleted: Boolean,
        payload: String?,
        processingStatus: String,
        createdAt: Long,
    ) {
        records.update { current ->
            val index = current.indexOfFirst {
                it.ownerFirebaseUid == owner && it.entityType == entityType && it.syncId == syncId
            }
            if (index >= 0 && current[index].serverVersion >= serverVersion) {
                current
            } else {
                val newRecord = SyncStagedRecordEntity(
                    stagedId = if (index >= 0) current[index].stagedId else (current.maxOfOrNull { it.stagedId } ?: 0L) + 1L,
                    ownerFirebaseUid = owner,
                    entityType = entityType,
                    syncId = syncId,
                    serverVersion = serverVersion,
                    serverUpdatedAt = serverUpdatedAt,
                    deleted = deleted,
                    payload = payload,
                    processingStatus = processingStatus,
                    createdAt = createdAt,
                )
                if (index >= 0) current.toMutableList().also { it[index] = newRecord }
                else current + newRecord
            }
        }
    }

    override suspend fun getAll(owner: String): List<SyncStagedRecordEntity> =
        records.value.filter { it.ownerFirebaseUid == owner }

    override suspend fun getByStatus(owner: String, status: String): List<SyncStagedRecordEntity> =
        getAll(owner).filter { it.processingStatus == status }

    override suspend fun find(owner: String, entityType: String, syncId: String): SyncStagedRecordEntity? =
        records.value.firstOrNull {
            it.ownerFirebaseUid == owner && it.entityType == entityType && it.syncId == syncId
        }

    override suspend fun updateStatus(owner: String, stagedId: Long, status: String, retryCount: Int, error: String?, attemptAt: Long) {
        records.update { current ->
            current.map {
                if (it.ownerFirebaseUid == owner && it.stagedId == stagedId) {
                    it.copy(processingStatus = status, retryCount = retryCount, lastError = error, lastAttemptAt = attemptAt)
                } else it
            }
        }
    }
}

class FakeSyncCursorDao : SyncCursorDao {
    val cursors = MutableStateFlow<List<SyncCursorEntity>>(emptyList())

    override suspend fun upsert(cursor: SyncCursorEntity) {
        cursors.update { current ->
            current.filterNot { it.ownerFirebaseUid == cursor.ownerFirebaseUid } + cursor
        }
    }

    override suspend fun get(owner: String): SyncCursorEntity? =
        cursors.value.firstOrNull { it.ownerFirebaseUid == owner }

    override fun observe(owner: String): Flow<SyncCursorEntity?> =
        cursors.map { list -> list.firstOrNull { it.ownerFirebaseUid == owner } }
}
