package com.example.organizer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    version = 1,
    entities = [
        Person::class
    ],
    exportSchema = false
)
abstract class PersonDatabase : RoomDatabase() {
    abstract val dao: PersonDao

    companion object {
        @Volatile private var INSTANCE: PersonDatabase? = null

        fun get(context: Context): PersonDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PersonDatabase::class.java,
                    "person_db"
                )
                    .allowMainThreadQueries() // ⚠️ only for learning / prototypes
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}