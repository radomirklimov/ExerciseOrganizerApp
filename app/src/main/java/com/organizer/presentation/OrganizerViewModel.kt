package com.organizer.presentation

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.organizer.data.local.db.entities.CategoryEntity
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.local.db.entities.ExerciseProgressEntity
import com.organizer.data.local.db.entities.PlannedSetEntity
import com.organizer.data.local.db.entities.StarterTemplateEntity
import com.organizer.data.local.db.entities.WorkoutEntity
import com.organizer.data.local.db.entities.WorkoutWithExerciseCount
import com.organizer.data.local.storage.NormalizedCrop
import com.organizer.data.media.MediaPriority
import com.organizer.data.repo.CategoryRepository
import com.organizer.data.repo.ExerciseCategoryRepository
import com.organizer.data.repo.ExerciseProgressRepository
import com.organizer.data.repo.ExerciseRepository
import com.organizer.data.repo.FavoriteExerciseRepository
import com.organizer.data.repo.FileRepository
import com.organizer.data.repo.PlannedSetRepository
import com.organizer.data.repo.StarterTemplateRepository
import com.organizer.data.repo.WorkoutCompletionRepository
import com.organizer.data.repo.WorkoutExerciseRepository
import com.organizer.data.repo.WorkoutRepository
import com.organizer.data.repo.exerciseCategoryNames
import com.organizer.data.repo.joinCategoryNames
import com.organizer.data.repo.usedCategories
import com.organizer.presentation.model.ExerciseCatalogEntry
import com.organizer.presentation.screens.workouts_and_exercises.CustomsTab
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Calendar

