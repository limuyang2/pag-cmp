package io.github.limuyang2.libpag.cmp

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
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
    val explicitLibpagPath = System.getProperty("libpag.cmp.libpag")?.takeIf { it.isNotBlank() }
    val explicitBridgePath = System.getProperty("libpag.cmp.bridge")?.takeIf { it.isNotBlank() }

    explicitLibpagPath?.let { path ->
        System.load(File(path).absolutePath)
    }

    explicitBridgePath?.let { path ->
        System.load(File(path).absolutePath)
        return
    }

    val bundledNative = BundledJvmNative.current()
    if (bundledNative != null) {
        if (explicitLibpagPath == null) {
            System.load(bundledNative.libpagPath)
        }
        System.load(bundledNative.bridgePath)
        return
    }

    System.loadLibrary("pag_cmp_jvm")
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
            val libpag = extractResource(
                resourcePath = "/native/$platform/libpag",
                target = nativeDir.resolve("libpag"),
            ) ?: return null
            val bridge = extractResource(
                resourcePath = "/native/$platform/libpag_cmp_jvm.dylib",
                target = nativeDir.resolve("libpag_cmp_jvm.dylib"),
            ) ?: return null
            return BundledJvmNative(
                libpagPath = libpag.absolutePathString(),
                bridgePath = bridge.absolutePathString(),
            )
        }

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

internal fun ByteArray.toImageBitmap(width: Int, height: Int): ImageBitmap {
    val bitmap = Bitmap()
    bitmap.allocPixels(
        ImageInfo(
            width = width,
            height = height,
            colorType = ColorType.BGRA_8888,
            alphaType = ColorAlphaType.PREMUL,
        )
    )
    check(bitmap.installPixels(this)) { "Failed to install PAG pixels into Skia bitmap." }
    return bitmap.asComposeImageBitmap()
}
