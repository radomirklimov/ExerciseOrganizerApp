package com.example.organizer.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity()
data class Person(
    @PrimaryKey var id: String = UUID.randomUUID().toString(),
    var name: String,
    var age: Int,
)