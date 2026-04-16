package com.example.organizer.data

import kotlinx.coroutines.flow.Flow

class PersonRepository(private val dao: PersonDao) {
    fun insert(person: Person) {
        dao.insert(person)
    }

    fun getAll(): Flow<List<Person>> {
        return dao.getAll()
    }
}
