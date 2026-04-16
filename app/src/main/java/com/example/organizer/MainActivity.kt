package com.example.organizer

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import com.example.organizer.data.Person
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
    }
}