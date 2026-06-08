package net.mm2d.inspector

import kotlinx.serialization.json.Json
import net.mm2d.inspector.model.NetworkEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NetworkEvent のシリアライズ/デシリアライズ機能の動作確認テスト。
 */
class NetworkEventSerializationTest {

    @Test
    fun testRequestSerialization() {
        val request = NetworkEvent.Request(
            id = "test-uuid-1",
            timestamp = 1234567890L,
            url = "https://example.com/api",
            method = "GET",
            headers = mapOf("Accept" to "application/json"),
            body = "test-body",
            bodySize = 9L,
        )

        val jsonStr = Json.encodeToString<NetworkEvent>(request)
        val decoded = Json.decodeFromString<NetworkEvent>(jsonStr)

        assertTrue(decoded is NetworkEvent.Request)
        val decodedReq = decoded as NetworkEvent.Request
        assertEquals("test-uuid-1", decodedReq.id)
        assertEquals(1234567890L, decodedReq.timestamp)
        assertEquals("https://example.com/api", decodedReq.url)
        assertEquals("GET", decodedReq.method)
        assertEquals("application/json", decodedReq.headers["Accept"])
        assertEquals("test-body", decodedReq.body)
        assertEquals(9L, decodedReq.bodySize)
    }

    @Test
    fun testResponseSerialization() {
        val response = NetworkEvent.Response(
            id = "test-uuid-2",
            timestamp = 1234567895L,
            code = 200,
            message = "OK",
            headers = mapOf("Content-Type" to "application/json"),
            body = "{\"success\":true}",
            bodySize = 16L,
            durationMs = 50L,
            isImage = false,
            contentType = "application/json",
        )

        val jsonStr = Json.encodeToString<NetworkEvent>(response)
        val decoded = Json.decodeFromString<NetworkEvent>(jsonStr)

        assertTrue(decoded is NetworkEvent.Response)
        val decodedRes = decoded as NetworkEvent.Response
        assertEquals("test-uuid-2", decodedRes.id)
        assertEquals(200, decodedRes.code)
        assertEquals("OK", decodedRes.message)
        assertEquals("{\"success\":true}", decodedRes.body)
        assertEquals(50L, decodedRes.durationMs)
        assertEquals(false, decodedRes.isImage)
        assertEquals("application/json", decodedRes.contentType)
    }
}
