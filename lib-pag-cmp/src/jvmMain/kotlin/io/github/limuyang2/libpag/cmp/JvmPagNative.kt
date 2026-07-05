package io.github.limuyang2.libpag.cmp

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.outputStream

internal object JvmPagNative {
    init {
        loadNativeLibraries()
    }

    @JvmStatic
    external fun loadFile(bytes: ByteArray, filePath: String?): Long

    @JvmStatic
    external fun createPlayer(): Long

    @JvmStatic
    external fun compositionWidth(handle: Long): Int

    @JvmStatic
    external fun compositionHeight(handle: Long): Int

    @JvmStatic
    external fun compositionDurationUs(handle: Long): Long

    @JvmStatic
    external fun setComposition(playerHandle: Long, compositionHandle: Long)

    @JvmStatic
    external fun setCacheEnabled(playerHandle: Long, cacheEnabled: Boolean)

    @JvmStatic
    external fun setVideoEnabled(playerHandle: Long, videoEnabled: Boolean)

    @JvmStatic
    external fun setUseDiskCache(playerHandle: Long, useDiskCache: Boolean)

    @JvmStatic
    external fun renderToBgra(
        playerHandle: Long,
        width: Int,
        height: Int,
        progress: Double,
        scaleMode: Int,
        pixels: ByteArray,
    ): Boolean

    @JvmStatic
    external fun release(handle: Long)
}

private fun loadNativeLibraries() {
    try {
        loadNativeLibrariesOrThrow()
    } catch (throwable: UnsatisfiedLinkError) {
        throw IllegalStateException(
            "Failed to load libpag JVM native libraries. " +
                "Platform=${BundledJvmNative.platformDescription()}. " +
                "Set -Dlibpag.cmp.libpag and -Dlibpag.cmp.bridge to debug with external binaries.",
            throwable,
        )
    }
}

private fun loadNativeLibrariesOrThrow() {
    val explicitLibpagPath = System.getProperty("libpag.cmp.libpag")?.takeIf { it.isNotBlank() }
    val explicitBridgePath = System.getProperty("libpag.cmp.bridge")?.takeIf { it.isNotBlank() }

    // 调试时允许绕过 jar 内置资源，直接加载外部构建产物。
    explicitLibpagPath?.let { path ->
        loadNativeFile(path, "libpag.cmp.libpag")
    }

    explicitBridgePath?.let { path ->
        loadNativeFile(path, "libpag.cmp.bridge")
        return
    }

    val bundledNative = BundledJvmNative.current()
    if (bundledNative != null) {
        // 正常消费路径：从 JVM jar 中释放 native 文件到临时目录再加载。
        if (explicitLibpagPath == null) {
            loadNativePath(bundledNative.libpagPath)
        }
        loadNativePath(bundledNative.bridgePath)
        return
    }

    System.loadLibrary("pag_cmp_jvm")
}

private fun loadNativeFile(path: String, propertyName: String) {
    val file = File(path).absoluteFile
    check(file.isFile) {
        "Native library path from -D$propertyName does not exist or is not a file: ${file.path}"
    }
    loadNativePath(file.path)
}

@Suppress("UnsafeDynamicallyLoadedCode")
private fun loadNativePath(path: String) {
    // Bundled JVM natives are extracted from this library's own jar into a private temp directory.
    // System.loadLibrary() cannot address these absolute files or the explicit debug paths.
    System.load(path)
}

