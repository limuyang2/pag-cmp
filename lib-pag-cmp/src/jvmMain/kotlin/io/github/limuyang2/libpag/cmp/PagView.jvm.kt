package io.github.limuyang2.libpag.cmp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.min

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
    val player = remember { Pag.createPlayer().asJvmPagPlayer() }
    val currentIsPlaying by rememberUpdatedState(isPlaying)
    val currentProgress by rememberUpdatedState(progress)
    val currentRepeatCount by rememberUpdatedState(repeatCount)
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }
    var playbackStartNanos by remember { mutableStateOf<Long?>(null) }
    var completedLoops by remember { mutableStateOf(0) }

    DisposableEffect(player) {
        onDispose { player.close() }
    }

    LaunchedEffect(player, composition, scaleMode, cacheEnabled, videoEnabled, useDiskCache) {
        player.composition = composition
        player.scaleMode = scaleMode
        player.cacheEnabled = cacheEnabled
        player.videoEnabled = videoEnabled
        player.useDiskCache = useDiskCache
    }

    LaunchedEffect(
        player,
        composition,
        canvasSize,
        isPlaying,
        progress,
        repeatCount,
        scaleMode,
        cacheEnabled,
        videoEnabled,
        useDiskCache,
    ) {
        val width = canvasSize.width
        val height = canvasSize.height
        if (width <= 0 || height <= 0) return@LaunchedEffect

        val renderSize = PagSize(width, height)
        fun render(progressValue: Double) {
            player.progress = progressValue
            frame = player.render(renderSize)
        }

        if (!currentIsPlaying) {
            render(currentProgress ?: player.progress)
            return@LaunchedEffect
        }

        playbackStartNanos = null
        completedLoops = 0
        val durationNanos = max(1L, composition.durationUs * 1_000L)

        var shouldContinue = true
        while (shouldContinue) {
            withFrameNanos { now ->
                val explicitProgress = currentProgress
                if (explicitProgress != null) {
                    render(explicitProgress)
                    return@withFrameNanos
                }

                val start = playbackStartNanos ?: now.also { playbackStartNanos = it }
                val elapsed = now - start
                val loop = (elapsed / durationNanos).toInt()
                if (currentRepeatCount in 1..loop) {
                    render(1.0)
                    shouldContinue = false
                    return@withFrameNanos
                }
                if (loop != completedLoops) {
                    completedLoops = loop
                }
                render((elapsed % durationNanos).toDouble() / durationNanos.toDouble())
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it },
    ) {
        frame?.let { image ->
            drawPagImage(image, scaleMode)
        }
    }
}

private fun PagPlayer.asJvmPagPlayer(): JvmPagPlayer =
    this as? JvmPagPlayer
        ?: error("JVM PagView requires a player created by Pag.createPlayer() on JVM.")

private fun DrawScope.drawPagImage(
    image: ImageBitmap,
    scaleMode: PagScaleMode,
) {
    val srcWidth = image.width.toFloat()
    val srcHeight = image.height.toFloat()
    val dstWidth = size.width
    val dstHeight = size.height
    if (srcWidth <= 0f || srcHeight <= 0f || dstWidth <= 0f || dstHeight <= 0f) return

    val (targetSize, topLeft) = when (scaleMode) {
        PagScaleMode.Stretch -> Size(dstWidth, dstHeight) to Offset.Zero
        PagScaleMode.None -> Size(srcWidth, srcHeight) to Offset.Zero
        PagScaleMode.LetterBox -> {
            val scale = min(dstWidth / srcWidth, dstHeight / srcHeight)
            val width = srcWidth * scale
            val height = srcHeight * scale
            Size(width, height) to Offset((dstWidth - width) / 2f, (dstHeight - height) / 2f)
        }
        PagScaleMode.Zoom -> {
            val scale = max(dstWidth / srcWidth, dstHeight / srcHeight)
            val width = srcWidth * scale
            val height = srcHeight * scale
            Size(width, height) to Offset((dstWidth - width) / 2f, (dstHeight - height) / 2f)
        }
    }

    drawImage(
        image = image,
        dstOffset = IntOffset(topLeft.x.toInt(), topLeft.y.toInt()),
        dstSize = IntSize(
            targetSize.width.toInt().coerceAtLeast(1),
            targetSize.height.toInt().coerceAtLeast(1),
        ),
    )
}
