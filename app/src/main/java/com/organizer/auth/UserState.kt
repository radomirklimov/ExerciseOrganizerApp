package com.organizer.auth

sealed interface UserState {
    data object Guest : UserState
    data object Authenticating : UserState
    data class Authenticated(
        val appUserId: String,
        val firebaseUid: String,
        val email: String?,
        val displayName: String?,
    ) : UserState
}