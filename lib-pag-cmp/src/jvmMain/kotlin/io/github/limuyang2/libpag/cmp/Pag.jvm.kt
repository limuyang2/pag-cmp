package io.github.limuyang2.libpag.cmp

import androidx.compose.ui.graphics.ImageBitmap

actual object Pag {
    actual fun load(bytes: ByteArray, filePath: String?): PagComposition {
        val handle = JvmPagNative.loadFile(bytes, filePath)
        check(handle != 0L) { "Failed to load PAG composition." }
        return JvmPagComposition(handle)
    }

    actual fun createPlayer(): PagPlayer {
        val handle = JvmPagNative.createPlayer()
        check(handle != 0L) { "Failed to create PAG player." }
        return JvmPagPlayer(handle)
    }
}

internal class JvmPagComposition(
    private val handle: Long,
) : PagComposition {
    private var closed = false

    override val durationUs: Long
        get() = JvmPagNative.compositionDurationUs(requireOpenHandle())

    override val width: Int
        get() = JvmPagNative.compositionWidth(requireOpenHandle())

    override val height: Int
        get() = JvmPagNative.compositionHeight(requireOpenHandle())

    override fun close() {
        if (!closed) {
            closed = true
            JvmPagNative.release(handle)
        }
    }

    fun requireOpenHandle(): Long {
        check(!closed) { "PagComposition is closed." }
        return handle
    }
}

internal class JvmPagPlayer(
    private val handle: Long,
) : PagPlayer {
    private var closed = false
    // JVM 走 readPixels 到 ImageBitmap 的路径，像素数组和 Skia Bitmap 必须复用，
    // 否则动画播放时会持续分配大对象并触发 GC。
    private val frameBuffer = JvmPagFrameBuffer()

    override var composition: PagComposition? = null
        set(value) {
            checkOpen()
            val jvmComposition = value?.asJvmPagComposition()
            JvmPagNative.setComposition(handle, jvmComposition?.requireOpenHandle() ?: 0L)
            field = value
        }

    override var progress: Double = 0.0
        set(value) {
            checkOpen()
            field = value.coerceIn(0.0, 1.0)
        }

    override var scaleMode: PagScaleMode = PagScaleMode.LetterBox
        set(value) {
            checkOpen()
            field = value
        }

    override var cacheEnabled: Boolean = true
        set(value) {
            checkOpen()
            field = value
            JvmPagNative.setCacheEnabled(handle, value)
        }

    var videoEnabled: Boolean = true
        set(value) {
            checkOpen()
            field = value
            JvmPagNative.setVideoEnabled(handle, value)
        }

    var useDiskCache: Boolean = false
        set(value) {
            checkOpen()
            field = value
            JvmPagNative.setUseDiskCache(handle, value)
        }

    override fun render(size: PagSize): ImageBitmap {
        checkOpen()
        // composition 可能在 set 到 player 后被外部 close，这里提前给出明确错误。
        requireComposition().asJvmPagComposition().requireOpenHandle()
        val pixels = frameBuffer.pixelsFor(size)
        val rendered = JvmPagNative.renderToBgra(
            playerHandle = handle,
            width = size.width,
            height = size.height,
            progress = progress,
            scaleMode = scaleMode.ordinal,
            pixels = pixels,
        )
        check(rendered) { "Failed to render PAG frame." }
        return frameBuffer.updateImage(size)
    }

    override fun close() {
        if (!closed) {
            closed = true
            JvmPagNative.release(handle)
        }
    }

    private fun checkOpen() {
        check(!closed) { "PagPlayer is closed." }
    }
}

private fun PagComposition.asJvmPagComposition(): JvmPagComposition =
    this as? JvmPagComposition
        ?: error("JVM PagPlayer requires a composition created by Pag.load() on JVM.")
