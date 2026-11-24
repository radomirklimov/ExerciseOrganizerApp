package com.organizer.sync

import com.organizer.auth.OwnerProvider
import com.organizer.data.local.db.entities.SyncOutboxEntity
import com.organizer.data.remote.model.ApiErrorDto
import com.organizer.data.remote.model.SyncChangesResponseDto
import com.organizer.data.remote.model.SyncMutationRequestDto
import com.organizer.data.remote.model.SyncPushRequestDto
import com.organizer.data.remote.model.SyncPushResponseDto
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class SyncContractMapperTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val mapper = SyncContractMapper()
    private val owner = "user-a"

    private fun outbox(
        entityType: String = SyncEntityTypes.WORKOUT,
        operation: String = "UPSERT",
        payload: String? = """{"name":"Push Day","notes":""}""",
        ownerUid: String = owner,
        createdAt: Long = 1_700_000_000_000L,
        baseVersion: Long = 0,
    ) = SyncOutboxEntity(
        mutationId = UUID.randomUUID().toString(),
        ownerFirebaseUid = ownerUid,
        entityType = entityType,
        recordSyncId = UUID.randomUUID().toString(),
        operation = operation,
        payload = payload,
        baseVersion = baseVersion,
        createdAt = createdAt,
    )

    @Test
    fun `push request matches backend JSON contract`() {
        val mutation = SyncMutationRequestDto(
            mutationId = "11111111-1111-1111-1111-111111111111",
            entityType = "WORKOUT",
            syncId = "22222222-2222-2222-2222-222222222222",
            operation = "UPSERT",
            baseVersion = 0,
            clientUpdatedAt = "2023-11-14T22:13:20.000Z",
            payload = json.parseToJsonElement("""{"name":"Push Day"}""") as JsonObject,
        )
        val encoded = json.encodeToString(SyncPushRequestDto(listOf(mutation)))
        val root = json.parseToJsonElement(encoded).jsonObject
        val item = root["mutations"]!!.jsonArray.first().jsonObject

        assertEquals(setOf("mutations"), root.keys)
        assertEquals(
            setOf(
                "mutationId", "entityType", "syncId", "operation",
                "baseVersion", "clientUpdatedAt", "payload",
            ),
            item.keys,
        )
        assertEquals("WORKOUT", item["entityType"]?.toString()?.trim('"'))
        assertEquals("UPSERT", item["operation"]?.toString()?.trim('"'))
        assertEquals("0", item["baseVersion"]?.toString())
        assertEquals("\"2023-11-14T22:13:20.000Z\"", item["clientUpdatedAt"]?.toString())
        assertEquals("Push Day", item["payload"]!!.jsonObject["name"]?.toString()?.trim('"'))
    }

    @Test
    fun `push response deserializes all result statuses`() {
        val body = """
            {
              "results": [
                {"mutationId":"m1","syncId":"s1","status":"APPLIED","serverVersion":1,"serverUpdatedAt":"2026-11-24T10:00:00.123Z","deleted":false},
                {"mutationId":"m2","syncId":"s2","status":"DUPLICATE","serverVersion":1,"serverUpdatedAt":"2026-11-24T10:00:01Z","deleted":false},
                {"mutationId":"m3","syncId":"s3","status":"CONFLICT","serverVersion":4,"serverUpdatedAt":"2026-11-24T10:00:02Z","deleted":false,"serverPayload":{"name":"Server"},"errorCode":"VERSION_CONFLICT","errorMessage":"stale"},
                {"mutationId":"m4","syncId":"s4","status":"REJECTED","errorCode":"INVALID_ENTITY_TYPE","errorMessage":"bad type"}
              ],
              "serverTime":"2026-11-24T10:00:03Z"
            }
        """.trimIndent()
        val response = json.decodeFromString<SyncPushResponseDto>(body)

        val outcomes = response.results.map(mapper::toOutcome)
        assertTrue(outcomes[0] is SyncMutationOutcome.Applied)
        assertTrue(outcomes[1] is SyncMutationOutcome.Duplicate)
        val conflict = outcomes[2] as SyncMutationOutcome.Conflict
        assertEquals(4L, conflict.serverVersion)
        assertEquals("Server", conflict.serverPayload?.get("name")?.toString()?.trim('"'))
        assertEquals("VERSION_CONFLICT", conflict.errorCode)
        assertTrue(outcomes[3] is SyncMutationOutcome.Rejected)
    }

    @Test
    fun `api error deserializes`() {
        val error = json.decodeFromString<ApiErrorDto>("""{"error":"INVALID_REQUEST","message":"bad cursor"}""")
        assertEquals("INVALID_REQUEST", error.error)
        assertEquals("bad cursor", error.message)
    }

    @Test
    fun `outbox maps for every supported entity type`() {
        SyncEntityTypes.ALL.forEach { entityType ->
            val payload = if (entityType == SyncEntityTypes.CUSTOM_EXERCISE) {
                """{"name":"Custom","isCustom":true}"""
            } else {
                """{"name":"Record"}"""
            }
            val result = mapper.toMutation(outbox(entityType = entityType, payload = payload), owner)
            assertTrue("$entityType should map", result is OutboxMappingResult.Success)
            val request = (result as OutboxMappingResult.Success).request
            assertEquals(entityType, request.entityType)
            assertEquals("UPSERT", request.operation)
            assertEquals("2023-11-14T22:13:20.000Z", request.clientUpdatedAt)
        }
    }

    @Test
    fun `outbox maps delete and restore operations`() {
        listOf("UPSERT", "DELETE", "RESTORE").forEach { operation ->
            val result = mapper.toMutation(outbox(operation = operation), owner)
            assertTrue(result is OutboxMappingResult.Success)
            assertEquals(operation, (result as OutboxMappingResult.Success).request.operation)
        }
    }

    @Test
    fun `outbox mapping rejects cross owner and guest mutations`() {
        assertTrue(mapper.toMutation(outbox(ownerUid = "user-b"), owner) is OutboxMappingResult.Failure)
        assertTrue(mapper.toMutation(outbox(ownerUid = OwnerProvider.GUEST), OwnerProvider.GUEST) is OutboxMappingResult.Failure)
    }

    @Test
    fun `outbox mapping rejects malformed input`() {
        assertTrue(mapper.toMutation(outbox(payload = "not json"), owner) is OutboxMappingResult.Failure)
        assertTrue(mapper.toMutation(outbox(payload = """["array"]"""), owner) is OutboxMappingResult.Failure)
        assertTrue(mapper.toMutation(outbox(entityType = "UNKNOWN"), owner) is OutboxMappingResult.Failure)
        assertTrue(mapper.toMutation(outbox(operation = "PATCH"), owner) is OutboxMappingResult.Failure)
    }

    @Test
    fun `mapper does not invent custom exercise payload schema`() {
        val result = mapper.toMutation(
            outbox(
                entityType = SyncEntityTypes.CUSTOM_EXERCISE,
                payload = """{"name":"Exercise"}""",
            ),
            owner,
        )
        assertTrue(result is OutboxMappingResult.Success)
    }

    @Test
    fun `pull response preserves opaque cursor and change data`() {
        val body = """
            {
              "records": [
                {"entityType":"WORKOUT","syncId":"33333333-3333-3333-3333-333333333333","serverVersion":2,"serverUpdatedAt":"2026-11-24T10:00:00.456789Z","deleted":true,"payload":{"name":"Deleted"}}
              ],
              "nextCursor":"opaque.cursor-with+chars/123=",
              "hasMore":true,
              "serverTime":"2026-11-24T10:00:01Z"
            }
        """.trimIndent()
        val dto = json.decodeFromString<SyncChangesResponseDto>(body)
        val changes = mapper.toChanges(dto).getOrThrow()

        assertEquals("opaque.cursor-with+chars/123=", changes.nextCursor)
        assertTrue(changes.hasMore)
        val record = changes.records.single()
        assertEquals("WORKOUT", record.entityType)
        assertEquals(2L, record.serverVersion)
        assertTrue(record.deleted)
        assertEquals("Deleted", record.payload?.get("name")?.toString()?.trim('"'))
        assertTrue(record.serverUpdatedAtMillis > 0)
    }

    @Test
    fun `timestamps handle fractional seconds and format in UTC millis`() {
        assertTrue(SyncTimestamps.parse("2026-11-24T10:00:00Z") != null)
        assertTrue(SyncTimestamps.parse("2026-11-24T10:00:00.123456789Z") != null)
        assertEquals("2023-11-14T22:13:20.000Z", SyncTimestamps.format(1_700_000_000_000L))
        assertFalse(SyncTimestamps.parse("not-a-timestamp") != null)
    }

    @Test
    fun `delete mutation with null payload is allowed`() {
        val result = mapper.toMutation(
            outbox(operation = "DELETE", payload = null),
            owner,
        )
        assertTrue(result is OutboxMappingResult.Success)
        val request = (result as OutboxMappingResult.Success).request
        assertEquals("DELETE", request.operation)
        assertEquals(null, request.payload)
    }

    @Test
    fun `null payload is omitted from serialized request`() {
        val mutation = SyncMutationRequestDto(
            mutationId = UUID.randomUUID().toString(),
            entityType = "WORKOUT",
            syncId = UUID.randomUUID().toString(),
            operation = "DELETE",
            baseVersion = 1,
            clientUpdatedAt = "2026-11-24T10:00:00Z",
            payload = null,
        )
        val encoded = json.encodeToString(SyncPushRequestDto(listOf(mutation)))
        val item = json.parseToJsonElement(encoded).jsonObject["mutations"]!!.jsonArray.first().jsonObject
        assertFalse(item.containsKey("payload"))
    }

    @Test
    fun `missing required field is rejected during deserialization`() {
        val body = """{"results":[{"mutationId":"m1","syncId":"s1"}],"serverTime":"2026-11-24T10:00:00Z"}"""
        assertThrows(SerializationException::class.java) {
            json.decodeFromString<SyncPushResponseDto>(body)
        }
    }

    @Test
    fun `unknown result status is mapped safely`() {
        val body = """{"mutationId":"m1","syncId":"s1","status":"UNKNOWN"}"""
        val dto = json.decodeFromString<com.organizer.data.remote.model.SyncMutationResultDto>(body)
        val outcome = mapper.toOutcome(dto)
        assertTrue(outcome is SyncMutationOutcome.Rejected)
        assertEquals("UNKNOWN_STATUS", (outcome as SyncMutationOutcome.Rejected).errorCode)
    }

    @Test
    fun `malformed change timestamp fails closed`() {
        val body = """
            {
              "records": [
                {"entityType":"WORKOUT","syncId":"33333333-3333-3333-3333-333333333333","serverVersion":1,"serverUpdatedAt":"not-a-time","deleted":false,"payload":{}}
              ],
              "nextCursor":"opaque",
              "hasMore":false,
              "serverTime":"2026-11-24T10:00:00Z"
            }
        """.trimIndent()
        val dto = json.decodeFromString<SyncChangesResponseDto>(body)
        assertTrue(mapper.toChanges(dto).isFailure)
    }
}