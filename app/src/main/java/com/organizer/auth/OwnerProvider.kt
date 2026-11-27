package com.organizer.auth

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Supplies the owner key for local user-owned data. */
@Singleton
open class OwnerProvider @Inject constructor() {
    private val _owner = MutableStateFlow(GUEST)

    init {
        runCatching {
            FirebaseAuth.getInstance().addAuthStateListener { auth ->
                _owner.value = auth.currentUser?.uid ?: GUEST
            }
        }
    }

    /** Owner key updates when the authenticated account changes. */
    open fun observeOwner(): Flow<String> = _owner.asStateFlow()

    open fun currentOwner(): String = runCatching {
        FirebaseAuth.getInstance().currentUser?.uid
    }.getOrNull() ?: GUEST

    companion object {
        const val GUEST = "guest"
        const val PUBLIC = "public"
    }
}