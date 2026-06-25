package io.github.limuyang2.libpag.cmp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import swiftPMImport.Pagcmp.lib.pag.cmp.PAGScaleModeLetterBox
import swiftPMImport.Pagcmp.lib.pag.cmp.PAGScaleModeNone
import swiftPMImport.Pagcmp.lib.pag.cmp.PAGScaleModeStretch
import swiftPMImport.Pagcmp.lib.pag.cmp.PAGScaleModeZoom
import swiftPMImport.Pagcmp.lib.pag.cmp.PAGView

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
            view.setRepeatCount(repeatCount)
            view.setScaleMode(scaleMode.toIosPagScaleMode())
            view.setCacheEnabled(cacheEnabled)
            view.setVideoEnabled(videoEnabled)
            view.setUseDiskCache(useDiskCache)
            progress?.let { view.setProgress(it.coerceIn(0.0, 1.0)) }
            if (isPlaying) {
                view.play()
            } else {
                view.pause()
                if (progress != null) {
                    view.flush()
                }
            }
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
