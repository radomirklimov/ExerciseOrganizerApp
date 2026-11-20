package com.organizer.data.remote

import com.organizer.data.remote.model.SyncChangesResponseDto
import com.organizer.data.remote.model.SyncPushRequestDto
import com.organizer.data.remote.model.SyncPushResponseDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface SyncApi {
    @GET("api/sync/changes")
    suspend fun changes(@Query("since") since: String? = null): SyncChangesResponseDto

    @POST("api/sync/push")
    suspend fun push(@Body request: SyncPushRequestDto): SyncPushResponseDto
}