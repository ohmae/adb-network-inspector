package net.mm2d.inspector

import net.mm2d.inspector.model.NetworkEvent
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID

/**
 * 通信データをキャプチャし、InspectorServer に送信する OkHttp3 インターセプター。
 */
class OkHttpInspectorInterceptor : Interceptor {
    private val maxBodySize = 2 * 1024 * 1024 // 2MB

    @Throws(IOException::class)
    override fun intercept(
        chain: Interceptor.Chain,
    ): Response {
        val request = chain.request()
        val id = UUID.randomUUID().toString()
        val requestTime = System.currentTimeMillis()

        // リクエストボディの読み込み
        var requestBodyStr: String? = null
        val requestBody = request.body
        val requestBodySize = requestBody?.contentLength() ?: 0L

        if (requestBody != null && requestBodySize in 1..maxBodySize) {
            val buffer = Buffer()
            requestBody.writeTo(buffer)
            val contentType = requestBody.contentType()
            val charset = contentType?.charset(StandardCharsets.UTF_8) ?: StandardCharsets.UTF_8
            requestBodyStr = buffer.readString(charset)
        }

        val requestHeadersMap = request.headers.toMap()

        // Request イベントを送信
        InspectorServer.sendEvent(
            NetworkEvent.Request(
                id = id,
                timestamp = requestTime,
                url = request.url.toString(),
                method = request.method,
                headers = requestHeadersMap,
                body = requestBodyStr,
                bodySize = requestBodySize,
            ),
        )

        val response: Response
        try {
            response = chain.proceed(request)
        } catch (e: IOException) {
            // 通信エラー時も何らかの通知を送るか、そのままスローする
            // ここではログ送信後に再スロー
            throw e
        }

        val responseTime = System.currentTimeMillis()
        val durationMs = responseTime - requestTime

        // レスポンスボディの読み込み
        val responseBody = response.body
        var responseBodyStr: String? = null
        var isImage = false
        val contentType = responseBody.contentType()
        val contentTypeStr = contentType?.toString()
        var responseBodySize = 0L

        val source = responseBody.source()
        source.request(maxBodySize.toLong()) // 最大2MBまで読み込み
        val buffer = source.buffer.clone()
        responseBodySize = buffer.size

        if (buffer.size > 0) {
            isImage = contentTypeStr?.startsWith("image/") == true
            if (isImage) {
                val bytes = buffer.readByteArray()
                responseBodyStr = Base64.getEncoder().encodeToString(bytes)
            } else {
                val charset = contentType?.charset(StandardCharsets.UTF_8) ?: StandardCharsets.UTF_8
                responseBodyStr = buffer.readString(charset)
            }
        }

        val responseHeadersMap = response.headers.toMap()

        // Response イベントを送信
        InspectorServer.sendEvent(
            NetworkEvent.Response(
                id = id,
                timestamp = responseTime,
                code = response.code,
                message = response.message,
                headers = responseHeadersMap,
                body = responseBodyStr,
                bodySize = responseBodySize,
                durationMs = durationMs,
                isImage = isImage,
                contentType = contentTypeStr,
            ),
        )

        return response
    }

    private fun Headers.toMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (i in 0 until size) {
            map[name(i)] = value(i)
        }
        return map
    }
}
