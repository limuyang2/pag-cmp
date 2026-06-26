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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

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
    var frame by remember { mutableStateOf<JvmPagRenderedFrame?>(null) }
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
        var nextFrameVersion = 0
        fun render(progressValue: Double) {
            player.progress = progressValue
            frame = JvmPagRenderedFrame(
                image = player.render(renderSize),
                version = nextFrameVersion++,
            )
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
        // scaleMode 已在 libpag native 渲染到 offscreen surface 时处理。
        // Compose 这里只负责把 readPixels 得到的结果 1:1 画出来。
        frame?.let { renderedFrame ->
            drawImage(renderedFrame.image)
        }
    }
}

private data class JvmPagRenderedFrame(
    val image: ImageBitmap,
    val version: Int,
)

private fun PagPlayer.asJvmPagPlayer(): JvmPagPlayer =
    this as? JvmPagPlayer
        ?: error("JVM PagView requires a player created by Pag.createPlayer() on JVM.")

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
    // Read the local file (or download the http(s) URL via the JDK) into bytes, then reuse the
    // existing PagView(bytes) renderer. Loading is async; nothing renders until bytes are ready.
    var bytes by remember(path) { mutableStateOf<ByteArray?>(null) }
    LaunchedEffect(path) {
        bytes = runCatching { readPagPathBytes(path) }.getOrElse {
            println("PagView: failed to load PAG from path: $path")
            null
        }
    }
    bytes?.let {
        PagView(
            bytes = it,
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
}

private suspend fun readPagPathBytes(path: String): ByteArray =
    withContext(Dispatchers.IO) {
        when {
            path.startsWith("http://") || path.startsWith("https://") || path.startsWith("file:") || path.startsWith("jar:") -> {
                java.net.URI.create(path).toURL().openStream().use { it.readBytes() }
            }

            else -> java.io.File(path).readBytes()
        }
    }
