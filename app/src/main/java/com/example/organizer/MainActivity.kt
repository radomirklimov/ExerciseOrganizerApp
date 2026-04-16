package com.example.organizer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.organizer.data.Person
import com.example.organizer.presentation.ViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: ViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OutputDB(viewModel)
        }
    }
}

@Composable
fun OutputDB(viewModel: ViewModel) {
    val people by viewModel.readAll.collectAsState(initial = emptyList())

    Column(modifier = Modifier.padding(64.dp)) {
        people.forEach {
            Text(text = "${it.name} - ${it.age}")
        }

        Button(
            onClick = {
                viewModel.addNew(Person(name = "New Person", age = 20))
            }
        ) {
            Text("Add Person")
        }
    }
}