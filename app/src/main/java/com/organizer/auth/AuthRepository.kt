package com.organizer.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.organizer.constants.AppConfig
import com.organizer.sync.SyncWorker
import com.organizer.data.remote.UserApi
import com.organizer.data.remote.model.AppUserDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val userApi: UserApi,
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<UserState>(UserState.Guest)
    val state: StateFlow<UserState> = _state.asStateFlow()

    fun currentState(): UserState = _state.value

    init {
        firebaseAuth.addAuthStateListener { auth ->
            val user = auth.currentUser
            if (user == null) {
                _state.value = UserState.Guest
            } else {
                scope.launch { resolveBackendUser(user) }
            }
        }
        val current = firebaseAuth.currentUser
        if (current != null) {
            scope.launch { resolveBackendUser(current) }
        }
    }

    private suspend fun resolveBackendUser(user: FirebaseUser) {
        _state.value = UserState.Authenticating
        try {
            user.getIdToken(false).await()
            val dto: AppUserDto = userApi.me()
            _state.value = UserState.Authenticated(
                appUserId = dto.id,
                firebaseUid = user.uid,
                email = dto.email ?: user.email,
                displayName = dto.displayName ?: user.displayName,
            )
            scheduleSync()
        } catch (e: Exception) {
            _state.value = UserState.Guest
        }
    }

    suspend fun signInWithGoogle(activity: Activity): Result<Unit> {
        _state.value = UserState.Authenticating
        return try {
            val credentialManager = CredentialManager.create(activity)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(AppConfig.GOOGLE_WEB_CLIENT_ID)
                .setAutoSelectEnabled(false)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val result = credentialManager.getCredential(activity, request)
            val googleCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
            val firebaseCredential = GoogleAuthProvider.getCredential(googleCredential.idToken, null)
            firebaseAuth.signInWithCredential(firebaseCredential).await()
            Result.success(Unit)
        } catch (e: Exception) {
            _state.value = UserState.Guest
            Result.failure(e)
        }
    }

    private fun scheduleSync() {
        val oneTime = OneTimeWorkRequestBuilder<SyncWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "cloud-sync-now",
            ExistingWorkPolicy.KEEP,
            oneTime,
        )
        val periodic = PeriodicWorkRequestBuilder<SyncWorker>(15, java.util.concurrent.TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "cloud-sync-periodic",
            ExistingPeriodicWorkPolicy.KEEP,
            periodic,
        )
    }

    suspend fun signOut() {
        firebaseAuth.signOut()
        _state.value = UserState.Guest
    }
}