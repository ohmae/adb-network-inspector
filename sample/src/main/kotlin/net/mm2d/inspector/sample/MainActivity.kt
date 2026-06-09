package net.mm2d.inspector.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.mm2d.inspector.InspectorServer
import net.mm2d.inspector.OkHttpInspectorInterceptor
import net.mm2d.inspector.sample.ui.theme.AppTheme
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class MainActivity : ComponentActivity() {

    private val okHttpClient = OkHttpClient.Builder()
        .addNetworkInterceptor(OkHttpInspectorInterceptor())
        .build()

    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(
        savedInstanceState: Bundle?,
    ) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // アプリ起動時に WebSocket サーバーを開始
        InspectorServer.start()

        setContent {
            AppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    var logText by remember { mutableStateOf("ログ: 待機中") }
                    var isLoading by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(16.dp),
                        ) {
                            Text("ADB Network Inspector", fontSize = 24.sp)
                            Text(
                                "WebSocket サーバー起動中 (Port: 8082)",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.secondary,
                            )

                            if (isLoading) {
                                CircularProgressIndicator()
                            } else {
                                Spacer(modifier = Modifier.height(40.dp))
                            }

                            // 各種通信生成ボタン
                            Button(
                                onClick = {
                                    sendRequest("https://jsonplaceholder.typicode.com/posts/1", "GET") {
                                            success,
                                            result,
                                        ->
                                        logText =
                                            if (success) "GET (JSON) 成功: \n$result" else "GET (JSON) 失敗: \n$result"
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("GET リクエスト (JSON)")
                            }

                            Button(
                                onClick = {
                                    sendRequest("https://picsum.photos/400/300", "GET") { success, result ->
                                        logText =
                                            if (success) "GET (画像) 成功: 画像データを受信しました" else "GET (画像) 失敗: \n$result"
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("GET リクエスト (画像)")
                            }

                            Button(
                                onClick = {
                                    val json = """{"title": "foo", "body": "bar", "userId": 1}"""
                                    sendRequest("https://jsonplaceholder.typicode.com/posts", "POST", json) {
                                            success,
                                            result,
                                        ->
                                        logText = if (success) "POST 成功: \n$result" else "POST 失敗: \n$result"
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("POST リクエスト (JSON)")
                            }

                            Button(
                                onClick = {
                                    sendRequest("https://jsonplaceholder.typicode.com/invalid-url-path-404", "GET") {
                                            success,
                                            result,
                                        ->
                                        logText = "GET (404エラー確認) 完了: \n$result"
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("GET リクエスト (404エラー)")
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // 簡易ログ表示
                            Text(
                                text = logText,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp)
                                    .padding(8.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun sendRequest(
        url: String,
        method: String,
        bodyContent: String? = null,
        onComplete: (Boolean, String) -> Unit,
    ) {
        scope.launch {
            val requestBuilder = Request.Builder().url(url)
            if (method == "POST" && bodyContent != null) {
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val requestBody = bodyContent.toRequestBody(mediaType)
                requestBuilder.post(requestBody)
            } else {
                requestBuilder.get()
            }

            val request = requestBuilder.build()
            val result = withContext(Dispatchers.IO) {
                try {
                    okHttpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            Pair(true, response.body.string())
                        } else {
                            Pair(false, "HTTP ${response.code}: ${response.message}")
                        }
                    }
                } catch (e: Exception) {
                    Pair(false, e.message ?: "エラーが発生しました")
                }
            }
            onComplete(result.first, result.second)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // アプリ終了時に WebSocket サーバーを停止
        InspectorServer.stop()
    }
}