@HiltViewModel
class OrganizerViewModel @Inject constructor(
    private val categoryRepo: CategoryRepository,
    private val exerciseRepo: ExerciseRepository,
    private val exerciseProgressRepo: ExerciseProgressRepository,
    private val exerciseCategoryRepo: ExerciseCategoryRepository,
    private val workoutRepo: WorkoutRepository,
    private val workoutExerciseRepo: WorkoutExerciseRepository,
    private val starterTemplateRepo: StarterTemplateRepository,
    private val favoriteExerciseRepo: FavoriteExerciseRepository,
    private val plannedSetRepo: PlannedSetRepository,
    private val workoutCompletionRepo: WorkoutCompletionRepository,
    private val fileRepo: FileRepository,
) : ViewModel() {

    // General UI state
    val isSyncing = MutableStateFlow(false)

    var errorMessage: String? by mutableStateOf(null)
        private set

    val selectedTab = MutableStateFlow(CustomsTab.WORKOUTS)

    // Search
    val searchQuery = MutableStateFlow("")

    // Initialization
    init {
        syncDb()
    }

    fun syncDb() {
        // Data sync only: repositories enqueue missing media in the background
        // without waiting for downloads, so isSyncing never covers media.
        if (isSyncing.value) return
        viewModelScope.launch {
            isSyncing.value = true
            errorMessage = null
            try {
                categoryRepo.refreshSports()
                categoryRepo.refreshCategories()
                exerciseRepo.refreshExercises()
                exerciseCategoryRepo.refreshExerciseCategory()
                // Starter templates are optional content: a failure here must
                // not mark the whole library sync as failed.
                try {
                    val unresolved = starterTemplateRepo.refreshTemplates()
                    if (unresolved > 0) {
                        Log.w("SYNC", "Starter template refresh had $unresolved unresolved exercise IDs")
                    }
                } catch (e: Exception) {
                    Log.w("SYNC", "Couldn't refresh starter templates", e)
                }
                // Catch-up for files missing for any reason (failed earlier,
                // restored database, ...). Queuing is immediate; downloads run
                // on the application-scoped media queue.
                categoryRepo.enqueueMissingIcons()
                exerciseRepo.enqueueMissingImages()
            } catch (e: IOException) {
                Log.e("SYNC", "No internet connection", e)
                errorMessage = "No internet connection. Please check your connection and try again."
            } catch (e: Exception) {
                Log.e("SYNC", "Sync failed", e)
                errorMessage = "Couldn't load data"
            } finally {
                isSyncing.value = false
            }
        }
    }

    // Categories
    fun observeSubcategories(categoryId: Long): Flow<List<CategoryEntity>> =
        categoryRepo.observeSubcategories(categoryId)

    /** Sport roots shown on the home page while the search field is empty. */
    val sportsUiState: StateFlow<List<CategoryEntity>?> =
        categoryRepo.observeSports()
            .stateIn(viewModelScope, Sharing, null)

    fun getCategoryPath(categoryId: Long): Flow<List<CategoryEntity>> =
        categoryRepo.getCategoryPath(categoryId)

    // Exercises
    fun observeExercisesByCategory(categoryId: Long): Flow<List<ExerciseEntity>> =
        exerciseRepo.observeExercisesByCategory(categoryId)

    fun observeExercise(id: Long): Flow<ExerciseEntity?> =
        exerciseRepo.observeExercise(id)

    /**
     * Every exercise with its category summary, backing the exercise picker on
     * the Create Workout screen.
     */
    val exerciseCatalogUiState: StateFlow<List<ExerciseCatalogEntry>?> =
        combine(
            exerciseRepo.observeAllExercises(),
            exerciseCategoryRepo.observeAllLinks(),
            categoryRepo.observeSports(),
            categoryRepo.observeCategories(),
        ) { exercises, links, sports, categories ->
            val allCategories = sports + categories
            val categoriesById = allCategories.associateBy { it.categoryId }
            val linksByExercise = links.groupBy { it.exerciseId }

            exercises.map { exercise ->
                val names = exerciseCategoryNames(
                    linkedCategories = linksByExercise[exercise.exerciseId]
                        .orEmpty()
                        .mapNotNull { categoriesById[it.categoryId] },
                    allCategories = allCategories,
                )
                ExerciseCatalogEntry(
                    exercise = exercise,
                    categoryLabel = joinCategoryNames(names),
                )
            }
        }.stateIn(viewModelScope, Sharing, null)

    /** A recent, mixed selection of exercises used across the user's workouts. */
    val suggestedExercisesUiState: StateFlow<List<ExerciseEntity>?> =
        workoutExerciseRepo.observeSuggestedExercises(SUGGESTION_LIMIT)
            .stateIn(viewModelScope, Sharing, null)

    /** Short category summary shown under the exercise name, e.g. "Chest & Arms". */
    fun observeExerciseCategoryLabel(exerciseId: Long): Flow<String?> = combine(
        exerciseCategoryRepo.observeCategoriesForExercise(exerciseId),
        categoryRepo.observeSports(),
        categoryRepo.observeCategories(),
    ) { linked, sports, categories ->
        joinCategoryNames(exerciseCategoryNames(linked, sports + categories))
    }

    fun observeExerciseSets(workoutId: Long, exerciseId: Long): Flow<List<ExerciseProgressEntity>> =
        exerciseProgressRepo.observeSets(workoutId, exerciseId)

    /** Sets logged during one execution, used by the workout player. */
    fun observeSessionSets(
        workoutId: Long,
        exerciseId: Long,
        sessionId: Long,
    ): Flow<List<ExerciseProgressEntity>> =
        exerciseProgressRepo.observeSessionSets(workoutId, exerciseId, sessionId)

    fun addExerciseSet(
        workoutId: Long,
        exerciseId: Long,
        setNumber: Int,
        reps: Int? = null,
        weight: Double? = null,
        durationSeconds: Int? = null,
        sessionId: Long = 0L,
        unitSystem: String = "METRIC",
    ) = viewModelScope.launch {
        exerciseProgressRepo.addSet(
            workoutId,
            exerciseId,
            setNumber,
            reps,
            weight,
            durationSeconds,
            sessionId,
            unitSystem,
        )
    }

    fun deleteExerciseSet(progressId: Long) = viewModelScope.launch {
        exerciseProgressRepo.deleteSet(progressId)
    }

    fun updateExerciseSet(
        progressId: Long,
        reps: Int? = null,
        weight: Double? = null,
        durationSeconds: Int? = null,
    ) = viewModelScope.launch {
        exerciseProgressRepo.updateSet(progressId, reps, weight, durationSeconds)
    }

    // Planned sets (routine targets, independent of logged progress).
    fun observePlannedSets(
        workoutId: Long,
        exerciseId: Long,
    ): Flow<List<PlannedSetEntity>> =
        plannedSetRepo.observePlannedSets(workoutId, exerciseId)

    /** Adds one planned set through the transactional repository/outbox path. */
    fun addPlannedSet(
        workoutId: Long,
        exerciseId: Long,
        setNumber: Int,
        reps: Int? = null,
        weight: Double? = null,
        durationSeconds: Int? = null,
    ) = viewModelScope.launch {
        plannedSetRepo.addSet(workoutId, exerciseId, setNumber, reps, weight, durationSeconds)
    }

    fun updatePlannedSet(
        workoutId: Long,
        exerciseId: Long,
        setNumber: Int,
        reps: Int? = null,
        weight: Double? = null,
        durationSeconds: Int? = null,
    ) = viewModelScope.launch {
        plannedSetRepo.updateSet(workoutId, exerciseId, setNumber, reps, weight, durationSeconds)
    }

    fun deletePlannedSet(workoutId: Long, exerciseId: Long, setNumber: Int) = viewModelScope.launch {
        plannedSetRepo.deleteSet(workoutId, exerciseId, setNumber)
    }

    /**
     * Records how many exercises of a workout were performed. The schedule
     * calendar shows the resulting percentage on today's date; no individual
     * results are saved.
     */
    fun recordWorkoutCompletion(
        workoutId: Long,
        completedExercises: Int,
        totalExercises: Int,
    ) = viewModelScope.launch {
        workoutCompletionRepo.recordCompletion(
            dateKey = currentDateKey(),
            workoutId = workoutId,
            completedExercises = completedExercises,
            totalExercises = totalExercises,
        )
    }

    fun observeWorkout(id: Long): Flow<WorkoutEntity?> =
        workoutRepo.observeWorkoutById(id)

    val customExercisesUiState: StateFlow<List<ExerciseEntity>?> =
        exerciseRepo.observeCustomExercises()
            .stateIn(viewModelScope, Sharing, null)

    // Favorite exercises: ids are used by every exercise card to render its
    // heart, the full list backs the Exercises page "Favorites" tab.
    val favoriteExerciseIds: StateFlow<Set<Long>> =
        favoriteExerciseRepo.observeFavoriteExerciseIds()
            .map { it.toSet() }
            .stateIn(viewModelScope, Sharing, emptySet())

    val favoriteExercisesUiState: StateFlow<List<ExerciseEntity>?> =
        favoriteExerciseRepo.observeFavoriteExercises()
            .stateIn(viewModelScope, Sharing, null)

    fun toggleFavoriteExercise(exerciseId: Long) = viewModelScope.launch {
        try {
            favoriteExerciseRepo.toggleFavorite(exerciseId)
        } catch (e: Exception) {
            Log.e("FAVORITES", "Couldn't update favorites", e)
            errorMessage = "Couldn't update favorites. Please try again."
        }
    }

    // Workouts
    private val selectedWorkoutId = MutableStateFlow<Long?>(null)

    fun selectWorkout(id: Long) {
        selectedWorkoutId.value = id
    }

    val workoutsUiState: StateFlow<List<WorkoutEntity>?> =
        workoutRepo.observeWorkouts()
            .stateIn(viewModelScope, Sharing, null)

    /**
     * Categories covered by each workout, ordered by how many of its exercises
     * use them. Uses the same category summarising as starter template cards,
     * so a workout imported from a template shows the same label it had there.
     */
    val workoutCategoryNamesUiState: StateFlow<Map<Long, List<String>>?> =
        combine(
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
                        // Follow the workout's exercise order so equally used
                        // categories are listed in the order the user sees.
                        exerciseIds = entries.sortedBy { it.orderIndex }.map { it.exerciseId },
                        linksByExercise = linksByExercise,
                        categories = categoriesById,
                    )
                }
        }.stateIn(viewModelScope, Sharing, null)

    /** Workouts with their exercise counts, used by the Add-to-workout sheet. */
    val workoutsWithCountsUiState: StateFlow<List<WorkoutWithExerciseCount>?> =
        workoutRepo.observeWorkoutsWithExerciseCount()
            .stateIn(viewModelScope, Sharing, null)

    // Starter workout templates (server content, never mixed into workouts).
    val starterTemplatesUiState: StateFlow<List<StarterTemplateEntity>?> =
        starterTemplateRepo.observeTemplates()
            .stateIn(viewModelScope, Sharing, null)

    fun observeStarterTemplate(templateId: Long): Flow<StarterTemplateEntity?> =
        starterTemplateRepo.observeTemplate(templateId)

    fun observeStarterTemplateExercises(templateId: Long): Flow<List<ExerciseEntity>> =
        starterTemplateRepo.observeTemplateExercises(templateId)

    /**
     * Copies the opened template into the user's workouts and hands the new
     * local workout id to [onCreated] so the UI can open the editable screen.
     */
    fun addStarterTemplateToWorkouts(
        templateId: Long,
        onCreated: (Long) -> Unit,
    ) = viewModelScope.launch {
        try {
            val workoutId = starterTemplateRepo.importTemplate(templateId)
            if (workoutId != null) {
                onCreated(workoutId)
            } else {
                errorMessage = "Couldn't add this template. Please try again."
            }
        } catch (e: Exception) {
            Log.e("WORKOUT", "Couldn't add starter template", e)
            errorMessage = "Couldn't add this template. Please try again."
        }
    }

    val workoutUiState: StateFlow<WorkoutEntity?> =
        selectedWorkoutId
            .filterNotNull()
            .flatMapLatest { id ->
                workoutRepo.observeWorkoutById(id)
            }
            .stateIn(viewModelScope, Sharing, null)

    fun observeWorkoutExercises(workoutId: Long) =
        exerciseRepo.observeExercisesByWorkout(workoutId)

    // File operations
    // Bumped on every saved media file; image composables collect it so newly
    // downloaded files appear without leaving the screen.
    val mediaVersion: StateFlow<Long> = fileRepo.mediaVersion

    fun getIconFile(name: String): File? {
        return fileRepo.getIcon(name)
    }

    fun getImageFile(name: String, isCustom: Boolean): File? {
        return fileRepo.getImage(name, isCustom)
    }

    // Media prioritization: screens re-request their visible media with
    // CURRENT priority using data they already observe. Queued entries are
    // raised instead of duplicated; missing files keep downloading in order.
    fun prioritizeSportsIcons(sports: List<CategoryEntity>) {
        fileRepo.enqueueIcons(sports.map { it.iconUrl }, MediaPriority.CURRENT)
    }

    fun prioritizeCategoryMedia(
        subcategories: List<CategoryEntity>,
        exercises: List<ExerciseEntity>,
        categoryPath: List<CategoryEntity>,
    ) {
        fileRepo.enqueueIcons(subcategories.map { it.iconUrl }, MediaPriority.CURRENT)
        fileRepo.enqueueImages(
            exercises.filter { !it.isCustom }.flatMap { it.imageUrls },
            MediaPriority.CURRENT
        )
        fileRepo.enqueueIcons(categoryPath.map { it.iconUrl }, MediaPriority.UPCOMING)
    }

    fun prioritizeExerciseMedia(exercise: ExerciseEntity) {
        if (!exercise.isCustom) {
            fileRepo.enqueueImages(exercise.imageUrls, MediaPriority.CURRENT)
        }
    }

    fun prioritizeWorkoutMedia(exercises: List<ExerciseEntity>) {
        fileRepo.enqueueImages(
            exercises.filter { !it.isCustom }.flatMap { it.imageUrls },
            MediaPriority.CURRENT
        )
    }

    /**
     * Crops [uri] to [crop] and stores it as a custom exercise image. Runs off
     * the main thread because decoding can be expensive; [onSaved] receives
     * the stored file name or null when the image could not be saved.
     */
    fun saveCustomImage(
        uri: Uri,
        crop: NormalizedCrop,
        onSaved: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            val imageName = withContext(Dispatchers.IO) {
                fileRepo.saveCustomImage(uri, crop)
            }
            onSaved(imageName)
        }
    }

    // Commands
    fun createWorkout(
        name: String,
        notes: String = "",
        exerciseIds: List<Long> = emptyList(),
        onCreated: (Long) -> Unit = {},
    ) = viewModelScope.launch {
        val workoutId = workoutRepo.createWorkout(name, notes)
        // Add exercises in one coroutine so orderIndex values stay sequential.
        exerciseIds.distinct().forEach { exerciseId ->
            workoutExerciseRepo.addExerciseToWorkout(workoutId, exerciseId)
        }
        onCreated(workoutId)
    }

    fun deleteWorkout(id: Long) = viewModelScope.launch {
        workoutRepo.deleteWorkout(id)
    }

    fun updateWorkoutNotes(id: Long, notes: String) = viewModelScope.launch {
        workoutRepo.updateWorkoutNotes(id, notes)
    }

    /** Keystrokes are saved locally only; no outbox mutation, no sync trigger. */
    fun updateWorkoutNotesWhileEditing(id: Long, notes: String) = viewModelScope.launch {
        workoutRepo.updateWorkoutNotesWhileEditing(id, notes)
    }

    /** Editing finished: durably save the latest text and make it sync-eligible. */
    fun commitWorkoutNotes(id: Long, notes: String) = viewModelScope.launch {
        workoutRepo.commitWorkoutNotes(id, notes)
    }

    fun addExerciseToWorkout(workoutId: Long, exerciseId: Long) = viewModelScope.launch {
        workoutExerciseRepo.addExerciseToWorkout(workoutId, exerciseId)
    }

    fun deleteExerciseFromWorkout(workoutId: Long, exerciseId: Long) = viewModelScope.launch {
        workoutExerciseRepo.deleteExerciseFromWorkout(workoutId, exerciseId)
    }

    fun updateExerciseOrder(workoutId: Long, exercises: List<ExerciseEntity>) =
        viewModelScope.launch {
            workoutExerciseRepo.updateExerciseOrder(workoutId, exercises)
        }

    fun createCustomExercise(
        name: String,
        instructions: List<String>,
        imageName: String,
        exerciseType: String = "REPS_WEIGHT",
    ) = viewModelScope.launch {
        exerciseRepo.addCustomExercise(name, instructions, imageName, exerciseType)
    }

    fun deleteCustomExercise(id: Long) = viewModelScope.launch {
        exerciseRepo.deleteCustomExercise(id)
    }

    companion object {
        val Sharing = SharingStarted.WhileSubscribed(5000)

        /** How many workout suggestions the Create Workout screen shows. */
        private const val SUGGESTION_LIMIT = 10
    }
}

/** Compact local date (`yyyyMMdd`), matching the schedule's one-time entries. */
private fun currentDateKey(): Int {
    val calendar = Calendar.getInstance()
    return calendar.get(Calendar.YEAR) * 10_000 +
        (calendar.get(Calendar.MONTH) + 1) * 100 +
        calendar.get(Calendar.DAY_OF_MONTH)
}
