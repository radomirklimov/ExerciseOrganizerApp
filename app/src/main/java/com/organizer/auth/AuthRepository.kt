package com.organizer.auth

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.credentials.CredentialManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.credentials.GetCredentialRequest
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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
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
    private val prefs: SharedPreferences =
        context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

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
        // Restore persisted state hint (Firebase already restores session).
        val current = firebaseAuth.currentUser
        if (current != null) {
            scope.launch { resolveBackendUser(current) }
        }
    }

    private suspend fun resolveBackendUser(user: FirebaseUser) {
        _state.value = UserState.Authenticating
        try {
            val tokenResult = user.getIdToken(false).await()
            // Token is attached by interceptor; nothing to store.
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

    suspend fun sendSignInLink(email: String): Result<Unit> {
        return try {
            val settings = com.google.firebase.auth.ActionCodeSettings.newBuilder()
                .setUrl(AppConfig.EMAIL_SIGN_IN_CONTINUE_URL)
                .setHandleCodeInApp(true)
                .setAndroidPackageName(context.packageName, true, null)
                .build()
            firebaseAuth.sendSignInLinkToEmail(email, settings).await()
            prefs.edit().putString("email_for_sign_in", email).apply()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun isSignInWithEmailLink(link: String): Boolean =
        firebaseAuth.isSignInWithEmailLink(link)

    suspend fun completeSignInWithEmailLink(email: String?, link: String): Result<Unit> {
        _state.value = UserState.Authenticating
        return try {
            val resolvedEmail = email ?: prefs.getString("email_for_sign_in", null)
            if (resolvedEmail.isNullOrBlank()) {
                _state.value = UserState.Guest
                return Result.failure(IllegalStateException("Email required to complete sign-in"))
            }
            firebaseAuth.signInWithEmailLink(resolvedEmail, link).await()
            prefs.edit().remove("email_for_sign_in").apply()
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

    fun rememberedEmail(): String? = prefs.getString("email_for_sign_in", null)

    fun handleEmailLink(activity: Activity, intent: android.content.Intent?) {
        val link = intent?.data?.toString() ?: return
        if (!firebaseAuth.isSignInWithEmailLink(link)) return
        scope.launch {
            val email = rememberedEmail()
            completeSignInWithEmailLink(email, link)
        }
    }
}