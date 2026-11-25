package com.organizer.sync

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves an ID token for the exact Firebase user a sync operation captured.
 * Ambient `currentUser` state is only consulted to confirm that user is still
 * signed in; the returned token always belongs to [owner].
 */
interface SyncTokenProvider {
    /** Returns null when [owner] is no longer the signed-in user. */
    suspend fun idTokenFor(owner: String): String?
}

@Singleton
class FirebaseSyncTokenProvider @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
) : SyncTokenProvider {
    override suspend fun idTokenFor(owner: String): String? = withContext(Dispatchers.IO) {
        val user = firebaseAuth.currentUser ?: return@withContext null
        if (user.uid != owner) return@withContext null
        runCatching { Tasks.await(user.getIdToken(false)).token }.getOrNull()
    }
}
