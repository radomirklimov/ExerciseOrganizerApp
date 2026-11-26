package com.organizer.sync

import com.organizer.data.local.dao.ExerciseDao
import com.organizer.data.local.dao.FavoriteExerciseDao
import com.organizer.data.local.dao.ScheduledWorkoutDao
import com.organizer.data.local.dao.SyncOutboxDao
import com.organizer.notification.ReminderScheduler
import com.organizer.data.local.dao.SyncStagedDao
import com.organizer.data.local.dao.WorkoutDao
import com.organizer.data.local.dao.WorkoutExerciseDao
import com.organizer.auth.OwnerProvider
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.local.db.entities.FavoriteExerciseEntity
import com.organizer.data.local.db.entities.ScheduledWorkoutEntity
import com.organizer.data.local.db.entities.SyncStagedRecordEntity
import com.organizer.data.local.db.entities.WorkoutExerciseEntity
import com.organizer.data.local.db.entities.SyncStagedStatus
import com.organizer.data.local.db.entities.WorkoutEntity
import com.organizer.data.repo.WorkoutNameNormalizer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncStagedApplier @Inject constructor(
    private val ownerProvider: OwnerProvider,
    private val workoutDao: WorkoutDao,
    private val exerciseDao: ExerciseDao,
    private val outboxDao: SyncOutboxDao,
    private val workoutExerciseDao: WorkoutExerciseDao,
    private val favoriteExerciseDao: FavoriteExerciseDao? = null,
    private val scheduledWorkoutDao: ScheduledWorkoutDao? = null,
    private val reminderScheduler: ReminderScheduler? = null,
    private val stagedDao: SyncStagedDao,
    private val transactions: SyncTransactionRunner,
    private val json: Json,
) {
    suspend fun process(owner: String, staged: SyncStagedRecordEntity): StagedApplyResult {
        if (staged.ownerFirebaseUid != owner) return StagedApplyResult.FAILED
        var currentRow: SyncStagedRecordEntity? = null
        return try {
            transactions.run {
                if (ownerProvider.currentOwner() != owner) throw OwnerChangedDuringStagedApplyException()
                // A newer delivery may have replaced this row (stagedId is
                // stable). Always apply the current version, never the snapshot
                // captured by the caller.
                val current = stagedDao.findByStagedId(owner, staged.stagedId)
                    ?: return@run StagedApplyResult.FAILED
                currentRow = current
                if (current.ownerFirebaseUid != owner) return@run StagedApplyResult.FAILED
                if (current.entityType !in APPLICABLE_TYPES) {
                    // Retain the staged payload. A future app version that adds
                    // an applier for this entity type must explicitly requeue
                    // these rows to PENDING; this version never does so.
                    return@run mark(current, SyncStagedStatus.UNSUPPORTED)
                }
                if (outboxDao.hasBlockingMutationForRecord(
                        owner, current.entityType, current.syncId, SyncOutboxStatus.BLOCKING
                    )
                ) {
                    return@run mark(current, SyncStagedStatus.LOCAL_PENDING, "Local mutation pending")
                }
                if (current.deleted) {
                    applyTombstone(owner, current)
                } else {
                    val payload = current.payload?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() } as? JsonObject
                        ?: return@run mark(current, SyncStagedStatus.FAILED, "Invalid payload")
                    val schemaVersion = payload["schemaVersion"]?.jsonPrimitive?.content?.toIntOrNull()
                    if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
                        return@run mark(current, SyncStagedStatus.UNSUPPORTED_SCHEMA, "Unsupported schemaVersion")
                    }
                    when (current.entityType) {
                        SyncEntityTypes.WORKOUT -> applyWorkout(owner, current, payload)
                        SyncEntityTypes.CUSTOM_EXERCISE -> applyCustomExercise(owner, current, payload)
                        SyncEntityTypes.WORKOUT_EXERCISE -> applyWorkoutExercise(owner, current, payload)
                        SyncEntityTypes.FAVORITE_EXERCISE -> applyFavoriteExercise(owner, current, payload)
                        SyncEntityTypes.SCHEDULED_WORKOUT -> applyScheduledWorkout(owner, current, payload)
                        else -> mark(current, SyncStagedStatus.UNSUPPORTED)
                    }
                }
            }
        } catch (ex: OwnerChangedDuringStagedApplyException) {
            StagedApplyResult.OWNER_CHANGED
        } catch (ex: Exception) {
            mark(currentRow ?: staged, SyncStagedStatus.FAILED, ex.javaClass.simpleName)
        }
    }

    private suspend fun applyTombstone(owner: String, staged: SyncStagedRecordEntity): StagedApplyResult {
        return when (staged.entityType) {
            SyncEntityTypes.WORKOUT -> {
                val local = workoutDao.findBySyncId(owner, staged.syncId)
                    ?: return mark(staged, SyncStagedStatus.TOMBSTONE_ONLY)
                if (local.serverVersion >= staged.serverVersion) {
                    mark(staged, SyncStagedStatus.APPLIED)
                } else {
                    workoutDao.applyRemoteDelete(owner, staged.syncId, staged.serverUpdatedAt, staged.serverVersion, staged.serverUpdatedAt)
                    mark(staged, SyncStagedStatus.APPLIED)
                }
            }
            SyncEntityTypes.CUSTOM_EXERCISE -> {
                val local = exerciseDao.findCustomBySyncId(owner, staged.syncId)
                    ?: return mark(staged, SyncStagedStatus.TOMBSTONE_ONLY)
                if (local.serverVersion >= staged.serverVersion) {
                    mark(staged, SyncStagedStatus.APPLIED)
                } else {
                    exerciseDao.applyRemoteDelete(owner, staged.syncId, staged.serverUpdatedAt, staged.serverVersion, staged.serverUpdatedAt)
                    mark(staged, SyncStagedStatus.APPLIED)
                }
            }
            SyncEntityTypes.WORKOUT_EXERCISE -> {
                // A stable syncId is enough to locate an existing row; parent
                // IDs are only needed when a tombstone row must be created.
                val bySyncId = workoutExerciseDao.findBySyncId(owner, staged.syncId)
                if (bySyncId != null) {
                    if (bySyncId.serverVersion >= staged.serverVersion) {
                        return mark(staged, SyncStagedStatus.APPLIED)
                    }
                    workoutExerciseDao.applyRemoteDeleteBySyncId(
                        owner, staged.syncId, staged.serverUpdatedAt, staged.serverVersion, staged.serverUpdatedAt,
                    )
                    return mark(staged, SyncStagedStatus.APPLIED)
                }

                val payload = staged.payload?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() } as? JsonObject
                val workoutId = payload?.let { resolveWorkout(owner, it) }
                    ?: return mark(
                        staged, SyncStagedStatus.WAITING_DEPENDENCY, "Tombstone workout reference unavailable"
                    )
                val exerciseId = payload.let { resolveExercise(owner, it) }
                    ?: return mark(
                        staged, SyncStagedStatus.WAITING_DEPENDENCY, "Tombstone exercise reference unavailable"
                    )
                val byPair = workoutExerciseDao.findByPair(owner, workoutId, exerciseId)
                if (byPair != null) {
                    return mark(staged, SyncStagedStatus.CONFLICT_BLOCKED, "Conflicting local association identity")
                }
                workoutExerciseDao.insertRemote(
                    WorkoutExerciseEntity(
                        workoutId = workoutId,
                        exerciseId = exerciseId,
                        orderIndex = payload.jsonPrimitiveInt("orderIndex") ?: 0,
                        ownerFirebaseUid = owner,
                        syncId = staged.syncId,
                        updatedAt = staged.serverUpdatedAt,
                        deletedAt = staged.serverUpdatedAt,
                        serverVersion = staged.serverVersion,
                        syncStatus = "SYNCED",
                    )
                )
                mark(staged, SyncStagedStatus.TOMBSTONE_ONLY)
            }
            SyncEntityTypes.FAVORITE_EXERCISE -> {
                val dao = favoriteExerciseDao ?: return mark(staged, SyncStagedStatus.UNSUPPORTED)
                val bySyncId = dao.findBySyncId(owner, staged.syncId)
                if (bySyncId != null) {
                    if (bySyncId.serverVersion >= staged.serverVersion) {
                        return mark(staged, SyncStagedStatus.APPLIED)
                    }
                    dao.applyRemoteDelete(owner, staged.syncId, staged.serverUpdatedAt, staged.serverVersion, staged.serverUpdatedAt)
                    return mark(staged, SyncStagedStatus.APPLIED)
                }
                val payload = staged.payload?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() } as? JsonObject
                val exerciseId = payload?.let { resolveFavoriteExercise(owner, it) }
                    ?: return mark(staged, SyncStagedStatus.WAITING_DEPENDENCY, "Favorite exercise reference unavailable")
                val byExercise = dao.findByOwnerAndExercise(owner, exerciseId)
                if (byExercise != null && byExercise.syncId != staged.syncId) {
                    return mark(staged, SyncStagedStatus.CONFLICT_BLOCKED, "Conflicting favorite identity")
                }
                if (byExercise == null) {
                    dao.insert(FavoriteExerciseEntity(
                        exerciseId = exerciseId,
                        ownerFirebaseUid = owner,
                        syncId = staged.syncId,
                        addedAt = staged.serverUpdatedAt,
                        updatedAt = staged.serverUpdatedAt,
                        deletedAt = staged.serverUpdatedAt,
                        syncStatus = "SYNCED",
                        serverVersion = staged.serverVersion,
                    ))
                } else {
                    dao.applyRemoteDelete(owner, staged.syncId, staged.serverUpdatedAt, staged.serverVersion, staged.serverUpdatedAt)
                }
                mark(staged, SyncStagedStatus.TOMBSTONE_ONLY)
            }
            SyncEntityTypes.SCHEDULED_WORKOUT -> {
                val dao = scheduledWorkoutDao ?: return mark(staged, SyncStagedStatus.UNSUPPORTED)
                val bySyncId = dao.findBySyncId(owner, staged.syncId)
                if (bySyncId != null) {
                    if (bySyncId.serverVersion >= staged.serverVersion) {
                        return mark(staged, SyncStagedStatus.APPLIED)
                    }
                    dao.applyRemoteDelete(owner, staged.syncId, staged.serverUpdatedAt, staged.serverVersion, staged.serverUpdatedAt)
                    rescheduleScheduleAlarm(owner, staged.syncId)
                    return mark(staged, SyncStagedStatus.APPLIED)
                }
                val payload = staged.payload?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() } as? JsonObject
                val workoutId = payload?.let { resolveWorkout(owner, it) }
                    ?: return mark(staged, SyncStagedStatus.WAITING_DEPENDENCY, "Schedule workout reference unavailable")
                dao.insert(ScheduledWorkoutEntity(
                    workoutId = workoutId,
                    dayOfWeek = payload.jsonPrimitiveInt("dayOfWeek") ?: 1,
                    scheduledDateKey = payload.jsonPrimitiveInt("scheduledDateKey"),
                    startMinutes = payload.jsonPrimitiveInt("startMinutes") ?: 0,
                    reminderEnabled = payload.get("reminderEnabled")?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true,
                    reminderMinutesBefore = payload.jsonPrimitiveInt("reminderMinutesBefore") ?: 30,
                    reminderNote = payload.string("reminderNote") ?: "",
                    ownerFirebaseUid = owner,
                    syncId = staged.syncId,
                    updatedAt = staged.serverUpdatedAt,
                    deletedAt = staged.serverUpdatedAt,
                    syncStatus = "SYNCED",
                    serverVersion = staged.serverVersion,
                ))
                mark(staged, SyncStagedStatus.TOMBSTONE_ONLY)
            }
            else -> mark(staged, SyncStagedStatus.UNSUPPORTED)
        }
    }

    private suspend fun applyWorkout(owner: String, staged: SyncStagedRecordEntity, payload: JsonObject): StagedApplyResult {
        val name = payload.string("name") ?: return mark(staged, SyncStagedStatus.FAILED, "Missing name")
        val notes = payload.string("notes") ?: ""
        val normalizedName = WorkoutNameNormalizer.normalize(name)
        val local = workoutDao.findBySyncId(owner, staged.syncId)
        val tombstone = stagedDao.find(owner, staged.entityType, staged.syncId)

        if (staged.deleted) {
            if (local == null) return mark(staged, SyncStagedStatus.TOMBSTONE_ONLY)
            if (local.serverVersion >= staged.serverVersion) return mark(staged, SyncStagedStatus.APPLIED)
            workoutDao.applyRemoteDelete(owner, staged.syncId, staged.serverUpdatedAt, staged.serverVersion, staged.serverUpdatedAt)
            // Cancel device-local reminders for schedules whose parent is now deleted.
            runCatching {
                scheduledWorkoutDao?.getForWorkout(owner, local.workoutId)?.forEach { entry ->
                    reminderScheduler?.cancel(entry.scheduleId)
                }
            }
            return mark(staged, SyncStagedStatus.APPLIED)
        }

        if (tombstone?.deleted == true && tombstone.serverVersion >= staged.serverVersion) {
            return mark(staged, SyncStagedStatus.APPLIED)
        }
        if (local != null && local.serverVersion >= staged.serverVersion) {
            return mark(staged, SyncStagedStatus.APPLIED)
        }

        if (local == null) {
            workoutDao.insert(
                WorkoutEntity(
                    name = name,
                    notes = notes,
                    ownerFirebaseUid = owner,
                    syncId = staged.syncId,
                    serverVersion = staged.serverVersion,
                    updatedAt = staged.serverUpdatedAt,
                    deletedAt = null,
                    syncStatus = "SYNCED",
                    normalizedName = normalizedName,
                )
            )
        } else {
            workoutDao.applyRemoteUpsert(
                owner, staged.syncId, name, notes, normalizedName,
                staged.serverVersion, staged.serverUpdatedAt,
            )
        }
        return mark(staged, SyncStagedStatus.APPLIED)
    }

    private suspend fun applyCustomExercise(owner: String, staged: SyncStagedRecordEntity, payload: JsonObject): StagedApplyResult {
        val name = payload.string("name") ?: return mark(staged, SyncStagedStatus.FAILED, "Missing name")
        val instructions = payload.stringList("instructions").orEmpty()
        val imageUrls = payload.stringList("imageUrls").orEmpty()
        val exerciseType = payload.string("exerciseType") ?: "REPS_WEIGHT"
        val isCustom = payload["isCustom"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true
        if (!isCustom) return mark(staged, SyncStagedStatus.FAILED, "Public exercise cannot be pulled as user data")

        val local = exerciseDao.findCustomBySyncId(owner, staged.syncId)
        val tombstone = stagedDao.find(owner, staged.entityType, staged.syncId)

        if (staged.deleted) {
            if (local == null) return mark(staged, SyncStagedStatus.TOMBSTONE_ONLY)
            if (local.serverVersion >= staged.serverVersion) return mark(staged, SyncStagedStatus.APPLIED)
            exerciseDao.applyRemoteDelete(owner, staged.syncId, staged.serverUpdatedAt, staged.serverVersion, staged.serverUpdatedAt)
            return mark(staged, SyncStagedStatus.APPLIED)
        }

        if (tombstone?.deleted == true && tombstone.serverVersion >= staged.serverVersion) {
            return mark(staged, SyncStagedStatus.APPLIED)
        }
        if (local != null && local.serverVersion >= staged.serverVersion) {
            return mark(staged, SyncStagedStatus.APPLIED)
        }

        if (local == null) {
            exerciseDao.insert(
                ExerciseEntity(
                    name = name,
                    instructions = instructions,
                    imageUrls = imageUrls,
                    isCustom = true,
                    exerciseType = exerciseType,
                    ownerFirebaseUid = owner,
                    syncId = staged.syncId,
                    serverVersion = staged.serverVersion,
                    updatedAt = staged.serverUpdatedAt,
                    syncStatus = "SYNCED",
                )
            )
        } else {
            exerciseDao.applyRemoteUpsert(
                owner, staged.syncId, name, instructions, imageUrls, exerciseType,
                staged.serverVersion, staged.serverUpdatedAt,
            )
        }
        return mark(staged, SyncStagedStatus.APPLIED)
    }

    private suspend fun resolveWorkout(owner: String, payload: JsonObject): Long? {
        val syncId = payload.string("workoutSyncId") ?: return null
        return workoutDao.findBySyncId(owner, syncId)?.workoutId
    }

    private suspend fun resolveExercise(owner: String, payload: JsonObject): Long? {
        val ref = payload["exerciseRef"] as? JsonObject ?: return null
        return when (ref.string("kind")) {
            "PUBLIC" -> {
                val catalogId = ref.jsonPrimitiveLong("exerciseId") ?: return null
                exerciseDao.findPublicByCatalogId(catalogId)?.exerciseId
            }
            "CUSTOM" -> {
                val syncId = ref.string("syncId") ?: return null
                exerciseDao.findCustomBySyncId(owner, syncId)?.exerciseId
            }
            else -> null
        }
    }

    private suspend fun resolveFavoriteExercise(owner: String, payload: JsonObject): Long? {
        val ref = payload["exerciseRef"] as? JsonObject ?: return null
        return when (ref.string("kind")) {
            "PUBLIC" -> {
                val catalogId = ref.jsonPrimitiveLong("exerciseId") ?: return null
                exerciseDao.findPublicByCatalogId(catalogId)?.exerciseId
            }
            "CUSTOM" -> {
                val syncId = ref.string("syncId") ?: return null
                exerciseDao.findCustomBySyncId(owner, syncId)?.exerciseId
            }
            else -> null
        }
    }

    private suspend fun applyWorkoutExercise(
        owner: String,
        staged: SyncStagedRecordEntity,
        payload: JsonObject,
    ): StagedApplyResult {
        val workoutId = resolveWorkout(owner, payload)
            ?: return mark(staged, SyncStagedStatus.WAITING_DEPENDENCY, "Workout not available")
        val exerciseId = resolveExercise(owner, payload)
            ?: return mark(staged, SyncStagedStatus.WAITING_DEPENDENCY, "Exercise not available")
        val orderIndex = payload.jsonPrimitiveInt("orderIndex")
            ?: return mark(staged, SyncStagedStatus.FAILED, "Missing orderIndex")

        // A newer staged tombstone for this identity must win over a late UPSERT.
        val tombstone = stagedDao.find(owner, SyncEntityTypes.WORKOUT_EXERCISE, staged.syncId)
        if (tombstone != null && tombstone.deleted && tombstone.serverVersion >= staged.serverVersion) {
            return mark(staged, SyncStagedStatus.APPLIED)
        }

        val local = workoutExerciseDao.findByPair(owner, workoutId, exerciseId)
        if (local != null) {
            if (local.syncId != staged.syncId) {
                return mark(staged, SyncStagedStatus.CONFLICT_BLOCKED, "Conflicting local association identity")
            }
            if (outboxDao.findByRecordSyncId(owner, local.syncId).any {
                    it.entityType == SyncEntityTypes.WORKOUT_EXERCISE &&
                        it.syncStatus in SyncOutboxStatus.BLOCKING
                }
            ) {
                return mark(staged, SyncStagedStatus.LOCAL_PENDING, "Local mutation pending")
            }
            if (local.serverVersion >= staged.serverVersion) {
                return mark(staged, SyncStagedStatus.APPLIED)
            }
            workoutExerciseDao.applyRemoteUpsert(
                owner, workoutId, exerciseId, orderIndex, staged.serverVersion, staged.serverUpdatedAt,
            )
            return mark(staged, SyncStagedStatus.APPLIED)
        }

        workoutExerciseDao.insertRemote(
            WorkoutExerciseEntity(
                workoutId = workoutId,
                exerciseId = exerciseId,
                orderIndex = orderIndex,
                ownerFirebaseUid = owner,
                syncId = staged.syncId,
                updatedAt = staged.serverUpdatedAt,
                serverVersion = staged.serverVersion,
                syncStatus = "SYNCED",
            )
        )
        return mark(staged, SyncStagedStatus.APPLIED)
    }

    private suspend fun applyFavoriteExercise(
        owner: String,
        staged: SyncStagedRecordEntity,
        payload: JsonObject,
    ): StagedApplyResult {
        val dao = favoriteExerciseDao ?: return mark(staged, SyncStagedStatus.UNSUPPORTED)
        val exerciseId = resolveFavoriteExercise(owner, payload)
            ?: return mark(staged, SyncStagedStatus.WAITING_DEPENDENCY, "Exercise not available")

        // A newer staged tombstone for this identity must win over a late UPSERT.
        val tombstone = stagedDao.find(owner, SyncEntityTypes.FAVORITE_EXERCISE, staged.syncId)
        if (tombstone != null && tombstone.deleted && tombstone.serverVersion >= staged.serverVersion) {
            return mark(staged, SyncStagedStatus.APPLIED)
        }

        val local = dao.findByOwnerAndExercise(owner, exerciseId)
        if (local != null) {
            if (local.syncId != staged.syncId) {
                return mark(staged, SyncStagedStatus.CONFLICT_BLOCKED, "Conflicting favorite identity")
            }
            if (local.serverVersion >= staged.serverVersion) {
                return mark(staged, SyncStagedStatus.APPLIED)
            }
            dao.applyRemoteUpsert(owner, staged.syncId, staged.serverVersion, staged.serverUpdatedAt)
            return mark(staged, SyncStagedStatus.APPLIED)
        }

        dao.insert(FavoriteExerciseEntity(
            exerciseId = exerciseId,
            ownerFirebaseUid = owner,
            syncId = staged.syncId,
            addedAt = staged.serverUpdatedAt,
            updatedAt = staged.serverUpdatedAt,
            serverVersion = staged.serverVersion,
            syncStatus = "SYNCED",
        ))
        return mark(staged, SyncStagedStatus.APPLIED)
    }

    private suspend fun applyScheduledWorkout(
        owner: String,
        staged: SyncStagedRecordEntity,
        payload: JsonObject,
    ): StagedApplyResult {
        val dao = scheduledWorkoutDao ?: return mark(staged, SyncStagedStatus.UNSUPPORTED)
        val workoutId = resolveWorkout(owner, payload)
            ?: return mark(staged, SyncStagedStatus.WAITING_DEPENDENCY, "Workout not available")

        // A newer staged tombstone for this identity must win over a late UPSERT.
        val tombstone = stagedDao.find(owner, SyncEntityTypes.SCHEDULED_WORKOUT, staged.syncId)
        if (tombstone != null && tombstone.deleted && tombstone.serverVersion >= staged.serverVersion) {
            return mark(staged, SyncStagedStatus.APPLIED)
        }

        val dayOfWeek = payload.jsonPrimitiveInt("dayOfWeek") ?: return mark(staged, SyncStagedStatus.FAILED, "Missing dayOfWeek")
        val startMinutes = payload.jsonPrimitiveInt("startMinutes") ?: return mark(staged, SyncStagedStatus.FAILED, "Missing startMinutes")
        val scheduledDateKey = payload.jsonPrimitiveInt("scheduledDateKey")
        val reminderEnabled = payload.get("reminderEnabled")?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true
        val reminderMinutesBefore = payload.jsonPrimitiveInt("reminderMinutesBefore") ?: 30
        val reminderNote = payload.string("reminderNote") ?: ""

        val local = dao.findBySyncId(owner, staged.syncId)
        if (local != null) {
            if (local.workoutId != workoutId) {
                return mark(staged, SyncStagedStatus.CONFLICT_BLOCKED, "Conflicting schedule workout reference")
            }
            if (local.serverVersion >= staged.serverVersion) {
                return mark(staged, SyncStagedStatus.APPLIED)
            }
            dao.applyRemoteUpsert(owner, staged.syncId, dayOfWeek, scheduledDateKey, startMinutes, reminderEnabled, reminderMinutesBefore, reminderNote, staged.serverVersion, staged.serverUpdatedAt)
            rescheduleScheduleAlarm(owner, staged.syncId)
            return mark(staged, SyncStagedStatus.APPLIED)
        }

        dao.insert(ScheduledWorkoutEntity(
            workoutId = workoutId,
            dayOfWeek = dayOfWeek,
            scheduledDateKey = scheduledDateKey,
            startMinutes = startMinutes,
            reminderEnabled = reminderEnabled,
            reminderMinutesBefore = reminderMinutesBefore,
            reminderNote = reminderNote,
            ownerFirebaseUid = owner,
            syncId = staged.syncId,
            updatedAt = staged.serverUpdatedAt,
            serverVersion = staged.serverVersion,
            syncStatus = "SYNCED",
        ))
        rescheduleScheduleAlarm(owner, staged.syncId)
        return mark(staged, SyncStagedStatus.APPLIED)
    }

    private suspend fun rescheduleScheduleAlarm(owner: String, syncId: String) {
        runCatching {
            val dao = scheduledWorkoutDao ?: return
            val entry = dao.findBySyncId(owner, syncId) ?: return
            if (entry.deletedAt != null) reminderScheduler?.cancel(entry.scheduleId)
            else reminderScheduler?.schedule(entry)
        }
    }

    private suspend fun mark(
        staged: SyncStagedRecordEntity,
        status: String,
        error: String? = null,
    ): StagedApplyResult {
        stagedDao.updateStatus(staged.ownerFirebaseUid, staged.stagedId, status, staged.retryCount, error, System.currentTimeMillis())
        return when (status) {
            SyncStagedStatus.APPLIED -> StagedApplyResult.APPLIED
            SyncStagedStatus.TOMBSTONE_ONLY -> StagedApplyResult.TOMBSTONE_ONLY
            SyncStagedStatus.WAITING_DEPENDENCY -> StagedApplyResult.WAITING_DEPENDENCY
            SyncStagedStatus.UNSUPPORTED -> StagedApplyResult.UNSUPPORTED
            SyncStagedStatus.UNSUPPORTED_SCHEMA -> StagedApplyResult.UNSUPPORTED_SCHEMA
            SyncStagedStatus.LOCAL_PENDING -> StagedApplyResult.LOCAL_PENDING
            SyncStagedStatus.FAILED -> StagedApplyResult.FAILED
            SyncStagedStatus.CONFLICT_BLOCKED -> StagedApplyResult.CONFLICT_BLOCKED
            else -> StagedApplyResult.FAILED
        }
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() }

    private fun JsonObject.jsonPrimitiveLong(key: String): Long? =
        runCatching { this[key]?.jsonPrimitive?.content?.toLongOrNull() }.getOrNull()

    private fun JsonObject.jsonPrimitiveInt(key: String): Int? =
        runCatching { this[key]?.jsonPrimitive?.content?.toIntOrNull() }.getOrNull()

    private fun JsonObject.stringList(key: String): List<String>? =
        this[key]?.jsonArray?.mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }

    companion object {
        const val SUPPORTED_SCHEMA_VERSION = 1
        private val APPLICABLE_TYPES = setOf(
            SyncEntityTypes.WORKOUT,
            SyncEntityTypes.CUSTOM_EXERCISE,
            SyncEntityTypes.WORKOUT_EXERCISE,
            SyncEntityTypes.FAVORITE_EXERCISE,
            SyncEntityTypes.SCHEDULED_WORKOUT,
        )
    }
}

enum class StagedApplyResult {
    APPLIED,
    TOMBSTONE_ONLY,
    WAITING_DEPENDENCY,
    UNSUPPORTED,
    UNSUPPORTED_SCHEMA,
    LOCAL_PENDING,
    FAILED,
    CONFLICT_BLOCKED,
    OWNER_CHANGED,
}

/** Thrown inside the staged-apply transaction to abort an account-switched call. */
class OwnerChangedDuringStagedApplyException : RuntimeException()