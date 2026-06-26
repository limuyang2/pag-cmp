@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.limuyang2.libpag.cmp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.Promise
import kotlinx.coroutines.await
import web.dom.document
import web.html.HTMLCanvasElement

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
    var bounds by remember { mutableStateOf<JsPagBounds?>(null) }
    // Keep a real DOM canvas over the Compose placeholder and let libpag render into it.
    val canvas = remember { createPagCanvas() }
    val currentIsPlaying by rememberUpdatedState(isPlaying)
    val currentProgress by rememberUpdatedState(progress)
    val currentRepeatCount by rememberUpdatedState(repeatCount)
    val currentScaleMode by rememberUpdatedState(scaleMode)
    val currentCacheEnabled by rememberUpdatedState(cacheEnabled)
    val currentVideoEnabled by rememberUpdatedState(videoEnabled)

    DisposableEffect(canvas) {
        document.body.appendChild(canvas)
        onDispose {
            JsPagBridge.destroy(canvas)
            canvas.remove()
        }
    }

    LaunchedEffect(canvas, bytes) {
        JsPagBridge.init(canvas, bytes).await<JsAny?>()
        JsPagBridge.setRepeatCount(canvas, currentRepeatCount)
        JsPagBridge.setScaleMode(canvas, currentScaleMode.toWebScaleMode())
        JsPagBridge.setCacheEnabled(canvas, currentCacheEnabled)
        JsPagBridge.setVideoEnabled(canvas, currentVideoEnabled)
        val explicitProgress = currentProgress
        if (explicitProgress != null) {
            JsPagBridge.setProgress(canvas, explicitProgress.coerceIn(0.0, 1.0)).await<JsAny?>()
        }
        if (currentIsPlaying) {
            JsPagBridge.play(canvas).await<JsAny?>()
        } else {
            JsPagBridge.pause(canvas)
        }
    }

    LaunchedEffect(canvas, isPlaying, progress, repeatCount, scaleMode, cacheEnabled, videoEnabled) {
        JsPagBridge.setRepeatCount(canvas, repeatCount)
        JsPagBridge.setScaleMode(canvas, scaleMode.toWebScaleMode())
        JsPagBridge.setCacheEnabled(canvas, cacheEnabled)
        JsPagBridge.setVideoEnabled(canvas, videoEnabled)
        if (progress != null) {
            JsPagBridge.setProgress(canvas, progress.coerceIn(0.0, 1.0)).await<JsAny?>()
        }
        if (isPlaying) {
            JsPagBridge.play(canvas).await<JsAny?>()
        } else {
            JsPagBridge.pause(canvas)
        }
    }

    LaunchedEffect(canvas, bounds) {
        bounds?.let { JsPagBridge.setCanvasBounds(canvas, it.left, it.top, it.width, it.height) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                val rect = coordinates.boundsInWindow()
                bounds = JsPagBounds(
                    left = rect.left.toDouble(),
                    top = rect.top.toDouble(),
                    width = rect.width.toDouble(),
                    height = rect.height.toDouble(),
                )
            },
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
    unsupportedPagPlatform("JS PagView(composition)")
}

private data class JsPagBounds(
    val left: Double,
    val top: Double,
    val width: Double,
    val height: Double,
)

private fun createPagCanvas(): HTMLCanvasElement {
    val canvas = document.createElement("canvas") as HTMLCanvasElement
    canvas.style.position = "fixed"
    canvas.style.pointerEvents = "none"
    canvas.style.zIndex = "1"
    return canvas
}

private fun PagScaleMode.toWebScaleMode(): Int = when (this) {
    PagScaleMode.None -> 0
    PagScaleMode.Stretch -> 1
    PagScaleMode.LetterBox -> 2
    PagScaleMode.Zoom -> 3
}

@JsFun(
    """
    (canvas, size, readByte) => {
      const stateKey = "__pag_cmp_state";
      const ensureSdk = () => {
        if (globalThis.libpag && globalThis.libpag.PAGInit) return Promise.resolve();
        if (!globalThis.__pag_cmp_sdk_loading) {
          globalThis.__pag_cmp_sdk_loading = new Promise((resolve, reject) => {
            const script = globalThis.document.createElement("script");
            script.type = "module";
            script.src = "pag/libpag-bootstrap.js";
            script.async = true;
            script.onload = resolve;
            script.onerror = () => reject(new Error("Failed to load local libpag Web SDK."));
            globalThis.document.head.appendChild(script);
          });
        }
        return globalThis.__pag_cmp_sdk_loading;
      };
      return ensureSdk().then(() => {
        if (!globalThis.libpag || !globalThis.libpag.PAGInit) {
          throw new Error("libpag Web SDK is not loaded. Add libpag.min.js before webApp.js.");
        }
        if (!globalThis.__pag_cmp_module) {
          globalThis.__pag_cmp_module = globalThis.libpag.PAGInit();
        }
        return globalThis.__pag_cmp_module;
      }).then((PAG) => {
        const data = new Uint8Array(size);
        for (let i = 0; i < size; i++) data[i] = readByte(i) & 255;
        const previousState = canvas[stateKey];
        const previous = previousState && previousState.view
          ? previousState.view.destroy()
          : Promise.resolve();
        return previous
          .then(() => PAG.PAGFile.load(data.buffer))
          .then((pagFile) => {
            return PAG.PAGView.init(pagFile, canvas, { firstFrame: true, useScale: false })
              .then((pagView) => {
                canvas[stateKey] = { file: pagFile, view: pagView };
              });
          });
      });
    }
    """
)
private external fun pagInit(canvas: HTMLCanvasElement, size: Int, readByte: (Int) -> Int): Promise<JsAny?>

