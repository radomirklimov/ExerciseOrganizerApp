package com.organizer.data.remote.model

import kotlinx.serialization.Serializable

@Serializable
data class AppUserDto(
    val id: String,
    val firebaseUid: String? = null,
    val email: String? = null,
    val displayName: String? = null,
    val createdAt: String? = null,
)