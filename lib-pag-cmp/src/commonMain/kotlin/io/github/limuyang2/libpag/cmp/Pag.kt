package io.github.limuyang2.libpag.cmp

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Entry point for loading PAG files and creating renderers.
 */
expect object Pag {
    /**
     * Loads a PAG file from bytes.
     *
     * [filePath] is optional metadata for native decoders that use it for cache keys or diagnostics.
     */
    fun load(bytes: ByteArray, filePath: String? = null): PagComposition

    /**
     * Creates a reusable player. A player owns render state such as progress and cache settings.
     */
    fun createPlayer(): PagPlayer
}

interface PagCloseable {
    fun close()
}

data class PagSize(
    val width: Int,
    val height: Int,
) {
    init {
        require(width > 0) { "width must be greater than 0." }
        require(height > 0) { "height must be greater than 0." }
    }
}

enum class PagScaleMode {
    None,
    Stretch,
    LetterBox,
    Zoom,
}

interface PagComposition : PagCloseable {
    val durationUs: Long
    val width: Int
    val height: Int

    val intrinsicSize: PagSize
        get() = PagSize(width, height)
}

interface PagPlayer : PagCloseable {
    var composition: PagComposition?
    var progress: Double
    var scaleMode: PagScaleMode
    var cacheEnabled: Boolean

    /**
     * Renders the current [progress] into an offscreen bitmap.
     */
    fun render(size: PagSize = requireComposition().intrinsicSize): ImageBitmap
}

fun PagPlayer.requireComposition(): PagComposition =
    composition ?: error("PagPlayer has no composition. Call set composition before rendering.")

internal fun unsupportedPagPlatform(platform: String): Nothing =
    throw UnsupportedOperationException(
        "PAG rendering is not implemented for $platform yet. " +
            "The public KMP API is in place; this target still needs a libpag native bridge."
    )
