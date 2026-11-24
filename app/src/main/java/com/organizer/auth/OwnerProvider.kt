package com.organizer.auth

import com.google.firebase.auth.FirebaseAuth
import javax.inject.Inject
import javax.inject.Singleton

/** Supplies the owner key for local user-owned data. */
@Singleton
class OwnerProvider @Inject constructor() {
    fun currentOwner(): String = runCatching {
        FirebaseAuth.getInstance().currentUser?.uid
    }.getOrNull() ?: GUEST

    companion object {
        const val GUEST = "guest"
        const val PUBLIC = "public"
    }
}