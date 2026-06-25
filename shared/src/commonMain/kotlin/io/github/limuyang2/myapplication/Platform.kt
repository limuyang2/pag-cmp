package io.github.limuyang2.myapplication

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform