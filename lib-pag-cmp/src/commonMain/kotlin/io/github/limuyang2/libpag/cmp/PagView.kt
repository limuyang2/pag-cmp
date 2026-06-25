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
