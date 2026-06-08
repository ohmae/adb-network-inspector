package net.mm2d.inspector.desktop.client

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.mm2d.inspector.model.NetworkEvent

/**
 * Android 側の WebSocket サーバーに接続し、受信した通信ログイベントを保持するクライアント。
 */
class InspectorClient(
    private val host: String = "localhost",
    private val port: Int = 8082,
) {
    private val client = HttpClient {
        install(WebSockets)
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _events = MutableStateFlow<List<NetworkEvent>>(emptyList())
    val events: StateFlow<List<NetworkEvent>> = _events

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        object Connected : ConnectionState()
    }

    /**
     * WebSocket 接続ループを開始します。
     * 自動的に adb forward の実行を試み、切断時は一定時間後に再接続を試行します。
     */
    fun start() {
        if (job != null) return
        job = scope.launch {
            while (isActive) {
                _connectionState.value = ConnectionState.Connecting

                // adb forward の実行を試みる (デスクトップの port から Android の port へ転送)
                try {
                    val process = Runtime.getRuntime().exec(arrayOf("adb", "forward", "tcp:$port", "tcp:$port"))
                    process.waitFor()
                } catch (e: Exception) {
                    System.err.println("adb forward 実行失敗: ${e.message} (adb が PATH に通っていない可能性があります)")
                }

                try {
                    client.webSocket(host = host, port = port, path = "/inspector") {
                        _connectionState.value = ConnectionState.Connected
                        println("Android アプリのインスペクターに接続しました！")

                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val text = frame.readText()
                                try {
                                    val event = Json.decodeFromString<NetworkEvent>(text)
                                    _events.value = _events.value + event
                                } catch (e: Exception) {
                                    System.err.println("受信イベントのパースに失敗しました: ${e.message}")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    System.err.println("接続に失敗したか切断されました: ${e.message}。2秒後に再試行します...")
                }

                _connectionState.value = ConnectionState.Disconnected
                delay(2000)
            }
        }
    }

    /**
     * 接続ループを停止します。
     */
    fun stop() {
        job?.cancel()
        job = null
        _connectionState.value = ConnectionState.Disconnected
    }

    /**
     * キャッシュされたイベントリストをクリアします。
     */
    fun clearEvents() {
        _events.value = emptyList()
    }
}
