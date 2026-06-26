package io.github.limuyang2.libpag.cmp

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import swiftPMImport.io.github.limuyang2.lib.pag.cmp.PAGFile
import swiftPMImport.io.github.limuyang2.lib.pag.cmp.PAGComposition as IosNativePagComposition

@OptIn(ExperimentalForeignApi::class)
actual object Pag {
    actual fun load(bytes: ByteArray, filePath: String?): PagComposition {
        require(bytes.isNotEmpty()) { "PAG bytes must not be empty." }
        val pagFile = bytes.usePinned { pinned ->
            PAGFile.Load(pinned.addressOf(0), bytes.size.convert())
        }
        return IosPagComposition(requireNotNull(pagFile) { "Invalid PAG bytes." })
    }

    actual fun createPlayer(): PagPlayer =
        unsupportedPagPlatform("iOS")
}

@OptIn(ExperimentalForeignApi::class)
internal class IosPagComposition(
    val nativeComposition: IosNativePagComposition,
) : PagComposition {
    override val durationUs: Long
        get() = nativeComposition.duration()

    override val width: Int
        get() = nativeComposition.width().toInt()

    override val height: Int
        get() = nativeComposition.height().toInt()

    override fun close() {
        // The Objective-C framework is ARC-managed from Kotlin/Native interop.
    }
}
