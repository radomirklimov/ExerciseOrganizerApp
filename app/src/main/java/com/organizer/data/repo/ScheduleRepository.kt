package com.organizer.data.repo

import com.organizer.auth.OwnerProvider
import com.organizer.data.local.dao.ScheduledWorkoutDao
import com.organizer.data.local.dao.SyncOutboxDao
import com.organizer.data.local.dao.WorkoutDao
import com.organizer.data.local.db.entities.ScheduledWorkoutEntity
import com.organizer.data.local.db.entities.WorkoutEntity
import com.organizer.sync.OwnerChangedDuringLocalWriteException
import com.organizer.sync.SyncEntityTypes
import com.organizer.sync.SyncOperationLocks
import com.organizer.sync.SyncOutboxStatus
import com.organizer.sync.SyncOutboxWriter
import com.organizer.sync.SyncPayloadFactory
import com.organizer.sync.SyncTransactionRunner
import com.organizer.notification.ReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

class ScheduleRepository @Inject constructor(
    private val scheduleDao: ScheduledWorkoutDao,
    private val workoutDao: WorkoutDao,
    private val outboxDao: SyncOutboxDao,
    private val ownerProvider: OwnerProvider,
    private val transactions: SyncTransactionRunner,
    private val outboxWriter: SyncOutboxWriter,
    private val payloadFactory: SyncPayloadFactory,
    private val operationLocks: SyncOperationLocks,
    private val reminderScheduler: ReminderScheduler? = null,
) {
    fun observeSchedule(): Flow<List<ScheduledWorkoutEntity>> =
        scheduleDao.observeAll(ownerProvider.currentOwner())

    suspend fun getSchedule(): List<ScheduledWorkoutEntity> =
        scheduleDao.getAllOnce(ownerProvider.currentOwner())

    suspend fun getEntry(scheduleId: Long): ScheduledWorkoutEntity? =
        scheduleDao.getById(ownerProvider.currentOwner(), scheduleId)

    suspend fun getWorkout(workoutId: Long): WorkoutEntity? =
        workoutDao.getByIdOnce(ownerProvider.currentOwner(), workoutId)

    private fun validate(dayOfWeek: Int, scheduledDateKey: Int?, startMinutes: Int, reminderMinutesBefore: Int) {
        require(dayOfWeek in 1..7) { "dayOfWeek must be 1..7" }
        require(startMinutes in 0..1439) { "startMinutes must be 0..1439" }
        require(reminderMinutesBefore >= 0) { "reminderMinutesBefore must be non-negative" }
        if (scheduledDateKey != null) {
            require(scheduledDateKey in 10000101..99991231) { "scheduledDateKey must be yyyyMMdd" }
            val date = runCatching {
                java.time.LocalDate.of(scheduledDateKey / 10000, (scheduledDateKey / 100) % 100, scheduledDateKey % 100)
            }.getOrElse { throw IllegalArgumentException("scheduledDateKey must be a valid date") }
            require(date.dayOfWeek.value == dayOfWeek) { "scheduledDateKey dayOfWeek mismatch" }
        }
    }

    suspend fun addEntry(
        workoutId: Long,
        dayOfWeek: Int,
        scheduledDateKey: Int?,
        startMinutes: Int,
        reminderEnabled: Boolean,
        reminderMinutesBefore: Int,
        reminderNote: String,
    ): Long {
        val owner = ownerProvider.currentOwner()
        return operationLocks.forOwner(owner).withLock {
            val id = transactions.run {
                if (ownerProvider.currentOwner() != owner) throw OwnerChangedDuringLocalWriteException()
                validate(dayOfWeek, scheduledDateKey, startMinutes, reminderMinutesBefore)
                val workout = workoutDao.getByIdOnce(owner, workoutId)
                    ?: throw IllegalArgumentException("Unknown workout")
                val now = System.currentTimeMillis()
                val id = scheduleDao.insert(
                    ScheduledWorkoutEntity(
                        workoutId = workoutId,
                        dayOfWeek = dayOfWeek,
                        scheduledDateKey = scheduledDateKey,
                        startMinutes = startMinutes,
                        reminderEnabled = reminderEnabled,
                        reminderMinutesBefore = reminderMinutesBefore,
                        reminderNote = reminderNote,
                        ownerFirebaseUid = owner,
                        updatedAt = now,
                    )
                )
                if (owner != OwnerProvider.GUEST) {
                    val created = scheduleDao.getById(owner, id)!!
                    val payload = payloadFactory.scheduledWorkout(created, workout.syncId)
                    outboxWriter.upsertScheduledWorkout(owner, created.syncId, created.serverVersion, payload)
                }
                id
            }
            runCatching {
                scheduleDao.getById(owner, id)?.let { reminderScheduler?.schedule(it) }
            }
            id
        }
    }

    suspend fun updateEntry(entry: ScheduledWorkoutEntity) {
        val owner = ownerProvider.currentOwner()
        operationLocks.forOwner(owner).withLock {
            transactions.run {
                if (ownerProvider.currentOwner() != owner) throw OwnerChangedDuringLocalWriteException()
                validate(entry.dayOfWeek, entry.scheduledDateKey, entry.startMinutes, entry.reminderMinutesBefore)
                val existing = scheduleDao.getById(owner, entry.scheduleId) ?: return@run
                val workout = workoutDao.getByIdOnce(owner, entry.workoutId) ?: return@run
                val now = System.currentTimeMillis()
                scheduleDao.update(entry.copy(updatedAt = now, syncStatus = "PENDING_UPLOAD"))
                if (owner != OwnerProvider.GUEST) {
                    val updated = scheduleDao.getById(owner, entry.scheduleId)!!
                    val payload = payloadFactory.scheduledWorkout(updated, workout.syncId)
                    val rows = outboxDao.findByRecordSyncId(owner, existing.syncId)
                        .filter { it.entityType == SyncEntityTypes.SCHEDULED_WORKOUT }
                    if (rows.isNotEmpty() && rows.none { it.everTransmitted }) {
                        outboxDao.markSuperseded(owner, rows.map { it.mutationId }.toSet())
                    }
                    outboxWriter.upsertScheduledWorkout(owner, existing.syncId, existing.serverVersion, payload)
                }
            }
            runCatching {
                scheduleDao.getById(owner, entry.scheduleId)?.let { reminderScheduler?.schedule(it) }
            }
        }
    }

    suspend fun updateNote(scheduleId: Long, note: String) {
        val owner = ownerProvider.currentOwner()
        operationLocks.forOwner(owner).withLock {
            transactions.run {
                if (ownerProvider.currentOwner() != owner) throw OwnerChangedDuringLocalWriteException()
                scheduleDao.updateNote(owner, scheduleId, note, System.currentTimeMillis())
                if (owner != OwnerProvider.GUEST) {
                    val entry = scheduleDao.getById(owner, scheduleId) ?: return@run
                    val workout = workoutDao.getByIdOnce(owner, entry.workoutId) ?: return@run
                    val payload = payloadFactory.scheduledWorkout(entry, workout.syncId)
                    outboxWriter.upsertScheduledWorkout(owner, entry.syncId, entry.serverVersion, payload)
                }
            }
        }
    }

    suspend fun deleteEntry(scheduleId: Long) {
        val owner = ownerProvider.currentOwner()
        operationLocks.forOwner(owner).withLock {
            transactions.run {
                if (ownerProvider.currentOwner() != owner) throw OwnerChangedDuringLocalWriteException()
                val existing = scheduleDao.getById(owner, scheduleId) ?: return@run
                val now = System.currentTimeMillis()
                scheduleDao.softDelete(owner, scheduleId, now)
                if (owner != OwnerProvider.GUEST) {
                    val rows = outboxDao.findByRecordSyncId(owner, existing.syncId)
                        .filter { it.entityType == SyncEntityTypes.SCHEDULED_WORKOUT }
                    if (rows.isNotEmpty() && rows.none { it.everTransmitted }) {
                        outboxDao.markSuperseded(owner, rows.map { it.mutationId }.toSet())
                    } else {
                        val workout = workoutDao.getByIdOnce(owner, existing.workoutId)
                        if (workout != null) {
                            val payload = payloadFactory.scheduledWorkout(existing.copy(deletedAt = now, updatedAt = now), workout.syncId)
                            outboxWriter.deleteScheduledWorkout(owner, existing.syncId, existing.serverVersion, payload)
                        }
                    }
                }
            }
            runCatching { reminderScheduler?.cancel(scheduleId) }
        }
    }
}