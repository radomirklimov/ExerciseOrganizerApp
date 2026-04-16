package com.example.organizer.data

import kotlinx.coroutines.flow.Flow

class PersonRepository(private val dao: PersonDao) {
    suspend fun insert(person: Person) {
        dao.insert(person)
    }

    fun getAll(): Flow<List<Person>> {
        return dao.getAll()
    }
}
