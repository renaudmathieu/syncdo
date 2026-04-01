package com.doppio.syncdo

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application

fun main() {
    val instanceName = System.getProperty("syncDo.instance") ?: "default"
    // Set a unique storage path per instance for testing multiple devices on the same machine
    System.setProperty("syncDo.storagePath", "${System.getProperty("user.home")}/.syncdo/$instanceName")

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "SyncDO — $instanceName",
            state = WindowState(
                width = 412.dp,
                height = 942.dp,
            ),
        ) {
            App()
        }
    }
}
