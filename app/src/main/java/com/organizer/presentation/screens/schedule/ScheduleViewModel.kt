package com.organizer.presentation.screens.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.organizer.data.local.db.entities.ScheduledWorkoutEntity
import com.organizer.data.local.db.entities.WorkoutWithExerciseCount
import com.organizer.data.repo.CategoryRepository
import com.organizer.data.repo.ExerciseCategoryRepository
import com.organizer.data.repo.ScheduleRepository
import com.organizer.data.repo.WorkoutExerciseRepository
import com.organizer.data.repo.WorkoutRepository
import com.organizer.data.repo.usedCategories
import com.organizer.notification.WorkoutReminderScheduler
import com.organizer.presentation.model.ScheduledWorkoutItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val scheduleRepo: ScheduleRepository,
    private val workoutRepo: WorkoutRepository,
    private val workoutExerciseRepo: WorkoutExerciseRepository,
    private val exerciseCategoryRepo: ExerciseCategoryRepository,
    private val categoryRepo: CategoryRepository,
    private val reminderScheduler: WorkoutReminderScheduler,
) : ViewModel() {

    /** Currently selected day in the weekly strip (ISO: Monday = 1). */
    val selectedDay = MutableStateFlow(currentIsoDay())

    /** 0 = current week; negative/positive values browse neighbouring weeks. */
    val weekOffset = MutableStateFlow(0)

    // Reminder writes are tiny but must not interleave: a toggle and a timing
    // change happening together would otherwise overwrite each other.
    private val entryMutex = Mutex()

    val workoutsUiState: StateFlow<List<WorkoutWithExerciseCount>?> =
        workoutRepo.observeWorkoutsWithExerciseCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val workoutCategories: Flow<Map<Long, List<String>>> = combine(
        workoutExerciseRepo.observeAllWorkoutExercises(),
        exerciseCategoryRepo.observeAllLinks(),
        categoryRepo.observeSports(),
        categoryRepo.observeCategories(),
    ) { workoutExercises, links, sports, categories ->
        val categoriesById = (sports + categories).associateBy { it.categoryId }
        val linksByExercise = links.groupBy { it.exerciseId }

        workoutExercises
            .groupBy { it.workoutId }
            .mapValues { (_, entries) ->
                usedCategories(
                    exerciseIds = entries
                        .sortedBy { it.orderIndex }
                        .map { it.exerciseId },
                    linksByExercise = linksByExercise,
                    categories = categoriesById,
                )
            }
    }

    /**
     * Every recurring entry joined with its workout. Entries whose workout no
     * longer exists are skipped; the foreign key normally removes them already.
     */
    val entriesUiState: StateFlow<List<ScheduledWorkoutItem>?> = combine(
        scheduleRepo.observeSchedule(),
        workoutRepo.observeWorkoutsWithExerciseCount(),
        workoutCategories,
    ) { entries, workouts, categories ->
        val workoutsById = workouts.associateBy { it.workoutId }

        entries
            .mapNotNull { entry ->
                val workout = workoutsById[entry.workoutId] ?: return@mapNotNull null
                ScheduledWorkoutItem(
                    entry = entry,
                    workoutName = workout.name,
                    exerciseCount = workout.exerciseCount,
                    categories = categories[entry.workoutId].orEmpty(),
                )
            }
            .sortedWith(compareBy({ it.entry.startMinutes }, { it.entry.scheduleId }))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectDay(isoDay: Int) {
        selectedDay.value = isoDay
    }

    fun previousWeek() {
        weekOffset.value -= 1
    }

    fun nextWeek() {
        weekOffset.value += 1
    }

    fun saveEntry(
        scheduleId: Long?,
        workoutId: Long,
        dayOfWeek: Int,
        startMinutes: Int,
        reminderEnabled: Boolean,
        reminderMinutesBefore: Int,
        reminderNote: String,
    ) = viewModelScope.launch {
        entryMutex.withLock {
            if (scheduleId == null) {
                val newId = scheduleRepo.addEntry(
                    workoutId = workoutId,
                    dayOfWeek = dayOfWeek,
                    startMinutes = startMinutes,
                    reminderEnabled = reminderEnabled,
                    reminderMinutesBefore = reminderMinutesBefore,
                    reminderNote = reminderNote,
                )
                reminderScheduler.schedule(
                    buildEntry(
                        scheduleId = newId,
                        workoutId = workoutId,
                        dayOfWeek = dayOfWeek,
                        startMinutes = startMinutes,
                        reminderEnabled = reminderEnabled,
                        reminderMinutesBefore = reminderMinutesBefore,
                        reminderNote = reminderNote,
                    )
                )
            } else {
                val updated = buildEntry(
                    scheduleId = scheduleId,
                    workoutId = workoutId,
                    dayOfWeek = dayOfWeek,
                    startMinutes = startMinutes,
                    reminderEnabled = reminderEnabled,
                    reminderMinutesBefore = reminderMinutesBefore,
                    reminderNote = reminderNote,
                )
                scheduleRepo.updateEntry(updated)
                reminderScheduler.schedule(updated)
            }
        }
    }

    fun setReminderEnabled(scheduleId: Long, enabled: Boolean) =
        updateEntry(scheduleId) { it.copy(reminderEnabled = enabled) }

    fun setReminderMinutesBefore(scheduleId: Long, minutes: Int) =
        updateEntry(scheduleId) { it.copy(reminderMinutesBefore = minutes) }

    fun updateReminderNote(scheduleId: Long, note: String) = viewModelScope.launch {
        // The receiver reads the note from the database when the alarm fires,
        // so changing it never needs a new alarm.
        entryMutex.withLock {
            scheduleRepo.updateNote(scheduleId, note)
        }
    }

    fun removeEntry(item: ScheduledWorkoutItem) = viewModelScope.launch {
        entryMutex.withLock {
            reminderScheduler.cancel(item.entry.scheduleId)
            scheduleRepo.deleteEntry(item.entry.scheduleId)
        }
    }

    /**
     * Applies a small change to the newest stored row instead of a snapshot
     * captured by the UI. Toggling the reminder and picking a lead time quickly
     * must not undo each other.
     */
    private fun updateEntry(
        scheduleId: Long,
        transform: (ScheduledWorkoutEntity) -> ScheduledWorkoutEntity,
    ) = viewModelScope.launch {
        entryMutex.withLock {
            val current = scheduleRepo.getEntry(scheduleId) ?: return@withLock
            val updated = transform(current)
            scheduleRepo.updateEntry(updated)
            reminderScheduler.schedule(updated)
        }
    }

    private fun buildEntry(
        scheduleId: Long,
        workoutId: Long,
        dayOfWeek: Int,
        startMinutes: Int,
        reminderEnabled: Boolean,
        reminderMinutesBefore: Int,
        reminderNote: String,
    ) = ScheduledWorkoutEntity(
        scheduleId = scheduleId,
        workoutId = workoutId,
        dayOfWeek = dayOfWeek,
        startMinutes = startMinutes,
        reminderEnabled = reminderEnabled,
        reminderMinutesBefore = reminderMinutesBefore,
        reminderNote = reminderNote,
    )
}
