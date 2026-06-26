# lib-pag-cmp

`lib-pag-cmp` 是 Tencent [libpag](https://github.com/Tencent/libpag) 的 Compose Multiplatform 封装。
它提供 common 层的 `PagView` Composable，并在不同平台使用对应的 libpag 渲染能力显示 PAG 动画文件。

[English](./README.md)

各平台实现均基于 Tencent 原版 [libpag](https://github.com/Tencent/libpag) **4.5.70** 构建。

## 当前状态

| 平台 | 状态 | 渲染路径 |
| --- | --- | --- |
| Android | 已支持 | 原生 `org.libpag.PAGView` + `AndroidView` |
| iOS | 已支持 | 原生 `PAGView` + `UIKitView` |
| JVM Desktop | 已支持 macOS arm64 | libpag 离屏渲染 -> 像素读取 -> Compose `ImageBitmap` |
| JS | 已支持 | libpag Web SDK canvas 集成 |
| WasmJS | 已支持 | libpag Web SDK canvas 集成 |

JVM artifact 目前只打包了 `macos-arm64` native 运行时文件。
Linux 和 Windows 的 JVM native 包还没有加入。

## 安装

添加发布仓库后，在 common source set 依赖 KMP 主 artifact：

```kotlin
repositories {
    mavenCentral()
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.limuyang2:lib-pag-cmp:0.1.1")
        }
    }
}
```

当前发布产物包括：

- `io.github.limuyang2:lib-pag-cmp`
- `io.github.limuyang2:lib-pag-cmp-android`
- `io.github.limuyang2:lib-pag-cmp-jvm`
- `io.github.limuyang2:lib-pag-cmp-js`
- `io.github.limuyang2:lib-pag-cmp-wasm-js`
- `io.github.limuyang2:lib-pag-cmp-ios-arm64`
- `io.github.limuyang2:lib-pag-cmp-ios-simulator-arm64`

## 基础用法

读取 PAG 文件为 bytes，然后传给 `PagView`：

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

上面的示例直接接收 `bytes`。要播放本地 `.pag` 文件，把它放到
`src/commonMain/composeResources/files/` 下，再用 Compose Multiplatform 资源 API
（`org.jetbrains.compose.components:components-resources`）读取——这套方式在所有平台都通用：

```kotlin
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.limuyang2.libpag.cmp.PagScaleMode
import io.github.limuyang2.libpag.cmp.PagView
import org.jetbrains.compose.resources.ExperimentalResourceApi
// `Res` 由 Compose resources 插件生成，包名取决于你的模块配置。
import your.module.generated.resources.Res

@OptIn(ExperimentalResourceApi::class)
@Composable
fun LocalAnimation() {
    var bytes by remember { mutableStateOf<ByteArray?>(null) }
    LaunchedEffect(Unit) {
        bytes = Res.readBytes("files/loading.pag")
    }
    bytes?.let {
        PagView(
            bytes = it,
            modifier = Modifier.size(160.dp),
            scaleMode = PagScaleMode.LetterBox,
        )
    }
}
```

也可以直接用 `PagView(path)` 传入本地文件路径或网络 URL，库会异步加载（加载完成前不渲染；
加载失败会记日志而非抛异常）：

```kotlin
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.limuyang2.libpag.cmp.PagScaleMode
import io.github.limuyang2.libpag.cmp.PagView

@Composable
fun RemoteAnimation(url: String) {
    PagView(
        path = url,
        modifier = Modifier.size(160.dp),
        scaleMode = PagScaleMode.LetterBox,
    )
}
```

`path` 在所有平台都支持本地文件路径；网络 URL（`http`/`https`）在 Android、JS、WasmJS 上支持
（JVM 通过 JDK 下载；iOS 网络支持为 best-effort）。

在 Android、iOS 和 JVM 上，如果同一个 PAG 文件会反复渲染，可以先加载 `PagComposition`，
再传给 `PagView`：

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

JS 和 WasmJS 当前支持的 Web 路径是 `PagView(bytes)`。

## 参数说明

`PagView` 在各个支持平台暴露同一组 common 参数：

- `isPlaying`：开始或暂停播放。
- `progress`：可选手动进度，范围为 `0.0..1.0`。暂停时设置进度会刷新当前帧。
- `repeatCount`：libpag 原生重复次数。`0` 表示无限循环。
- `scaleMode`：控制 PAG 内容如何适配渲染区域。
- `cacheEnabled`：启用 libpag 渲染缓存。
- `videoEnabled`：PAG 文件包含视频序列时，控制是否启用视频渲染。
- `useDiskCache`：在平台支持时启用 libpag 磁盘缓存。

`scaleMode` 只影响 PAG 原生渲染器内部的内容变换，不负责测量、改变或约束 Compose `PagView`
自己的布局尺寸。

## ScaleMode

- `None`：保持 PAG composition 的原始尺寸和位置。
- `Stretch`：宽高分别拉伸到填满渲染区域，内容可能变形。
- `LetterBox`：等比缩放到完整显示内容，某个方向可能留空。
- `Zoom`：等比缩放到覆盖整个渲染区域，某个方向可能被裁剪。

## Web 运行时

JS 和 WasmJS 实现要求 libpag Web SDK 在 Compose app 启动前加载完成。
当前 demo 会从 web app 里加载打包的 SDK 资源。

如果业务 app 使用自己的托管方式，需要确保 `libpag.min.js` 以及对应 wasm runtime
在 `webApp.js` 执行前可用。

## JVM 运行时

JVM 实现使用 JNI bridge 和打包进 jar 的 native libpag 运行时文件。
运行时库会把这些文件解压到临时目录，然后通过 `System.load()` 加载。

本地调试可以通过 JVM system properties 指定外部 native 文件：

```shell
-Dlibpag.cmp.libpag=/path/to/libpag
-Dlibpag.cmp.bridge=/path/to/libpag_cmp_jvm.dylib
```

JVM native 产物的重新构建和替换方式见 [BUILD.md](./BUILD.md)。

## 构建验证

常用验证命令：

```shell
./gradlew :lib-pag-cmp:compileAndroidMain
./gradlew :lib-pag-cmp:compileKotlinIosArm64
./gradlew :lib-pag-cmp:compileKotlinIosSimulatorArm64
./gradlew :lib-pag-cmp:compileKotlinJvm
./gradlew :lib-pag-cmp:compileKotlinJs
./gradlew :lib-pag-cmp:compileKotlinWasmJs
```

发布 metadata 可以用下面命令检查：

```shell
./gradlew :lib-pag-cmp:generatePomFileForWasmJsPublication
```
