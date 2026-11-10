package com.organizer.data.remote.model

import kotlinx.serialization.Serializable

@Serializable
data class Exercise (
    val exerciseId: Long,
    val name: String,
    val instructions: List<String>,
    val images: List<String>,
    val exerciseType: String = "REPS_WEIGHT",
)
