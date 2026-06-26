# lib-pag-cmp

`lib-pag-cmp` is a Compose Multiplatform wrapper for Tencent [libpag](https://github.com/Tencent/libpag).
It provides a common `PagView` composable and platform implementations for rendering PAG animation files.

[中文说明](./README_CN.md)

## Status

| Platform | Status | Rendering path |
| --- | --- | --- |
| Android | Supported | Native `org.libpag.PAGView` through `AndroidView` |
| iOS | Supported | Native `PAGView` through `UIKitView` |
| JVM Desktop | Supported on macOS arm64 | libpag offscreen render -> pixels -> Compose `ImageBitmap` |
| JS | Supported | libpag Web SDK canvas integration |
| WasmJS | Supported | libpag Web SDK canvas integration |

The JVM artifact currently bundles native runtime files only for `macos-arm64`.
Linux and Windows JVM native packages are not included yet.

## Installation

Add the repository that contains the published artifacts, then depend on the common KMP artifact:

```kotlin
repositories {
    mavenCentral()
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.limuyang2:lib-pag-cmp:0.1.0")
        }
    }
}
```

Platform publications are produced as:

- `io.github.limuyang2:lib-pag-cmp`
- `io.github.limuyang2:lib-pag-cmp-android`
- `io.github.limuyang2:lib-pag-cmp-jvm`
- `io.github.limuyang2:lib-pag-cmp-js`
- `io.github.limuyang2:lib-pag-cmp-wasm-js`
- `io.github.limuyang2:lib-pag-cmp-ios-arm64`
- `io.github.limuyang2:lib-pag-cmp-ios-simulator-arm64`

## Basic Usage

Load a PAG file as bytes and pass it to `PagView`:

```kotlin
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.limuyang2.libpag.cmp.PagScaleMode
import io.github.limuyang2.libpag.cmp.PagView

@Composable
fun LoadingAnimation(bytes: ByteArray) {
    PagView(
        bytes = bytes,
        modifier = Modifier.size(160.dp),
        isPlaying = true,
        repeatCount = 0,
        scaleMode = PagScaleMode.LetterBox,
    )
}
```

On Android, iOS, and JVM, repeated rendering of the same file can load a composition once and pass
it to `PagView`:

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import io.github.limuyang2.libpag.cmp.Pag
import io.github.limuyang2.libpag.cmp.PagView

@Composable
fun ReusedAnimation(bytes: ByteArray) {
    val composition = remember(bytes) { Pag.load(bytes) }

    DisposableEffect(composition) {
        onDispose { composition.close() }
    }

    PagView(composition = composition)
}
```

The JS and WasmJS implementations currently use `PagView(bytes)` as the supported Web path.

## Parameters

`PagView` exposes the same common parameters on every supported platform:

- `isPlaying`: starts or pauses playback.
- `progress`: optional manual progress in `0.0..1.0`. When set while paused, the current frame is flushed.
- `repeatCount`: native libpag repeat count. `0` means infinite repeat.
- `scaleMode`: controls how PAG content fits inside the render area.
- `cacheEnabled`: enables libpag render cache.
- `videoEnabled`: enables video sequence rendering when the PAG file contains video content.
- `useDiskCache`: enables libpag disk cache on platforms that expose it.

`scaleMode` only affects the content transform inside the native PAG renderer. It does not measure,
resize, or otherwise affect the Compose `PagView` layout bounds.

## Scale Modes

- `None`: keeps the composition at its original size and position.
- `Stretch`: scales width and height independently to fill the render area. Content may be distorted.
- `LetterBox`: scales uniformly until the whole composition is visible. Empty space may remain on one axis.
- `Zoom`: scales uniformly until the render area is fully covered. Content may be cropped on one axis.

## Web Runtime

The JS and WasmJS implementations expect the libpag Web SDK to be loaded before the Compose app starts.
The project demo loads the bundled SDK resources from the web app.

If your app hosts the library differently, make sure `libpag.min.js` and its wasm runtime are available
before `webApp.js` executes.

## JVM Runtime

The JVM implementation uses a JNI bridge and bundled native libpag runtime files. At runtime, the library
extracts those files from the jar and loads them with `System.load()`.

For local debugging, external native files can be supplied with JVM system properties:

```shell
-Dlibpag.cmp.libpag=/path/to/libpag
-Dlibpag.cmp.bridge=/path/to/libpag_cmp_jvm.dylib
```

See [BUILD.md](./BUILD.md) for details about rebuilding and replacing JVM native artifacts.

## Build Checks

Useful verification commands:

```shell
./gradlew :lib-pag-cmp:compileAndroidMain
./gradlew :lib-pag-cmp:compileKotlinIosArm64
./gradlew :lib-pag-cmp:compileKotlinIosSimulatorArm64
./gradlew :lib-pag-cmp:compileKotlinJvm
./gradlew :lib-pag-cmp:compileKotlinJs
./gradlew :lib-pag-cmp:compileKotlinWasmJs
```

Publication metadata can be checked with:

```shell
./gradlew :lib-pag-cmp:generatePomFileForWasmJsPublication
```
