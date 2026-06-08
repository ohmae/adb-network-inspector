package net.mm2d.inspector.model

import kotlinx.serialization.Serializable

/**
 * Androidアプリとデスクトップアプリ間で送受信される通信イベントデータモデル。
 */
@Serializable
sealed class NetworkEvent {
    abstract val id: String
    abstract val timestamp: Long

    /**
     * HTTP リクエスト情報
     */
    @Serializable
    data class Request(
        override val id: String,
        override val timestamp: Long,
        val url: String,
        val method: String,
        val headers: Map<String, String>,
        val body: String? = null,
        val bodySize: Long = 0,
    ) : NetworkEvent()

    /**
     * HTTP レスポンス情報
     */
    @Serializable
    data class Response(
        override val id: String,
        override val timestamp: Long,
        val code: Int,
        val message: String,
        val headers: Map<String, String>,
        val body: String? = null, // テキスト、またはBase64エンコードされたバイナリ
        val bodySize: Long = 0,
        val durationMs: Long,
        val isImage: Boolean = false,
        val contentType: String? = null,
    ) : NetworkEvent()
}
