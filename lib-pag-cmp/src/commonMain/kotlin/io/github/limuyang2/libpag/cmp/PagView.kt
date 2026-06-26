package io.github.limuyang2.libpag.cmp

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun PagView(
    bytes: ByteArray,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
    progress: Double? = null,
    repeatCount: Int = 0,
    scaleMode: PagScaleMode = PagScaleMode.LetterBox,
    cacheEnabled: Boolean = true,
    videoEnabled: Boolean = true,
    useDiskCache: Boolean = false,
)

@Composable
expect fun PagView(
    composition: PagComposition,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
    progress: Double? = null,
    repeatCount: Int = 0,
    scaleMode: PagScaleMode = PagScaleMode.LetterBox,
    cacheEnabled: Boolean = true,
    videoEnabled: Boolean = true,
    useDiskCache: Boolean = false,
)

/**
 * Loads and renders a PAG file from [path].
 *
 * [path] can be a local file path or a network URL (`http`/`https`). Loading is asynchronous:
 * nothing is rendered until the file is ready, and a load failure is logged instead of throwing.
 * Network URL support depends on the platform (see the README).
 */
@Composable
expect fun PagView(
    path: String,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
    progress: Double? = null,
    repeatCount: Int = 0,
    scaleMode: PagScaleMode = PagScaleMode.LetterBox,
    cacheEnabled: Boolean = true,
    videoEnabled: Boolean = true,
    useDiskCache: Boolean = false,
)
