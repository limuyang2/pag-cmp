package io.github.limuyang2.libpag.cmp

actual fun pathConvert(path: String): String {
    if (path.isHttpUrl()) return path
    if (path.startsWith(AndroidAssetUriPrefix)) {
        return AndroidAssetPathPrefix + path.removePrefix(AndroidAssetUriPrefix)
    }
    return path
}

private const val AndroidAssetUriPrefix = "file:///android_asset/"
private const val AndroidAssetPathPrefix = "assets://"
