package io.github.limuyang2.myapplication

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Pagcmp",
    ) {
        App()
    }
}