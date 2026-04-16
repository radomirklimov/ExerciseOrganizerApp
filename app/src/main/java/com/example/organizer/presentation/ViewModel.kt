package com.example.organizer.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.organizer.data.Person
import com.example.organizer.data.PersonDatabase
import com.example.organizer.data.PersonRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class ViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: PersonRepository
    val readAll: Flow<List<Person>>

    init {
        val db = PersonDatabase.get(application).dao
        repository = PersonRepository(db)
        readAll = repository.getAll()
    }

    fun addNew(person: Person) {
        viewModelScope.launch() {
            repository.insert(person)
        }
    }
}
