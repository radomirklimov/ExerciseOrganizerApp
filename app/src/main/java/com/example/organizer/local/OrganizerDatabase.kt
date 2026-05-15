package com.example.organizer.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.organizer.local.dao.CategoryDao
import com.example.organizer.local.entity.CategoryEntity

@Database(
    version = 1,
    entities = [
        CategoryEntity::class
    ],
    exportSchema = false
)
abstract class OrganizerDatabase : RoomDatabase() {
    abstract val dao: CategoryDao

    companion object {
        @Volatile private var INSTANCE: OrganizerDatabase? = null

        fun get(context: Context): OrganizerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OrganizerDatabase::class.java,
                    "organizer_db"
                )
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}