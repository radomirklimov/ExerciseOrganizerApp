package com.organizer.data.remote.repo

import android.net.Uri
import android.util.Log
import com.organizer.constants.AppConfig
import com.organizer.data.remote.ApiService
import jakarta.inject.Inject

class FileRemoteDataSource @Inject constructor(
    private val api: ApiService
) {

    suspend fun downloadFile(
        subfolder: String,
        fileName: String
    ): ByteArray? {
        return try {
            // File names from the API may contain spaces (e.g. "Flat Bench Press.webp"),
            // so the path segment must be encoded for a valid Supabase Storage URL.
            val url = "${AppConfig.SUPABASE_STORAGE_BASE_URL}/$subfolder/${Uri.encode(fileName)}"

            val response = api.downloadFile(url)

            if (response.isSuccessful) {
                response.body()?.bytes()
            } else {
                null
            }

        } catch (e: Exception) {
            Log.e("FILES", "Failed downloading image", e)
            null
        }
    }
}
