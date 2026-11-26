package com.organizer.presentation.screens.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.organizer.data.local.db.entities.ScheduledWorkoutEntity
import com.organizer.data.local.db.entities.WorkoutWithExerciseCount
import com.organizer.data.repo.CategoryRepository
import com.organizer.data.repo.ExerciseCategoryRepository
import com.organizer.data.repo.ScheduleRepository
import com.organizer.data.repo.WorkoutCompletionRepository
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
    private val workoutCompletionRepo: WorkoutCompletionRepository,
) : ViewModel() {

    /** Currently selected day in the weekly strip (ISO: Monday = 1). */
    val selectedDay = MutableStateFlow(currentIsoDay())

    /** 0 = current week; negative/positive values browse neighbouring weeks. */
    val weekOffset = MutableStateFlow(0)

    // Entry writes are tiny but must not interleave: an edit saved from the
    // sheet and a note update from the card would otherwise overwrite each
    // other with stale snapshots.
    private val entryMutex = Mutex()

    val workoutsUiState: StateFlow<List<WorkoutWithExerciseCount>?> =
        workoutRepo.observeWorkoutsWithExerciseCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Completion percentage per date key (`yyyyMMdd`), written by the workout
     * player when an execution ends. The day strip shows it on that date.
     */
    val completionPercentByDate: StateFlow<Map<Int, Int>> =
        workoutCompletionRepo.observeCompletionPercentByDate()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

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
        scheduledDateKey: Int?,
        startMinutes: Int,
        reminderEnabled: Boolean,
        reminderMinutesBefore: Int,
        reminderNote: String,
    ) = viewModelScope.launch {
        entryMutex.withLock {
            if (scheduleId == null) {
                scheduleRepo.addEntry(
                    workoutId = workoutId,
                    dayOfWeek = dayOfWeek,
                    scheduledDateKey = scheduledDateKey,
                    startMinutes = startMinutes,
                    reminderEnabled = reminderEnabled,
                    reminderMinutesBefore = reminderMinutesBefore,
                    reminderNote = reminderNote,
                )
            } else {
                val updated = buildEntry(
                    scheduleId = scheduleId,
                    workoutId = workoutId,
                    dayOfWeek = dayOfWeek,
                    scheduledDateKey = scheduledDateKey,
                    startMinutes = startMinutes,
                    reminderEnabled = reminderEnabled,
                    reminderMinutesBefore = reminderMinutesBefore,
                    reminderNote = reminderNote,
                )
                scheduleRepo.updateEntry(updated)
            }
        }
    }

    /**
     * Recreates every pending alarm. Called after the user grants exact-alarm
     * access so existing reminders switch from inexact to exact delivery.
     */
    fun rescheduleReminders() = viewModelScope.launch {
        entryMutex.withLock {
            reminderScheduler.rescheduleAll(scheduleRepo.getSchedule())
        }
    }

    fun updateReminderNote(scheduleId: Long, note: String) = viewModelScope.launch {
        // The receiver reads the note from the database when the alarm fires,
        // so changing it never needs a new alarm.
        entryMutex.withLock {
            scheduleRepo.updateNote(scheduleId, note)
        }
    }

    fun removeEntry(item: ScheduledWorkoutItem) = viewModelScope.launch {
        entryMutex.withLock {
            scheduleRepo.deleteEntry(item.entry.scheduleId)
        }
    }

    private fun buildEntry(
        scheduleId: Long,
        workoutId: Long,
        dayOfWeek: Int,
        scheduledDateKey: Int?,
        startMinutes: Int,
        reminderEnabled: Boolean,
        reminderMinutesBefore: Int,
        reminderNote: String,
    ) = ScheduledWorkoutEntity(
        scheduleId = scheduleId,
        workoutId = workoutId,
        dayOfWeek = dayOfWeek,
        scheduledDateKey = scheduledDateKey,
        startMinutes = startMinutes,
        reminderEnabled = reminderEnabled,
        reminderMinutesBefore = reminderMinutesBefore,
        reminderNote = reminderNote,
    )
}
