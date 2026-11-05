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
import com.organizer.data.local.db.entities.WorkoutEntity
import com.organizer.data.media.MediaPriority
import com.organizer.data.repo.CategoryRepository
import com.organizer.data.repo.ExerciseCategoryRepository
import com.organizer.data.repo.ExerciseRepository
import com.organizer.data.repo.FileRepository
import com.organizer.data.repo.WorkoutExerciseRepository
import com.organizer.data.repo.WorkoutRepository
import com.organizer.presentation.model.SearchResult
import com.organizer.presentation.screens.workouts_and_exercises.CustomsTab
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

@HiltViewModel
class OrganizerViewModel @Inject constructor(
    private val categoryRepo: CategoryRepository,
    private val exerciseRepo: ExerciseRepository,
    private val exerciseCategoryRepo: ExerciseCategoryRepository,
    private val workoutRepo: WorkoutRepository,
    private val workoutExerciseRepo: WorkoutExerciseRepository,
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

    private fun matches(text: String, query: String): Boolean {
        val words = query
            .trim()
            .lowercase()
            .split(Regex("\\s+"))

        return words.all { word ->
            text.lowercase().contains(word)
        }
    }

    private fun score(text: String, query: String): Int {
        val t = text.lowercase()
        val q = query.lowercase()

        return when {
            t == q -> 0
            t.startsWith(q) -> 1
            t.split(" ").contains(q) -> 2
            t.contains(q) -> 3
            else -> 4
        }
    }

    val searchResultsUiState: StateFlow<List<SearchResult>> =
        combine(
            categoryRepo.observeSports(),
            categoryRepo.observeCategories(),
            exerciseRepo.observeAllExercises(),
            searchQuery
        ) { sports, categories, exercises, query ->
            if (query.isBlank()) {
                sports.map { SearchResult.Sport(it) }
            } else {
                val results = mutableListOf<SearchResult>()
                results += sports
                    .filter { matches(it.name, query) }
                    .sortedBy { score(it.name, query) }
                    .map { SearchResult.Sport(it) }

                results += categories
                    .filter { matches(it.name, query) }
                    .sortedBy { score(it.name, query) }
                    .map { SearchResult.Category(it) }

                results += exercises
                    .filter { matches(it.name, query) }
                    .sortedBy { score(it.name, query) }
                    .map { SearchResult.Exercise(it) }

                results
            }
        }.stateIn(
            viewModelScope,Sharing,emptyList()
        )

    fun getCategoryPath(categoryId: Long): Flow<List<CategoryEntity>> =
        categoryRepo.getCategoryPath(categoryId)

    // Exercises
    fun observeExercisesByCategory(categoryId: Long): Flow<List<ExerciseEntity>> =
        exerciseRepo.observeExercisesByCategory(categoryId)

    fun observeExercise(id: Long): Flow<ExerciseEntity?> =
        exerciseRepo.observeExercise(id)

    val customExercisesUiState: StateFlow<List<ExerciseEntity>> =
        exerciseRepo.observeCustomExercises()
            .stateIn(viewModelScope, Sharing, emptyList())

    // Workouts
    private val selectedWorkoutId = MutableStateFlow<Long?>(null)

    fun selectWorkout(id: Long) {
        selectedWorkoutId.value = id
    }

    val workoutsUiState: StateFlow<List<WorkoutEntity>> =
        workoutRepo.observeWorkouts()
            .stateIn(viewModelScope, Sharing, emptyList())

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

    fun saveCustomImage(uri: Uri): String? {
        return fileRepo.saveCustomImage(uri)
    }

    // Commands
    fun createWorkout(name: String) = viewModelScope.launch {
        workoutRepo.createWorkout(name)
    }

    fun deleteWorkout(id: Long) = viewModelScope.launch {
        workoutRepo.deleteWorkout(id)
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

    fun createCustomExercise(name: String, instructions: List<String>, imageName: String) =
        viewModelScope.launch {
            exerciseRepo.addCustomExercise(name, instructions, imageName)
        }

    fun deleteCustomExercise(id: Long) = viewModelScope.launch {
        exerciseRepo.deleteCustomExercise(id)
    }

    companion object {
        val Sharing = SharingStarted.WhileSubscribed(5000)
    }
}
