package net.mm2d.inspector

import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.mm2d.inspector.model.NetworkEvent
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Android アプリ内で動作し、収集した通信データを PC のデスクトップアプリへ送信する WebSocket サーバー。
 */
object InspectorServer {
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private val sessions = Collections.newSetFromMap(ConcurrentHashMap<DefaultWebSocketServerSession, Boolean>())
    private val eventCache = CopyOnWriteArrayList<NetworkEvent>()
    private const val MAX_CACHE_SIZE = 100
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * WebSocket サーバーを指定されたポートで起動します。
     */
    fun start(
        port: Int = 8082,
    ) {
        if (server != null) return

        server = embeddedServer(Netty, port = port) {
            install(WebSockets)
            routing {
                webSocket("/inspector") {
                    sessions.add(this)
                    try {
                        // 新規接続時に、キャッシュされている通信ログを順次すべて送信する
                        for (event in eventCache) {
                            sendEventDirect(this, event)
                        }
                        // クライアントとの接続を維持
                        for (frame in incoming) {
                            // クライアントからの受信データ処理（今回は特になし）
                        }
                    } catch (e: Exception) {
                        // エラーハンドリング（切断など）
                    } finally {
                        sessions.remove(this)
                    }
                }
            }
        }.apply {
            start(wait = false)
        }
    }

    /**
     * WebSocket サーバーを停止します。
     */
    fun stop() {
        server?.stop(500, 1000)
        server = null
        sessions.clear()
    }

    /**
     * 新しい通信ログイベントをキャッシュに追加し、接続中のクライアントにブロードキャストします。
     */
    fun sendEvent(
        event: NetworkEvent,
    ) {
        synchronized(eventCache) {
            eventCache.add(event)
            if (eventCache.size > MAX_CACHE_SIZE) {
                eventCache.removeAt(0)
            }
        }

        scope.launch {
            sessions.forEach { session ->
                try {
                    sendEventDirect(session, event)
                } catch (e: Exception) {
                    // 送信失敗時のエラーは無視（接続切れなど）
                }
            }
        }
    }

    private suspend fun sendEventDirect(
        session: DefaultWebSocketServerSession,
        event: NetworkEvent,
    ) {
        val jsonStr = Json.encodeToString(event)
        session.send(Frame.Text(jsonStr))
    }
}
