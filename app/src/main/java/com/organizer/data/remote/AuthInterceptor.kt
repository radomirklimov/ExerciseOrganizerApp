package com.organizer.data.remote

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class AuthInterceptor @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        // A caller performing an owner-bound sync already supplied the exact
        // credential for that operation. Never replace it from ambient state.
        if (original.header("Authorization") != null) return chain.proceed(original)
        val token = try {
            val task = firebaseAuth.currentUser?.getIdToken(false)
            val result = if (task != null) Tasks.await(task) else null
            result?.token
        } catch (e: Exception) {
            null
        }
        val request = if (token != null) {
            original.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            original
        }
        return chain.proceed(request)
    }
}