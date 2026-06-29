package io.github.limuyang2.libpag.cmp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import swiftPMImport.io.github.limuyang2.lib.pag.cmp.PAGScaleModeLetterBox
import swiftPMImport.io.github.limuyang2.lib.pag.cmp.PAGScaleModeNone
import swiftPMImport.io.github.limuyang2.lib.pag.cmp.PAGScaleModeStretch
import swiftPMImport.io.github.limuyang2.lib.pag.cmp.PAGScaleModeZoom
import swiftPMImport.io.github.limuyang2.lib.pag.cmp.PAGView

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
    val composition = remember(bytes) { Pag.load(bytes) }
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

@OptIn(ExperimentalForeignApi::class)
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
    val iosComposition = composition.asIosPagComposition()

    UIKitView(
        factory = {
            PAGView().apply {
                setComposition(iosComposition.nativeComposition)
            }
        },
        modifier = modifier,
        update = { view ->
            view.setComposition(iosComposition.nativeComposition)
            view.applyPagViewState(isPlaying, progress, repeatCount, scaleMode, cacheEnabled, videoEnabled, useDiskCache)
        },
        onRelease = { view ->
            view.stop()
            view.freeCache()
        },
        properties = UIKitInteropProperties(
            isInteractive = true,
            isNativeAccessibilityEnabled = true,
        ),
    )
}

private fun PagComposition.asIosPagComposition(): IosPagComposition =
    this as? IosPagComposition
        ?: error("iOS PagView requires a composition created by Pag.load() on iOS.")

@OptIn(ExperimentalForeignApi::class)
private fun PagScaleMode.toIosPagScaleMode() = when (this) {
    PagScaleMode.None -> PAGScaleModeNone
    PagScaleMode.Stretch -> PAGScaleModeStretch
    PagScaleMode.LetterBox -> PAGScaleModeLetterBox
    PagScaleMode.Zoom -> PAGScaleModeZoom
}

@OptIn(ExperimentalForeignApi::class)
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
    setScaleMode(scaleMode.toIosPagScaleMode())
    setCacheEnabled(cacheEnabled)
    setVideoEnabled(videoEnabled)
    setUseDiskCache(useDiskCache)
    progress?.let { setProgress(it.coerceIn(0.0, 1.0)) }
    if (isPlaying) {
        play()
    } else {
        pause()
        if (progress != null) {
            flush()
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
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
    // libpag's PAGView loads a local path (and a network URL on a best-effort basis) natively via
    // setPathAsync, so we skip the bytes/composition path entirely.
    var pagView by remember { mutableStateOf<PAGView?>(null) }

    LaunchedEffect(path, pagView) {
        val view = pagView ?: return@LaunchedEffect
        val convertedPath = pathConvert(path)
        view.setPathAsync(filePath = convertedPath, completionBlock = { pagFile ->
            if (pagFile == null) {
                println("PagView: failed to load PAG from path: $path")
            }
        })
    }

    UIKitView(
        factory = {
            PAGView().apply { pagView = this }
        },
        modifier = modifier,
        update = { view ->
            view.applyPagViewState(isPlaying, progress, repeatCount, scaleMode, cacheEnabled, videoEnabled, useDiskCache)
        },
        onRelease = { view ->
            view.stop()
            view.freeCache()
        },
        properties = UIKitInteropProperties(
            isInteractive = true,
            isNativeAccessibilityEnabled = true,
        ),
    )
}
