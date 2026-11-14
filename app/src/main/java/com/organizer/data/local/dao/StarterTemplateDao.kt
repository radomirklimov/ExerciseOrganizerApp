package com.organizer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.local.db.entities.StarterTemplateEntity
import com.organizer.data.local.db.entities.StarterTemplateExerciseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StarterTemplateDao {

    @Query("SELECT * FROM starter_template ORDER BY templateId ASC")
    fun observeAll(): Flow<List<StarterTemplateEntity>>

    @Query("SELECT * FROM starter_template WHERE templateId = :id")
    fun observeById(id: Long): Flow<StarterTemplateEntity?>

    @Query(
        """SELECT e.* FROM exercise e
        JOIN starter_template_exercise ste ON e.exerciseId = ste.exerciseId
        WHERE ste.templateId = :templateId
        ORDER BY ste.orderIndex ASC"""
    )
    fun observeExercises(templateId: Long): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM starter_template WHERE templateId = :id")
    suspend fun getByIdOnce(id: Long): StarterTemplateEntity?

    @Query(
        """SELECT exerciseId FROM starter_template_exercise
        WHERE templateId = :templateId
        ORDER BY orderIndex ASC"""
    )
    suspend fun getExerciseIdsOnce(templateId: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplates(templates: List<StarterTemplateEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercises(exercises: List<StarterTemplateExerciseEntity>)

    @Query("DELETE FROM starter_template_exercise")
    suspend fun deleteAllExercises()

    @Query("DELETE FROM starter_template")
    suspend fun deleteAllTemplates()

    /**
     * Templates are small and only refreshed at startup; replacing them keeps
     * the local copy exactly in sync with the API.
     */
    @Transaction
    suspend fun replaceAll(
        templates: List<StarterTemplateEntity>,
        exercises: List<StarterTemplateExerciseEntity>,
    ) {
        deleteAllExercises()
        deleteAllTemplates()
        if (templates.isNotEmpty()) insertTemplates(templates)
        if (exercises.isNotEmpty()) insertExercises(exercises)
    }
}
