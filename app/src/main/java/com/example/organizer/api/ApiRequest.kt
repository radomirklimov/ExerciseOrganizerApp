package com.example.organizer.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class ApiRequest {

    val baseUrl = "http://10.0.2.2:8080/"

    suspend fun healthCheck(): Int = withContext(Dispatchers.IO){
        val request = Request.Builder()
            .url(baseUrl + "actuator/health")
            .build()

        OkHttpClient().newCall(request).execute().code
    }
}