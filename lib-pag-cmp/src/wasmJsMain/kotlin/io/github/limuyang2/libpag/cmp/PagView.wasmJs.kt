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
import kotlinx.coroutines.await
import web.dom.document
import web.html.HTMLCanvasElement
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.js.Promise

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
    var bounds by remember { mutableStateOf<WasmPagBounds?>(null) }
    // Compose Canvas on wasm cannot hand its Skia surface to libpag directly yet.
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
            WasmPagBridge.destroy(canvas)
            canvas.remove()
        }
    }

    LaunchedEffect(canvas, bytes) {
        WasmPagBridge.init(canvas, bytes).await<JsAny?>()
        WasmPagBridge.setRepeatCount(canvas, currentRepeatCount)
        WasmPagBridge.setScaleMode(canvas, currentScaleMode.toWebScaleMode())
        WasmPagBridge.setCacheEnabled(canvas, currentCacheEnabled)
        WasmPagBridge.setVideoEnabled(canvas, currentVideoEnabled)
        val explicitProgress = currentProgress
        if (explicitProgress != null) {
            WasmPagBridge.setProgress(canvas, explicitProgress.coerceIn(0.0, 1.0)).await<JsAny?>()
        }
        if (currentIsPlaying) {
            WasmPagBridge.play(canvas).await<JsAny?>()
        } else {
            WasmPagBridge.pause(canvas)
        }
    }

    LaunchedEffect(canvas, isPlaying, progress, repeatCount, scaleMode, cacheEnabled, videoEnabled) {
        WasmPagBridge.setRepeatCount(canvas, repeatCount)
        WasmPagBridge.setScaleMode(canvas, scaleMode.toWebScaleMode())
        WasmPagBridge.setCacheEnabled(canvas, cacheEnabled)
        WasmPagBridge.setVideoEnabled(canvas, videoEnabled)
        if (progress != null) {
            WasmPagBridge.setProgress(canvas, progress.coerceIn(0.0, 1.0)).await<JsAny?>()
        }
        if (isPlaying) {
            WasmPagBridge.play(canvas).await<JsAny?>()
        } else {
            WasmPagBridge.pause(canvas)
        }
    }

    LaunchedEffect(canvas, bounds) {
        bounds?.let { WasmPagBridge.setCanvasBounds(canvas, it.left, it.top, it.width, it.height) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                // Compose reports the placeholder bounds; the JS bridge maps them onto
                // the overlay canvas in CSS pixels and keeps the canvas buffer in device pixels.
                val rect = coordinates.boundsInWindow()
                bounds = WasmPagBounds(
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
    unsupportedPagPlatform("WasmJS PagView(composition)")
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
    // Fetch the path (a local resource URL or a network URL) into bytes, then reuse the
    // PagView(bytes) DOM-canvas renderer. Loading is async; nothing renders until bytes are ready.
    var bytes by remember(path) { mutableStateOf<ByteArray?>(null) }
    LaunchedEffect(path) {
        bytes = runCatching { WasmPagBridge.loadPathBytes(pathConvert(path)) }.getOrElse {
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

private data class WasmPagBounds(
    val left: Double,
    val top: Double,
    val width: Double,
    val height: Double,
)

private fun createPagCanvas(): HTMLCanvasElement {
    val canvas = document.createElement("canvas") as HTMLCanvasElement
    // boundsInWindow() is viewport-relative, so fixed positioning uses the same origin.
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
      return (async () => {
        if (!globalThis.libpag || !globalThis.libpag.PAGInit) {
          if (!globalThis.__pag_cmp_sdk_loading) {
            globalThis.__pag_cmp_sdk_loading = new Promise((resolve, reject) => {
              const script = globalThis.document.createElement("script");
              // libpag 4.5.70 uses import.meta internally, so it must be loaded as a module.
              script.type = "module";
              script.src = "pag/libpag-bootstrap.js";
              script.async = true;
              script.onload = resolve;
              script.onerror = () => reject(new Error("Failed to load local libpag Web SDK."));
              globalThis.document.head.appendChild(script);
            });
          }
          await globalThis.__pag_cmp_sdk_loading;
        }
        if (!globalThis.libpag || !globalThis.libpag.PAGInit) {
          throw new Error("libpag Web SDK is not loaded. Add libpag.min.js before webApp.js.");
        }
        if (!globalThis.__pag_cmp_module) {
          // PAGInit is expensive and owns the wasm module; share one initialized module per page.
          globalThis.__pag_cmp_module = globalThis.libpag.PAGInit();
        }
        const PAG = await globalThis.__pag_cmp_module;
        if (canvas[stateKey]?.view) {
          await canvas[stateKey].view.destroy();
        }
        const data = new Uint8Array(size);
        for (let i = 0; i < size; i++) data[i] = readByte(i) & 255;
        const arrayBuffer = data.buffer;
        const pagFile = await PAG.PAGFile.load(arrayBuffer);
        if (!pagFile) {
          throw new Error("PAGFile.load() returned empty result.");
        }
        const pagView = await PAG.PAGView.init(pagFile, canvas, { firstFrame: true, useScale: false });
        canvas[stateKey] = { file: pagFile, view: pagView };
      })();
    }
    """
)
private external fun pagInit(canvas: HTMLCanvasElement, size: Int, readByte: (Int) -> Int): Promise<JsAny?>

@JsFun(
    """
    (canvas) => {
      const view = canvas.__pag_cmp_state?.view;
      return view ? view.play() : Promise.resolve();
    }
    """
)
private external fun pagPlay(canvas: HTMLCanvasElement): Promise<JsAny?>

@JsFun(
    """
    (canvas) => {
      const view = canvas.__pag_cmp_state?.view;
      if (view) view.pause();
    }
    """
)
private external fun pagPause(canvas: HTMLCanvasElement)

@JsFun(
    """
    (canvas, progress) => {
      const view = canvas.__pag_cmp_state?.view;
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
      const view = canvas.__pag_cmp_state?.view;
      if (view) view.setRepeatCount(repeatCount);
    }
    """
)
private external fun pagSetRepeatCount(canvas: HTMLCanvasElement, repeatCount: Int)

@JsFun(
    """
    (canvas, scaleMode) => {
      const view = canvas.__pag_cmp_state?.view;
      if (view) view.setScaleMode(scaleMode);
    }
    """
)
private external fun pagSetScaleMode(canvas: HTMLCanvasElement, scaleMode: Int)

@JsFun(
    """
    (canvas, left, top, width, height) => {
      const dpr = globalThis.devicePixelRatio || 1;
      // Compose wasm gives device-pixel bounds on high-DPI displays. CSS layout needs CSS pixels,
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
      const view = canvas.__pag_cmp_state?.view;
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
      const view = canvas.__pag_cmp_state?.view;
      if (view) view.setCacheEnabled(enabled);
    }
    """
)
private external fun pagSetCacheEnabled(canvas: HTMLCanvasElement, enabled: Boolean)

@JsFun(
    """
    (canvas, enabled) => {
      const view = canvas.__pag_cmp_state?.view;
      if (view) view.setVideoEnabled(enabled);
    }
    """
)
private external fun pagSetVideoEnabled(canvas: HTMLCanvasElement, enabled: Boolean)

@JsFun(
    """
    (canvas) => {
      const state = canvas.__pag_cmp_state;
      if (state?.view) state.view.destroy();
      if (state?.file) state.file.destroy();
      canvas.__pag_cmp_state = undefined;
    }
    """
)
private external fun pagDestroy(canvas: HTMLCanvasElement)

@JsFun(
    """
    (path, onSize) => {
      const normalizePagUrl = (value) => {
        const url = new URL(value, globalThis.location.href);
        const parts = url.pathname.split("/").filter(Boolean);
        if (url.hostname === "github.com" && parts.length > 4 && parts[2] === "raw") {
          const owner = parts[0];
          const repo = parts[1];
          let ref;
          let fileStart;
          if (parts[3] === "refs" && parts[4] === "heads" && parts.length > 6) {
            ref = parts[5];
            fileStart = 6;
          } else {
            ref = parts[3];
            fileStart = 4;
          }
          return "https://raw.githubusercontent.com/" + owner + "/" + repo + "/" + ref + "/" + parts.slice(fileStart).join("/");
        }
        return url.toString();
      };
      const fetchPath = normalizePagUrl(path);
      fetch(fetchPath, { mode: "cors", redirect: "follow" }).then((res) => {
        if (!res.ok) throw new Error("Failed to fetch PAG: " + res.status + " " + res.statusText);
        return res.arrayBuffer();
      }).then((buffer) => {
        const bytes = new Uint8Array(buffer);
        globalThis.__pag_cmp_path_cache = globalThis.__pag_cmp_path_cache || {};
        globalThis.__pag_cmp_path_cache[path] = bytes;
        onSize(bytes.length);
      }).catch((error) => {
        console.error("PagView: failed to fetch PAG from path:", path, "normalized:", fetchPath, error);
        onSize(-1);
      });
    }
    """
)
private external fun fetchPagPathBytes(path: String, onSize: (Int) -> Unit)

@JsFun(
    """
    (path, index) => {
      const cache = globalThis.__pag_cmp_path_cache;
      const bytes = cache && cache[path];
      return bytes ? bytes[index] : 0;
    }
    """
)
private external fun readFetchedByte(path: String, index: Int): Int

@JsFun(
    """
    (path) => {
      const cache = globalThis.__pag_cmp_path_cache;
      if (cache) delete cache[path];
    }
    """
)
private external fun clearFetchedBytes(path: String)

private object WasmPagBridge {
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

    suspend fun loadPathBytes(path: String): ByteArray {
        val size = suspendCoroutine { cont ->
            fetchPagPathBytes(path) { fetched -> cont.resume(fetched) }
        }
        require(size > 0) { "PAG bytes must not be empty." }
        return ByteArray(size) { readFetchedByte(path, it).toByte() }.also { clearFetchedBytes(path) }
    }
}
