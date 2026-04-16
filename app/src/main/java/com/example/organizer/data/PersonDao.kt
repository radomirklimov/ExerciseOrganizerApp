package com.example.organizer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {
    @Insert
    suspend fun insert(vararg people: Person)

    @Query("SELECT * FROM Person")
    fun getAll(): Flow<List<Person>>
}
