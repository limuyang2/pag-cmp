package io.github.limuyang2.libpag.cmp

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
actual fun PagView(
    bytes: ByteArray,
    modifier: Modifier,
    isPlaying: Boolean,
    progress: Double?,
    repeatCount: Int,
    scaleMode: PagScaleMode,
    cacheEnabled: Boolean,
    videoEnabled: Boolean,
    useDiskCache: Boolean,
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
    )
}

@Composable
actual fun PagView(
    composition: PagComposition,
    modifier: Modifier,
    isPlaying: Boolean,
    progress: Double?,
    repeatCount: Int,
    scaleMode: PagScaleMode,
    cacheEnabled: Boolean,
    videoEnabled: Boolean,
    useDiskCache: Boolean,
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
            }
        },
        update = { view ->
            if (view.composition !== androidComposition.nativeComposition) {
                view.composition = androidComposition.nativeComposition
            }
            view.applyPagViewState(isPlaying, progress, repeatCount, scaleMode, cacheEnabled, videoEnabled, useDiskCache)
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

private fun PAGView.applyPagViewState(
    isPlaying: Boolean,
    progress: Double?,
    repeatCount: Int,
    scaleMode: PagScaleMode,
    cacheEnabled: Boolean,
    videoEnabled: Boolean,
    useDiskCache: Boolean,
) {
    setRepeatCount(repeatCount)
    setScaleMode(scaleMode.toAndroidPagScaleMode())
    setCacheEnabled(cacheEnabled)
    setVideoEnabled(videoEnabled)
    setUseDiskCache(useDiskCache)
    progress?.let { this.progress = it.coerceIn(0.0, 1.0) }
    if (isPlaying) {
        play()
    } else {
        pause()
        if (progress != null) {
            flush()
        }
    }
}

@Composable
actual fun PagView(
    path: String,
    modifier: Modifier,
    isPlaying: Boolean,
    progress: Double?,
    repeatCount: Int,
    scaleMode: PagScaleMode,
    cacheEnabled: Boolean,
    videoEnabled: Boolean,
    useDiskCache: Boolean,
) {
    // libpag's PAGView loads a local path or network URL natively via setPathAsync, so we skip the
    // bytes/composition path entirely and let it own loading + rendering.
    var pagView by remember { mutableStateOf<PAGView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            pagView?.stop()
            pagView?.freeCache()
            pagView = null
        }
    }

    LaunchedEffect(path, pagView) {
        val view = pagView ?: return@LaunchedEffect
        view.setPathAsync(pathConvert(path)) { result ->
            if (result == null) {
                Log.w("PagView", "Failed to load PAG from path: $path")
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            PAGView(context).apply { pagView = this }
        },
        update = { view ->
            view.applyPagViewState(isPlaying, progress, repeatCount, scaleMode, cacheEnabled, videoEnabled, useDiskCache)
        },
    )
}
