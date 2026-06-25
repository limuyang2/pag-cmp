package io.github.limuyang2.libpag.cmp

import org.libpag.PAGFile
import org.libpag.PAGComposition as AndroidPAGComposition

actual object Pag {
    actual fun load(bytes: ByteArray, filePath: String?): PagComposition =
        AndroidPagComposition(requireNotNull(PAGFile.Load(bytes)) { "Invalid PAG bytes." })

    actual fun createPlayer(): PagPlayer =
        unsupportedPagPlatform("Android")
}

internal class AndroidPagComposition(
    val nativeComposition: AndroidPAGComposition,
) : PagComposition {
    override val durationUs: Long
        get() = nativeComposition.duration()

    override val width: Int
        get() = nativeComposition.width()

    override val height: Int
        get() = nativeComposition.height()

    override fun close() {
        // libpag's Android PAGComposition does not expose a public release API.
    }
}