private data class BundledJvmNative(
    val libpagPath: String,
    val bridgePath: String,
) {
    companion object {
        fun current(): BundledJvmNative? {
            val platform = currentPlatform() ?: return null
            val nativeDir = Files.createTempDirectory("libpag-cmp-$platform-").apply {
                toFile().deleteOnExit()
            }
            val libpagResource = "/native/$platform/libpag"
            val bridgeResource = "/native/$platform/libpag_cmp_jvm.dylib"
            val libpag = extractResource(
                resourcePath = libpagResource,
                target = nativeDir.resolve("libpag"),
            ) ?: throw UnsatisfiedLinkError("Bundled native resource is missing: $libpagResource")
            val bridge = extractResource(
                resourcePath = bridgeResource,
                target = nativeDir.resolve("libpag_cmp_jvm.dylib"),
            ) ?: throw UnsatisfiedLinkError("Bundled native resource is missing: $bridgeResource")
            return BundledJvmNative(
                libpagPath = libpag.absolutePathString(),
                bridgePath = bridge.absolutePathString(),
            )
        }

        fun platformDescription(): String =
            "${System.getProperty("os.name")} ${System.getProperty("os.arch")}"

        private fun currentPlatform(): String? {
            val os = System.getProperty("os.name").lowercase()
            val arch = System.getProperty("os.arch").lowercase()
            return when {
                os.contains("mac") && (arch == "aarch64" || arch == "arm64") -> "macos-arm64"
                else -> null
            }
        }

        private fun extractResource(resourcePath: String, target: Path): Path? {
            val input = BundledJvmNative::class.java.getResourceAsStream(resourcePath) ?: return null
            target.parent.createDirectories()
            input.use { source ->
                target.outputStream().use { destination ->
                    source.copyTo(destination)
                }
            }
            target.toFile().apply {
                setReadable(true)
                setExecutable(true)
                deleteOnExit()
            }
            return target.takeIf { it.exists() }
        }
    }
}

internal class JvmPagFrameBuffer {
    private var size: PagSize? = null
    private var pixels = ByteArray(0)
    private var imageBitmap: ImageBitmap? = null
    private var canvas: Canvas? = null
    private val paint = Paint().apply {
        isAntiAlias = false
    }

    fun pixelsFor(size: PagSize): ByteArray {
        ensureAllocated(size)
        return pixels
    }

    fun updateImage(size: PagSize): ImageBitmap {
        ensureAllocated(size)
        drawPixels(size)
        return requireNotNull(imageBitmap)
    }

    private fun ensureAllocated(newSize: PagSize) {
        if (size == newSize) return

        // 只有尺寸变化时才重建大对象；普通帧只更新像素内容。
        size = newSize
        pixels = ByteArray(newSize.width * newSize.height * BytesPerPixel)
        imageBitmap = ImageBitmap(newSize.width, newSize.height)
        canvas = Canvas(requireNotNull(imageBitmap))
    }

    private fun drawPixels(size: PagSize) {
        val currentCanvas = requireNotNull(canvas)
        var offset = 0
        for (y in 0 until size.height) {
            var runStartX = 0
            var runColor = argbAt(offset)
            var x = 1
            offset += BytesPerPixel
            while (x < size.width) {
                val color = argbAt(offset)
                if (color != runColor) {
                    drawRun(currentCanvas, y, runStartX, x, runColor)
                    runStartX = x
                    runColor = color
                }
                x++
                offset += BytesPerPixel
            }
            drawRun(currentCanvas, y, runStartX, size.width, runColor)
        }
    }

    private fun drawRun(canvas: Canvas, y: Int, startX: Int, endX: Int, argb: Int) {
        paint.color = Color(argb)
        canvas.drawRect(
            left = startX.toFloat(),
            top = y.toFloat(),
            right = endX.toFloat(),
            bottom = (y + 1).toFloat(),
            paint = paint,
        )
    }

    private fun argbAt(offset: Int): Int {
        val blue = pixels[offset].toInt() and 0xff
        val green = pixels[offset + 1].toInt() and 0xff
        val red = pixels[offset + 2].toInt() and 0xff
        val alpha = pixels[offset + 3].toInt() and 0xff
        if (alpha == 0) return 0
        if (alpha == 255) {
            return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
        }
        val unpremultipliedRed = unpremultiply(red, alpha)
        val unpremultipliedGreen = unpremultiply(green, alpha)
        val unpremultipliedBlue = unpremultiply(blue, alpha)
        return (alpha shl 24) or
            (unpremultipliedRed shl 16) or
            (unpremultipliedGreen shl 8) or
            unpremultipliedBlue
    }

    private companion object {
        const val BytesPerPixel = 4

        fun unpremultiply(component: Int, alpha: Int): Int =
            ((component * 255) + (alpha / 2)) / alpha
    }
}
