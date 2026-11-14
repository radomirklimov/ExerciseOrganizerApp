package com.organizer.data.repo

import com.organizer.data.local.dao.CategoryDao
import com.organizer.data.local.dao.ExerciseCategoryDao
import com.organizer.data.local.dao.ExerciseDao
import com.organizer.data.local.dao.StarterTemplateDao
import com.organizer.data.local.dao.WorkoutDao
import com.organizer.data.local.dao.WorkoutExerciseDao
import com.organizer.data.local.db.entities.CategoryEntity
import com.organizer.data.local.db.entities.ExerciseCategoryEntity
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.local.db.entities.StarterTemplateEntity
import com.organizer.data.local.db.entities.StarterTemplateExerciseEntity
import com.organizer.data.local.db.entities.WorkoutEntity
import com.organizer.data.local.db.entities.WorkoutExerciseEntity
import com.organizer.data.mapper.asEntity
import com.organizer.data.remote.model.WorkoutTemplateExercise
import com.organizer.data.remote.repo.StarterTemplateRemoteDataSource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class StarterTemplateRepository @Inject constructor(
    private val templateDao: StarterTemplateDao,
    private val remoteDataSource: StarterTemplateRemoteDataSource,
    private val categoryDao: CategoryDao,
    private val exerciseCategoryDao: ExerciseCategoryDao,
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao,
    private val workoutExerciseDao: WorkoutExerciseDao,
    private val fileRepository: FileRepository,
) {

    fun observeTemplates(): Flow<List<StarterTemplateEntity>> = templateDao.observeAll()

    fun observeTemplate(templateId: Long): Flow<StarterTemplateEntity?> =
        templateDao.observeById(templateId)

    fun observeTemplateExercises(templateId: Long): Flow<List<ExerciseEntity>> =
        templateDao.observeExercises(templateId)

    /**
     * Downloads starter templates and resolves the category that dominates
     * each template (the standard category covering the most exercises).
     */
    suspend fun refreshTemplates() {
        val remoteTemplates = remoteDataSource.getTemplates()

        // Templates can arrive before an exercise refresh has stored a newly
        // added exercise; upsert the included exercise payloads so the foreign
        // keys in starter_template_exercise always resolve.
        val existingIds = exerciseDao.getAllOnce().map { it.exerciseId }.toSet()
        val missingExercises = remoteTemplates
            .flatMap { it.exercises }
            .mapNotNull { it.exercise }
            .filter { it.exerciseId !in existingIds }
            .distinctBy { it.exerciseId }
        if (missingExercises.isNotEmpty()) {
            exerciseDao.insertAll(missingExercises.map { it.asEntity() })
            fileRepository.enqueueImages(missingExercises.flatMap { it.images })
        }
        val availableIds = existingIds + missingExercises.map { it.exerciseId }

        val categories =
            (categoryDao.getSportsOnce() + categoryDao.getCategoriesOnce())
                .associateBy { it.categoryId }
        val linksByExercise = exerciseCategoryDao.getAllOnce().groupBy { it.exerciseId }

        val templateEntities = remoteTemplates.map { template ->
            val exerciseIds = template.exercises
                .sortedBy { it.orderIndex }
                .mapNotNull { it.exerciseIdOrNull() }
                .filter { it in availableIds }
                .distinct()
            val dominant = dominantCategory(exerciseIds, linksByExercise, categories)

            StarterTemplateEntity(
                templateId = template.workoutId,
                name = template.name,
                notes = template.notes,
                categoryName = dominant?.name,
                iconUrl = dominant?.iconUrl,
                exerciseCount = exerciseIds.size,
            )
        }

        val templateExercises = remoteTemplates.flatMap { template ->
            val seen = mutableSetOf<Long>()
            template.exercises
                .sortedBy { it.orderIndex }
                .mapNotNull { it.exerciseIdOrNull() }
                .filter { it in availableIds && seen.add(it) }
                .mapIndexed { index, exerciseId ->
                    StarterTemplateExerciseEntity(
                        templateId = template.workoutId,
                        exerciseId = exerciseId,
                        orderIndex = index,
                    )
                }
        }

        templateDao.replaceAll(templateEntities, templateExercises)
    }

    /**
     * Copies a template into the user's own workouts. Returns the new local
     * workout id, or null when the template no longer exists.
     */
    suspend fun importTemplate(templateId: Long): Long? {
        val template = templateDao.getByIdOnce(templateId) ?: return null
        val exerciseIds = templateDao.getExerciseIdsOnce(templateId)

        val workoutId = workoutDao.insert(
            WorkoutEntity(
                name = uniqueWorkoutName(template.name),
                notes = template.notes,
            )
        )
        if (exerciseIds.isNotEmpty()) {
            workoutExerciseDao.insertAll(
                exerciseIds.mapIndexed { index, exerciseId ->
                    WorkoutExerciseEntity(
                        workoutId = workoutId,
                        exerciseId = exerciseId,
                        orderIndex = index,
                    )
                }
            )
        }
        return workoutId
    }

    private suspend fun uniqueWorkoutName(baseName: String): String {
        if (!workoutDao.nameExists(baseName)) return baseName
        var suffix = 2
        while (workoutDao.nameExists("$baseName ($suffix)")) {
            suffix++
        }
        return "$baseName ($suffix)"
    }
}

