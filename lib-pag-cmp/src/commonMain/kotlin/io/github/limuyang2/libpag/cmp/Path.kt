package io.github.limuyang2.libpag.cmp

expect fun pathConvert(path: String): String

internal fun String.isHttpUrl(): Boolean {
    return startsWith("http://") || startsWith("https://")
}