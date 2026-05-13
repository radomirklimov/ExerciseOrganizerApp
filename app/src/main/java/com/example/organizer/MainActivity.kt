package com.example.organizer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.organizer.presentation.OrganizerViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    private val viewModel: OrganizerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GetAllCategories()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GetAllCategories() {
    val viewModel: OrganizerViewModel = viewModel()
    Text(text = viewModel.categoriesUiState)
}
