package com.example.organizer

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import com.example.organizer.data.PersonDao
import com.example.organizer.data.PersonDatabase
import com.example.organizer.data.PersonRepository

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = PersonDatabase.get(this)
        val dao = db.dao

        setContent {
            AddNewPerson(dao)
            OutputDB(dao)
        }
    }
}

@Composable
fun AddNewPerson(dao: PersonDao) {
    val repo = PersonRepository(dao)

    Button(onClick = { repo.insert(Person(name = "Mandi", age = 18)) }) {
        Text(
            "Add New Person",
            modifier = Modifier.padding(8.dp)
        )
    }
}

@Composable
fun OutputDB(dao: PersonDao) {
    val repo = PersonRepository(dao)
    val people by repo.getAll().collectAsState(initial = emptyList())

    Column(modifier = Modifier.padding(64.dp)) {
        people.forEach {
            Text(text = "${it.name} - ${it.age}")
        }
    }
}