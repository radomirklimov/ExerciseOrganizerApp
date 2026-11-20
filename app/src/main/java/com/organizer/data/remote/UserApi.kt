package com.organizer.data.remote

import com.organizer.data.remote.model.AppUserDto
import retrofit2.http.GET

interface UserApi {
    @GET("api/users/me")
    suspend fun me(): AppUserDto
}