package net.mm2d.inspector.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import net.mm2d.inspector.desktop.client.InspectorClient
import net.mm2d.inspector.desktop.ui.MainWindow

fun main() =
    application {
        val client = remember {
            InspectorClient().apply {
                start()
            }
        }

        // アプリケーション終了時に接続ループを停止する
        DisposableEffect(Unit) {
            onDispose {
                client.stop()
            }
        }

        Window(
            onCloseRequest = ::exitApplication,
            title = "ADB Network Inspector",
            state = rememberWindowState(size = DpSize(1200.dp, 800.dp)),
        ) {
            MainWindow(client)
        }
    }
