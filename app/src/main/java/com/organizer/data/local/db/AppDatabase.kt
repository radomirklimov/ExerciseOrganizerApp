package com.organizer.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.organizer.data.local.dao.CategoryDao
import com.organizer.data.local.dao.ExerciseCategoryDao
import com.organizer.data.local.dao.ExerciseDao
import com.organizer.data.local.dao.ExerciseProgressDao
import com.organizer.data.local.dao.StarterTemplateDao
import com.organizer.data.local.dao.WorkoutDao
import com.organizer.data.local.dao.WorkoutExerciseDao
import com.organizer.data.local.db.entities.CategoryEntity
import com.organizer.data.local.db.entities.ExerciseCategoryEntity
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.local.db.entities.ExerciseProgressEntity
import com.organizer.data.local.db.entities.StarterTemplateEntity
import com.organizer.data.local.db.entities.StarterTemplateExerciseEntity
import com.organizer.data.local.db.entities.WorkoutEntity
import com.organizer.data.local.db.entities.WorkoutExerciseEntity
import com.organizer.data.mapper.ListConverters

@Database(
    version = 6,
    entities = [
        CategoryEntity::class,
        ExerciseEntity::class,
        ExerciseCategoryEntity::class,
        WorkoutEntity::class,
        WorkoutExerciseEntity::class,
        ExerciseProgressEntity::class,
        StarterTemplateEntity::class,
        StarterTemplateExerciseEntity::class
    ],
    exportSchema = false
)
@TypeConverters(ListConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract val categoryDao: CategoryDao
    abstract val exerciseDao: ExerciseDao
    abstract val exerciseCategoryDao: ExerciseCategoryDao
    abstract val workoutDao: WorkoutDao
    abstract val workoutExerciseDao: WorkoutExerciseDao
    abstract val exerciseProgressDao: ExerciseProgressDao
    abstract val starterTemplateDao: StarterTemplateDao
}

val STARTER_TEMPLATES_MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS starter_template (
                templateId INTEGER NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                notes TEXT NOT NULL,
                categoryName TEXT,
                iconUrl TEXT,
                exerciseCount INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS starter_template_exercise (
                templateId INTEGER NOT NULL,
                exerciseId INTEGER NOT NULL,
                orderIndex INTEGER NOT NULL,
                PRIMARY KEY (templateId, exerciseId),
                FOREIGN KEY (templateId) REFERENCES starter_template (templateId) ON DELETE CASCADE,
                FOREIGN KEY (exerciseId) REFERENCES exercise (exerciseId) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_starter_template_exercise_templateId ON starter_template_exercise (templateId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_starter_template_exercise_exerciseId ON starter_template_exercise (exerciseId)")
    }
}

val WORKOUT_NOTES_MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workout ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
    }
}

val EXERCISE_PROGRESS_MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS exercise_progress (
                progressId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                workoutId INTEGER NOT NULL,
                exerciseId INTEGER NOT NULL,
                setNumber INTEGER NOT NULL,
                reps INTEGER,
                weight REAL,
                durationSeconds INTEGER,
                recordedAt INTEGER NOT NULL,
                FOREIGN KEY (workoutId) REFERENCES workout (workoutId) ON DELETE CASCADE,
                FOREIGN KEY (exerciseId) REFERENCES exercise (exerciseId) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_exercise_progress_workoutId ON exercise_progress (workoutId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_exercise_progress_exerciseId ON exercise_progress (exerciseId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_exercise_progress_recordedAt ON exercise_progress (recordedAt)")
    }
}

val EXERCISE_TYPE_MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE exercise ADD COLUMN exerciseType TEXT NOT NULL DEFAULT 'REPS_WEIGHT'")
    }
}

val DISPLAY_ORDER_MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE category ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
        db.execSQL("""
            UPDATE category SET sortOrder = (
                SELECT COUNT(*) FROM category AS earlier
                WHERE earlier.categoryId < category.categoryId
                  AND (earlier.parentCategoryId = category.parentCategoryId
                       OR (earlier.parentCategoryId IS NULL AND category.parentCategoryId IS NULL))
            )
        """.trimIndent())
        db.execSQL("ALTER TABLE exercise_category ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
        db.execSQL("""
            UPDATE exercise_category SET sortOrder = (
                SELECT COUNT(*) FROM exercise_category AS earlier
                WHERE earlier.categoryId = exercise_category.categoryId
                  AND earlier.exerciseCategoryId < exercise_category.exerciseCategoryId
            )
        """.trimIndent())
    }
}
