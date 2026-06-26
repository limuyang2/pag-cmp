package io.github.limuyang2.libpag.cmp

import androidx.compose.ui.graphics.ImageBitmap

actual object Pag {
    actual fun load(bytes: ByteArray, filePath: String?): PagComposition =
        JvmPagComposition(JvmPagNative.loadFile(bytes, filePath))

    actual fun createPlayer(): PagPlayer =
        JvmPagPlayer(JvmPagNative.createPlayer())
}

internal class JvmPagComposition(
    val handle: Long,
) : PagComposition {
    private var closed = false

    override val durationUs: Long =
        JvmPagNative.compositionDurationUs(handle)

    override val width: Int =
        JvmPagNative.compositionWidth(handle)

    override val height: Int =
        JvmPagNative.compositionHeight(handle)

    override fun close() {
        if (!closed) {
            closed = true
            JvmPagNative.release(handle)
        }
    }
}

internal class JvmPagPlayer(
    private val handle: Long,
) : PagPlayer {
    private var closed = false
    private var surfaceSize: PagSize? = null
    private var pixelBuffer = ByteArray(0)

    override var composition: PagComposition? = null
        set(value) {
            val jvmComposition = value?.asJvmPagComposition()
            JvmPagNative.setComposition(handle, jvmComposition?.handle ?: 0L)
            field = value
        }

    override var progress: Double = 0.0
        set(value) {
            field = value.coerceIn(0.0, 1.0)
        }

    override var scaleMode: PagScaleMode = PagScaleMode.LetterBox

    override var cacheEnabled: Boolean = true
        set(value) {
            field = value
            JvmPagNative.setCacheEnabled(handle, value)
        }

    var videoEnabled: Boolean = true
        set(value) {
            field = value
            JvmPagNative.setVideoEnabled(handle, value)
        }

    var useDiskCache: Boolean = false
        set(value) {
            field = value
            JvmPagNative.setUseDiskCache(handle, value)
        }

    override fun render(size: PagSize): ImageBitmap {
        check(!closed) { "PagPlayer is closed." }
        requireComposition()
        ensureBuffer(size)
        val rendered = JvmPagNative.renderToBgra(
            playerHandle = handle,
            width = size.width,
            height = size.height,
            progress = progress,
            scaleMode = scaleMode.ordinal,
            pixels = pixelBuffer,
        )
        check(rendered) { "Failed to render PAG frame." }
        return pixelBuffer.toImageBitmap(size.width, size.height)
    }

    override fun close() {
        if (!closed) {
            closed = true
            JvmPagNative.release(handle)
        }
    }

    private fun ensureBuffer(size: PagSize) {
        if (surfaceSize != size || pixelBuffer.size != size.width * size.height * BytesPerPixel) {
            surfaceSize = size
            pixelBuffer = ByteArray(size.width * size.height * BytesPerPixel)
        }
    }

    private companion object {
        const val BytesPerPixel = 4
    }
}

private fun PagComposition.asJvmPagComposition(): JvmPagComposition =
    this as? JvmPagComposition
        ?: error("JVM PagPlayer requires a composition created by Pag.load() on JVM.")
