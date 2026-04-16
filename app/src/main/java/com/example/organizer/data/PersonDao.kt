package com.example.organizer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PersonDao {
    @Insert
    fun insert(vararg people: Person)

    @Query("SELECT * FROM Person")
    fun getPeople(): List<Person>
}