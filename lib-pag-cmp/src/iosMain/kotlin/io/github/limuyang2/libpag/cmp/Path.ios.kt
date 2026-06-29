package io.github.limuyang2.libpag.cmp

actual fun pathConvert(path: String): String {
    if (path.isHttpUrl()) return path
    return path.removePrefix(IosFileUriPrefix)
}

private const val IosFileUriPrefix = "file://"
