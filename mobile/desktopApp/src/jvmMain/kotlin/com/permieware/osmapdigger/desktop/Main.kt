package com.permieware.osmapdigger.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "OsmapDigger test",
    ) {
    }
}