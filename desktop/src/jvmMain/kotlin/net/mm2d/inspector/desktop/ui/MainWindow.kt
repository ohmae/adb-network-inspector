package net.mm2d.inspector.desktop.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.Json
import net.mm2d.inspector.desktop.client.InspectorClient
import net.mm2d.inspector.model.NetworkEvent
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale

// 通信トランザクションのモデリング（RequestとResponseの紐付け）
data class HttpTransaction(
    val id: String,
    val request: NetworkEvent.Request?,
    val response: NetworkEvent.Response?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainWindow(
    client: InspectorClient,
) {
    val connectionState by client.connectionState.collectAsState()
    val events by client.events.collectAsState()

    var selectedTransactionId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var methodFilter by remember { mutableStateOf("ALL") }
    var statusFilter by remember { mutableStateOf("ALL") }

    // イベントリストからトランザクションモデルへの集計
    val transactions = remember(events) {
        val map = mutableMapOf<String, HttpTransaction>()
        events.forEach { event ->
            val id = event.id
            val current = map[id]
            when (event) {
                is NetworkEvent.Request -> {
                    map[id] = current?.copy(request = event) ?: HttpTransaction(id, event, null)
                }

                is NetworkEvent.Response -> {
                    map[id] = current?.copy(response = event) ?: HttpTransaction(id, null, event)
                }
            }
        }
        map.values.sortedBy { it.request?.timestamp ?: it.response?.timestamp ?: 0L }
    }

    // フィルタリング処理
    val filteredTransactions = remember(transactions, searchQuery, methodFilter, statusFilter) {
        transactions.filter { tx ->
            val url = tx.request?.url ?: ""
            val method = tx.request?.method ?: ""
            val status = tx.response?.code?.toString() ?: ""

            val matchesSearch = url.contains(searchQuery, ignoreCase = true) ||
                method.contains(searchQuery, ignoreCase = true) ||
                status.contains(searchQuery)

            val matchesMethod = methodFilter == "ALL" || method.equals(methodFilter, ignoreCase = true)

            val matchesStatus = when (statusFilter) {
                "ALL" -> true
                "2xx" -> tx.response?.code in 200..299
                "3xx" -> tx.response?.code in 300..399
                "4xx" -> tx.response?.code in 400..499
                "5xx" -> tx.response?.code in 500..599
                "Error" -> tx.response == null || tx.response.code >= 400
                else -> true
            }

            matchesSearch && matchesMethod && matchesStatus
        }
    }

    val selectedTx = transactions.find { it.id == selectedTransactionId }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF64B5F6),
            secondary = Color(0xFF03DAC6),
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E),
            onBackground = Color(0xFFFFFFFF),
            onSurface = Color(0xFFFFFFFF),
        ),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("ADB Network Inspector") },
                    actions = {
                        // 接続ステータス表示
                        val statusText = when (connectionState) {
                            InspectorClient.ConnectionState.Disconnected -> "未接続"
                            InspectorClient.ConnectionState.Connecting -> "接続試行中..."
                            InspectorClient.ConnectionState.Connected -> "接続中"
                        }
                        val statusColor = when (connectionState) {
                            InspectorClient.ConnectionState.Disconnected -> Color.Red
                            InspectorClient.ConnectionState.Connecting -> Color.Yellow
                            InspectorClient.ConnectionState.Connected -> Color.Green
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 16.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(statusColor),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(statusText, fontSize = 14.sp)
                        }

                        IconButton(onClick = {
                            client.clearEvents()
                            selectedTransactionId = null
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "ログクリア")
                        }
                    },
                )
            },
        ) { paddingValues ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background),
            ) {
                // 左ペイン：リストとフィルター (幅 45%)
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.45f)
                        .padding(8.dp),
                ) {
                    // 検索バー
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        placeholder = { Text("URL / Method / Status で検索...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                    )

                    // フィルター選択
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // メソッドフィルター
                        var methodExpanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.weight(1f)) {
                            Button(
                                onClick = { methodExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                ),
                            ) {
                                Text(
                                    "Method: $methodFilter",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            DropdownMenu(
                                expanded = methodExpanded,
                                onDismissRequest = { methodExpanded = false },
                            ) {
                                listOf("ALL", "GET", "POST", "PUT", "DELETE", "PATCH").forEach { m ->
                                    DropdownMenuItem(
                                        text = { Text(m) },
                                        onClick = {
                                            methodFilter = m
                                            methodExpanded = false
                                        },
                                    )
                                }
                            }
                        }

                        // ステータスフィルター
                        var statusExpanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.weight(1f)) {
                            Button(
                                onClick = { statusExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                ),
                            ) {
                                Text(
                                    "Status: $statusFilter",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            DropdownMenu(
                                expanded = statusExpanded,
                                onDismissRequest = { statusExpanded = false },
                            ) {
                                listOf("ALL", "2xx", "3xx", "4xx", "5xx").forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text(s) },
                                        onClick = {
                                            statusFilter = s
                                            statusExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }

                    // リスト表示
                    val listState = rememberLazyListState()
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(filteredTransactions, key = { it.id }) { tx ->
                                TransactionRow(
                                    transaction = tx,
                                    isSelected = tx.id == selectedTransactionId,
                                    onClick = { selectedTransactionId = tx.id },
                                )
                            }
                        }
                        VerticalScrollbar(
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                            adapter = rememberScrollbarAdapter(listState),
                        )
                    }
                }

                // ディバイダー
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(Color.Gray.copy(alpha = 0.5f)),
                )

                // 右ペイン：詳細表示 (残り 55%)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        .padding(8.dp),
                ) {
                    if (selectedTx != null) {
                        TransactionDetail(selectedTx)
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("通信ログを選択すると、詳細が表示されます。", color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TransactionRow(
    transaction: HttpTransaction,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val req = transaction.request
    val res = transaction.response

    val method = req?.method ?: "UNKNOWN"
    val url = req?.url ?: ""
    val status = res?.code ?: -1
    val duration = res?.durationMs

    val methodColor = when (method.uppercase()) {
        "GET" -> Color(0xFF4CAF50)
        "POST" -> Color(0xFF2196F3)
        "PUT" -> Color(0xFFFF9800)
        "DELETE" -> Color(0xFFF44336)
        else -> Color.Gray
    }

    val statusColor = when {
        status in 200..299 -> Color(0xFF4CAF50)
        status in 300..399 -> Color(0xFFFF9800)
        status >= 400 -> Color(0xFFF44336)
        else -> Color.Gray
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primary.copy(
                    alpha = 0.2f,
                )
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        shape = RoundedCornerShape(4.dp),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // メソッドタグ
                    Box(
                        modifier = Modifier
                            .background(methodColor, shape = RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            method,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    // ステータスコード
                    if (status != -1) {
                        Text(
                            status.toString(),
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                        )
                    } else {
                        Text("PENDING", color = Color.LightGray, fontSize = 11.sp)
                    }
                }

                // 所要時間 & サイズ
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (duration != null) {
                        Text("${duration}ms", fontSize = 11.sp, color = Color.Gray)
                    }
                    val size = res?.bodySize ?: req?.bodySize ?: 0L
                    if (size > 0) {
                        Text(formatSize(size), fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // URL
            Text(
                url,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )

            Spacer(modifier = Modifier.height(2.dp))

            // 時刻
            val timeStr = remember(req?.timestamp ?: res?.timestamp) {
                val t = req?.timestamp ?: res?.timestamp ?: 0L
                if (t > 0) {
                    SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(t))
                } else {
                    ""
                }
            }
            if (timeStr.isNotEmpty()) {
                Text(
                    timeStr,
                    fontSize = 10.sp,
                    color = Color.Gray,
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}

@Composable
fun TransactionDetail(
    transaction: HttpTransaction,
) {
    val req = transaction.request
    val res = transaction.response
    val scrollState = rememberScrollState()

    var activeTab by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = activeTab) {
            Tab(selected = activeTab == 0, onClick = { activeTab = 0 }) {
                Text("General / Headers", modifier = Modifier.padding(12.dp))
            }
            Tab(selected = activeTab == 1, onClick = { activeTab = 1 }) {
                Text("Request Body", modifier = Modifier.padding(12.dp))
            }
            Tab(selected = activeTab == 2, onClick = { activeTab = 2 }) {
                Text("Response Body", modifier = Modifier.padding(12.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState),
        ) {
            when (activeTab) {
                0 -> {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // General Info
                        DetailSection("General Info") {
                            DetailItem("URL", req?.url ?: "UNKNOWN")
                            DetailItem("Method", req?.method ?: "UNKNOWN")
                            if (res != null) {
                                DetailItem("Status Code", "${res.code} (${res.message})")
                                DetailItem("Duration", "${res.durationMs} ms")
                            } else {
                                DetailItem("Status Code", "Pending")
                            }
                            val timestamp = req?.timestamp ?: res?.timestamp ?: 0L
                            if (timestamp > 0) {
                                DetailItem(
                                    "Time",
                                    SimpleDateFormat(
                                        "yyyy-MM-dd HH:mm:ss.SSS",
                                        Locale.getDefault(),
                                    ).format(Date(timestamp)),
                                )
                            }
                        }

                        // Request Headers
                        if (req != null && req.headers.isNotEmpty()) {
                            DetailSection("Request Headers") {
                                req.headers.forEach { (k, v) ->
                                    DetailItem(k, v)
                                }
                            }
                        }

                        // Response Headers
                        if (res != null && res.headers.isNotEmpty()) {
                            DetailSection("Response Headers") {
                                res.headers.forEach { (k, v) ->
                                    DetailItem(k, v)
                                }
                            }
                        }
                    }
                }

                1 -> {
                    BodyViewer(
                        body = req?.body,
                        contentType = req?.headers?.get("Content-Type") ?: req?.headers?.get("content-type"),
                        isImage = false,
                    )
                }

                2 -> {
                    BodyViewer(
                        body = res?.body,
                        contentType = res?.contentType,
                        isImage = res?.isImage ?: false,
                    )
                }
            }
        }
    }
}

@Composable
fun DetailSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Divider(color = Color.Gray.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
fun DetailItem(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            modifier = Modifier.width(150.dp),
            color = Color.LightGray,
        )
        SelectionContainer {
            Text(
                value,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
fun BodyViewer(
    body: String?,
    contentType: String?,
    isImage: Boolean,
) {
    if (body.isNullOrEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Text("No Body Content", color = Color.Gray)
        }
        return
    }

    if (isImage) {
        val imageBitmap = remember(body) { rememberImageFromBase64(body) }
        if (imageBitmap != null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.Image(
                    bitmap = imageBitmap,
                    contentDescription = "Response Image",
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Text("画像のデコードに失敗しました。", color = Color.Red)
            }
        }
    } else {
        // JSON整形
        val formattedBody = remember(body, contentType) {
            val isJson = contentType?.contains("json", ignoreCase = true) == true
            if (isJson) formatJson(body) else body
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F0F0F), shape = RoundedCornerShape(4.dp))
                .padding(8.dp),
        ) {
            val hScrollState = rememberScrollState()
            SelectionContainer {
                Text(
                    text = formattedBody,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color(0xFFE0E0E0),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(hScrollState),
                )
            }
        }
    }
}

fun formatJson(
    jsonStr: String,
): String =
    try {
        val jsonElement = Json.parseToJsonElement(jsonStr)
        val prettyJson = Json { prettyPrint = true }
        prettyJson.encodeToString(jsonElement)
    } catch (e: Exception) {
        jsonStr // 整形に失敗した場合はそのまま返す
    }

fun rememberImageFromBase64(
    base64Str: String,
): ImageBitmap? =
    try {
        val bytes = Base64.getDecoder().decode(base64Str)
        ByteArrayInputStream(bytes).use { inputStream ->
            loadImageBitmap(inputStream)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }

fun formatSize(
    bytes: Long,
): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1] + ""
    return String.format(Locale.getDefault(), "%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
}
