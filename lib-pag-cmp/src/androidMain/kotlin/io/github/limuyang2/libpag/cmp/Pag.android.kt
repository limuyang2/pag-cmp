package io.github.limuyang2.libpag.cmp

actual object Pag {
    actual fun load(bytes: ByteArray, filePath: String?): PagComposition =
        unsupportedPagPlatform("Android")

    actual fun createPlayer(): PagPlayer =
        unsupportedPagPlatform("Android")
}
