package com.organizer.sync

import com.organizer.data.remote.SyncApi
import com.organizer.data.remote.model.ApiErrorDto
import com.organizer.data.remote.model.SyncChangesResponseDto
import com.organizer.data.remote.model.SyncPushRequestDto
import com.organizer.data.remote.model.SyncPushResponseDto
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SyncApiResult<out T> {
    data class Success<T>(val data: T) : SyncApiResult<T>
    data class HttpError(val code: Int, val error: String?, val message: String?) : SyncApiResult<Nothing>
    data class TransportError(val message: String?) : SyncApiResult<Nothing>
    data class MalformedResponse(val message: String?) : SyncApiResult<Nothing>
}

@Singleton
class SyncApiClient @Inject constructor(
    private val syncApi: SyncApi,
    private val json: Json,
) {
    suspend fun push(
        request: SyncPushRequestDto,
        authorization: String,
    ): SyncApiResult<SyncPushResponseDto> = safeCall {
        syncApi.push(request, authorization)
    }

    suspend fun changes(
        cursor: String?,
        limit: Int,
        authorization: String,
    ): SyncApiResult<SyncChangesResponseDto> = safeCall {
        syncApi.changes(cursor, limit, authorization)
    }

    companion object {
        /** Structured backend error code for an expired, otherwise-valid cursor. */
        const val CURSOR_EXPIRED = "CURSOR_EXPIRED"
    }

    private suspend inline fun <reified T> safeCall(crossinline call: suspend () -> T): SyncApiResult<T> =
        try {
            SyncApiResult.Success(call())
        } catch (ex: HttpException) {
            val body = ex.response()?.errorBody()?.string()
            val apiError = body?.let { runCatching { json.decodeFromString<ApiErrorDto>(it) }.getOrNull() }
            SyncApiResult.HttpError(ex.code(), apiError?.error, apiError?.message)
        } catch (ex: IOException) {
            SyncApiResult.TransportError(ex.message)
        } catch (ex: SerializationException) {
            SyncApiResult.MalformedResponse(ex.message)
        } catch (ex: IllegalArgumentException) {
            SyncApiResult.MalformedResponse(ex.message)
        }
}