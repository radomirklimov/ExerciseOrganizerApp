package com.organizer.sync

import com.organizer.data.remote.SyncApi
import com.organizer.data.remote.model.SyncChangesResponseDto
import com.organizer.data.remote.model.SyncPushResponseDto
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.create
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class SyncApiClientTest {

    private class FakeSyncApi : SyncApi {
        var pushBlock: suspend () -> SyncPushResponseDto = { error("not configured") }
        var changesBlock: suspend () -> SyncChangesResponseDto = { error("not configured") }

        override suspend fun push(request: com.organizer.data.remote.model.SyncPushRequestDto) = pushBlock()
        override suspend fun changes(cursor: String?, limit: Int) = changesBlock()
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun successPush() = SyncPushResponseDto(
        results = emptyList(),
        serverTime = "2026-11-24T10:00:00Z",
    )

    private fun apiClient(api: FakeSyncApi) = SyncApiClient(api, json)

    @Test
    fun `successful push is returned`() = runBlocking {
        val api = FakeSyncApi().apply { pushBlock = { successPush() } }
        val result = apiClient(api).push(
            com.organizer.data.remote.model.SyncPushRequestDto(emptyList())
        )
        assertTrue(result is SyncApiResult.Success)
        assertTrue((result as SyncApiResult.Success).data.results.isEmpty())
    }

    @Test
    fun `http error parses backend api error`() = runBlocking {
        val body = """{"error":"INVALID_REQUEST","message":"bad cursor"}"""
        val response = Response.error<SyncPushResponseDto>(
            400,
            create("application/json".toMediaType(), body),
        )
        val api = FakeSyncApi().apply { pushBlock = { throw HttpException(response) } }
        val result = apiClient(api).push(
            com.organizer.data.remote.model.SyncPushRequestDto(emptyList())
        )
        val error = result as SyncApiResult.HttpError
        assertEquals(400, error.code)
        assertEquals("INVALID_REQUEST", error.error)
        assertEquals("bad cursor", error.message)
    }

    @Test
    fun `unauthorized error is reported as http error`() = runBlocking {
        val response = Response.error<SyncChangesResponseDto>(
            401,
            create("application/json".toMediaType(), """{"error":"UNAUTHENTICATED","message":"Authentication required"}"""),
        )
        val api = FakeSyncApi().apply { changesBlock = { throw HttpException(response) } }
        val result = apiClient(api).changes(null, 100)
        val error = result as SyncApiResult.HttpError
        assertEquals(401, error.code)
        assertEquals("UNAUTHENTICATED", error.error)
    }

    @Test
    fun `network failure is reported as transport error`() = runBlocking {
        val api = FakeSyncApi().apply { pushBlock = { throw IOException("offline") } }
        val result = apiClient(api).push(
            com.organizer.data.remote.model.SyncPushRequestDto(emptyList())
        )
        assertTrue(result is SyncApiResult.TransportError)
        assertEquals("offline", (result as SyncApiResult.TransportError).message)
    }

    @Test
    fun `malformed response is reported safely`() = runBlocking {
        val api = FakeSyncApi().apply { pushBlock = { throw SerializationException("bad json") } }
        val result = apiClient(api).push(
            com.organizer.data.remote.model.SyncPushRequestDto(emptyList())
        )
        assertTrue(result is SyncApiResult.MalformedResponse)
        assertEquals("bad json", (result as SyncApiResult.MalformedResponse).message)
    }
}