@JsFun(
    """
    (canvas) => {
      const state = canvas.__pag_cmp_state;
      const view = state && state.view;
      return view ? view.play() : Promise.resolve();
    }
    """
)
private external fun pagPlay(canvas: HTMLCanvasElement): Promise<JsAny?>

@JsFun(
    """
    (canvas) => {
      const state = canvas.__pag_cmp_state;
      const view = state && state.view;
      if (view) view.pause();
    }
    """
)
private external fun pagPause(canvas: HTMLCanvasElement)

@JsFun(
    """
    (canvas, progress) => {
      const state = canvas.__pag_cmp_state;
      const view = state && state.view;
      if (!view) return Promise.resolve();
      view.setProgress(progress);
      return view.flush();
    }
    """
)
private external fun pagSetProgress(canvas: HTMLCanvasElement, progress: Double): Promise<JsAny?>

@JsFun(
    """
    (canvas, repeatCount) => {
      const state = canvas.__pag_cmp_state;
      const view = state && state.view;
      if (view) view.setRepeatCount(repeatCount);
    }
    """
)
private external fun pagSetRepeatCount(canvas: HTMLCanvasElement, repeatCount: Int)

@JsFun(
    """
    (canvas, scaleMode) => {
      const state = canvas.__pag_cmp_state;
      const view = state && state.view;
      if (view) view.setScaleMode(scaleMode);
    }
    """
)
private external fun pagSetScaleMode(canvas: HTMLCanvasElement, scaleMode: Int)

@JsFun(
    """
    (canvas, left, top, width, height) => {
      const dpr = globalThis.devicePixelRatio || 1;
      // Compose gives device-pixel bounds on high-DPI displays. CSS layout needs CSS pixels,
      // while the canvas backing store should remain device-pixel sized for sharp rendering.
      const cssLeft = left / dpr;
      const cssTop = top / dpr;
      const cssWidth = width / dpr;
      const cssHeight = height / dpr;
      canvas.style.left = cssLeft + "px";
      canvas.style.top = cssTop + "px";
      canvas.style.width = cssWidth + "px";
      canvas.style.height = cssHeight + "px";
      canvas.width = Math.max(1, Math.round(width));
      canvas.height = Math.max(1, Math.round(height));
      const state = canvas.__pag_cmp_state;
      const view = state && state.view;
      if (view) {
        view.updateSize();
        if (!view.isPlaying) view.flush();
      }
    }
    """
)
private external fun pagSetCanvasBounds(
    canvas: HTMLCanvasElement,
    left: Double,
    top: Double,
    width: Double,
    height: Double,
)

@JsFun(
    """
    (canvas, enabled) => {
      const state = canvas.__pag_cmp_state;
      const view = state && state.view;
      if (view) view.setCacheEnabled(enabled);
    }
    """
)
private external fun pagSetCacheEnabled(canvas: HTMLCanvasElement, enabled: Boolean)

@JsFun(
    """
    (canvas, enabled) => {
      const state = canvas.__pag_cmp_state;
      const view = state && state.view;
      if (view) view.setVideoEnabled(enabled);
    }
    """
)
private external fun pagSetVideoEnabled(canvas: HTMLCanvasElement, enabled: Boolean)

@JsFun(
    """
    (canvas) => {
      const state = canvas.__pag_cmp_state;
      if (state && state.view) state.view.destroy();
      if (state && state.file) state.file.destroy();
      canvas.__pag_cmp_state = undefined;
    }
    """
)
private external fun pagDestroy(canvas: HTMLCanvasElement)

private object JsPagBridge {
    fun init(canvas: HTMLCanvasElement, bytes: ByteArray) =
        pagInit(canvas, bytes.size) { index -> bytes[index].toInt() }

    fun play(canvas: HTMLCanvasElement) = pagPlay(canvas)
    fun pause(canvas: HTMLCanvasElement) = pagPause(canvas)
    fun setProgress(canvas: HTMLCanvasElement, progress: Double) = pagSetProgress(canvas, progress)
    fun setRepeatCount(canvas: HTMLCanvasElement, repeatCount: Int) = pagSetRepeatCount(canvas, repeatCount)
    fun setScaleMode(canvas: HTMLCanvasElement, scaleMode: Int) = pagSetScaleMode(canvas, scaleMode)
    fun setCacheEnabled(canvas: HTMLCanvasElement, enabled: Boolean) = pagSetCacheEnabled(canvas, enabled)
    fun setVideoEnabled(canvas: HTMLCanvasElement, enabled: Boolean) = pagSetVideoEnabled(canvas, enabled)
    fun setCanvasBounds(canvas: HTMLCanvasElement, left: Double, top: Double, width: Double, height: Double) =
        pagSetCanvasBounds(canvas, left, top, width, height)

    fun destroy(canvas: HTMLCanvasElement) = pagDestroy(canvas)
}
