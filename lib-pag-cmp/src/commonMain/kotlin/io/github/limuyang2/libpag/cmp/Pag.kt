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

/**
 * Controls how PAG content is fitted into the render area.
 *
 * This only affects the content transform inside the native PAG renderer. It does not measure,
 * resize, or otherwise affect the Compose [PagView] layout bounds.
 */
enum class PagScaleMode {
    /**
     * Keeps the PAG composition at its original size and position.
     */
    None,

    /**
     * Scales width and height independently to fill the render area. The content may be distorted.
     */
    Stretch,

    /**
     * Scales uniformly until the whole composition is visible. Empty space may remain on one axis.
     */
    LetterBox,

    /**
     * Scales uniformly until the render area is fully covered. Content may be cropped on one axis.
     */
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
