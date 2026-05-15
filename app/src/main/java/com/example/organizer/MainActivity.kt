package com.example.organizer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

@Composable
fun GetAllCategories() {
    val viewModel: OrganizerViewModel = viewModel()
    val categories = viewModel.categoriesUiState

    when {
        categories.isEmpty() -> {
            Text("Loading...")
        }

        else -> {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("There are ${categories.size} categories")
                Text("First category: ${categories.first().name}")
            }
        }
    }
}
