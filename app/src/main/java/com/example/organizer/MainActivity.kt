package com.example.organizer

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.organizer.data.Person
import com.example.organizer.data.PersonDao
import com.example.organizer.data.PersonDatabase
import com.example.organizer.ui.theme.ExerciseOrganizerAppTheme
import kotlin.math.log

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = PersonDatabase.get(this)
        val dao = db.dao

        //Insert
        dao.insert(
            Person(name = "Alice", age = 25),
            Person(name = "Bob", age = 30)
        )

        //Read
        val people = dao.getPeople()

        people.forEach {
            Log.d("DB", "${it.name} - ${it.age}")
        }

        setContent {
            OutputDB(dao)
        }

    }
}

@Composable
fun OutputDB(dao: PersonDao){
    val people = dao.getPeople()
    Column(modifier = Modifier.padding(16.dp)) {
        people.forEach {
            Text(text = "${it.name} - ${it.age}")
        }
    }
}