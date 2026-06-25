package io.github.limuyang2.libpag.cmp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.libpag.PAGFile
import org.libpag.PAGView
import org.libpag.PAGScaleMode as AndroidPagScaleMode

@Composable
fun PagView(
    bytes: ByteArray,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
    progress: Double? = null,
    repeatCount: Int = 0,
    scaleMode: PagScaleMode = PagScaleMode.LetterBox,
    cacheEnabled: Boolean = true,
    videoEnabled: Boolean = true,
    useDiskCache: Boolean = false,
    onViewCreated: (PAGView) -> Unit = {},
) {
    val composition = remember(bytes) { AndroidPagComposition(loadAndroidPagFile(bytes)) }
    DisposableEffect(composition) {
        onDispose { composition.close() }
    }
    PagView(
        composition = composition,
        modifier = modifier,
        isPlaying = isPlaying,
        progress = progress,
        repeatCount = repeatCount,
        scaleMode = scaleMode,
        cacheEnabled = cacheEnabled,
        videoEnabled = videoEnabled,
        useDiskCache = useDiskCache,
        onViewCreated = onViewCreated,
    )
}

@Composable
fun PagView(
    composition: PagComposition,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
    progress: Double? = null,
    repeatCount: Int = 0,
    scaleMode: PagScaleMode = PagScaleMode.LetterBox,
    cacheEnabled: Boolean = true,
    videoEnabled: Boolean = true,
    useDiskCache: Boolean = false,
    onViewCreated: (PAGView) -> Unit = {},
) {
    val androidComposition = composition.asAndroidPagComposition()
    var pagView by remember { mutableStateOf<PAGView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            pagView?.stop()
            pagView?.freeCache()
            pagView = null
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            PAGView(context).apply {
                pagView = this
                setComposition(androidComposition.nativeComposition)
                onViewCreated(this)
            }
        },
        update = { view ->
            if (view.composition !== androidComposition.nativeComposition) {
                view.composition = androidComposition.nativeComposition
            }
            view.setRepeatCount(repeatCount)
            view.setScaleMode(scaleMode.toAndroidPagScaleMode())
            view.setCacheEnabled(cacheEnabled)
            view.setVideoEnabled(videoEnabled)
            view.setUseDiskCache(useDiskCache)
            progress?.let { view.progress = it.coerceIn(0.0, 1.0) }
            if (isPlaying) {
                view.play()
            } else {
                view.pause()
                if (progress != null) {
                    view.flush()
                }
            }
        },
    )
}

private fun loadAndroidPagFile(bytes: ByteArray): PAGFile =
    requireNotNull(PAGFile.Load(bytes)) { "Invalid PAG bytes." }

private fun PagComposition.asAndroidPagComposition(): AndroidPagComposition =
    this as? AndroidPagComposition
        ?: error("Android PagView requires a composition created by Pag.load() on Android.")

private fun PagScaleMode.toAndroidPagScaleMode(): Int = when (this) {
    PagScaleMode.None -> AndroidPagScaleMode.None
    PagScaleMode.Stretch -> AndroidPagScaleMode.Stretch
    PagScaleMode.LetterBox -> AndroidPagScaleMode.LetterBox
    PagScaleMode.Zoom -> AndroidPagScaleMode.Zoom
}
