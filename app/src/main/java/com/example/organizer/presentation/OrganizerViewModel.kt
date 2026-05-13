package com.example.organizer.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.organizer.network.Api
import kotlinx.coroutines.launch

class OrganizerViewModel: ViewModel() {
    var categoriesUiState: String by mutableStateOf("")
        private set

    init {
        getCategories()
    }

    fun getCategories() {
        viewModelScope.launch {
            categoriesUiState = Api.retrofitService.getCategories()
        }
    }
}