private fun WorkoutTemplateExercise.exerciseIdOrNull(): Long? =
    (if (exerciseId != 0L) exerciseId else exercise?.exerciseId)?.takeIf { it != 0L }

internal data class DominantCategory(val name: String, val iconUrl: String?)

/**
 * Counts how many template exercises are covered by each standard category.
 * Every non-root category (the mapped category and its ancestors) is counted,
 * so broad categories like "Chest" win when their subcategories are used.
 */
internal fun dominantCategory(
    exerciseIds: List<Long>,
    linksByExercise: Map<Long, List<ExerciseCategoryEntity>>,
    categories: Map<Long, CategoryEntity>,
): DominantCategory? {
    if (exerciseIds.isEmpty()) return null

    val coverage = mutableMapOf<Long, MutableSet<Long>>()
    for (exerciseId in exerciseIds) {
        val visited = mutableSetOf<Long>()
        linksByExercise[exerciseId].orEmpty().forEach { link ->
            var category = categories[link.categoryId]
            // Roots are sports ("Gym", "Calisthenics", ...) and are too broad
            // to describe a workout, so they are not candidates.
            while (category != null && category.parentCategoryId != null) {
                if (visited.add(category.categoryId)) {
                    coverage.getOrPut(category.categoryId) { mutableSetOf() }.add(exerciseId)
                }
                category = categories[category.parentCategoryId]
            }
        }
    }

    val best = coverage.entries.maxWithOrNull(
        compareBy<Map.Entry<Long, Set<Long>>> { it.value.size }
            // With equal coverage prefer the broader category ("Chest" over
            // its equipment subcategory "Barbell").
            .thenByDescending { depth(it.key, categories) }
            .thenBy { it.key }
    ) ?: return null

    val category = categories[best.key] ?: return null
    return DominantCategory(
        name = category.name,
        iconUrl = closestIcon(category, categories),
    )
}

private fun closestIcon(
    category: CategoryEntity,
    categories: Map<Long, CategoryEntity>,
): String? {
    var current: CategoryEntity? = category
    val visited = mutableSetOf<Long>()
    while (current != null && visited.add(current.categoryId)) {
        current.iconUrl?.takeIf { it.isNotBlank() }?.let { return it }
        current = current.parentCategoryId?.let(categories::get)
    }
    return null
}

private fun depth(categoryId: Long, categories: Map<Long, CategoryEntity>): Int {
    var current = categories[categoryId]
    var depth = 0
    val visited = mutableSetOf<Long>()
    while (current != null && visited.add(current.categoryId)) {
        depth++
        current = current.parentCategoryId?.let(categories::get)
    }
    return depth
}
