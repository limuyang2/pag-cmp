# lib-pag-cmp

[![Maven Central](https://img.shields.io/maven-central/v/io.github.limuyang2/lib-pag-cmp.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.limuyang2/lib-pag-cmp)
[![Context7](https://img.shields.io/badge/Context7-AI-5D5FEC.svg)](https://context7.com/limuyang2/pag-cmp)

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
            implementation("io.github.limuyang2:lib-pag-cmp:0.1.3")
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

上面的示例直接接收 `bytes`。这是最通用的加载方式，当前所有支持平台都可用。

要播放本地 `.pag` 文件，把它放到
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

也可以直接用 `PagView(path)` 传入本地文件路径、Compose resource URI 或网络 URL，库会异步加载
（加载完成前不渲染；加载失败会记日志而非抛异常）：

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

如果 `.pag` 文件放在 `src/commonMain/composeResources/files/` 下，传入
`Res.getUri("files/name.pag")` 返回的 URI：

```kotlin
@Composable
fun ResourcePathAnimation() {
    PagView(
        path = Res.getUri("files/loading_bmp.pag"),
        modifier = Modifier.size(160.dp),
        scaleMode = PagScaleMode.LetterBox,
    )
}
```

不要把原始资源相对路径（`"files/loading_bmp.pag"`）直接传给 `PagView(path)`。`path` 必须是
`Res.getUri(...)` 返回的平台可访问本地路径/URI，或者 `http`/`https` 网络 URL。

网络 URL 的行为与平台有关：

- Android 使用 libpag 原生 path loader。
- JVM 通过 JDK 下载，再复用 bytes 渲染路径。
- JS 和 WasmJS 使用浏览器 `fetch`，远端地址必须能被浏览器访问，并且满足 CORS。
- iOS 使用 libpag 原生 path loader，网络加载为 best-effort。

如果 PAG 文件托管在 GitHub，推荐使用 `raw.githubusercontent.com` 直链。Web 实现也会把常见的
`github.com/.../raw/...` 链接规范化后再发起 fetch。

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

JS 和 WasmJS 当前支持 `PagView(bytes)` 和 `PagView(path)`。`PagView(composition)` 暂不支持 Web target。

## Demo

当前 demo 验证三种加载路径：

- `ByteArray`：`Res.readBytes("files/8.pag")` -> `PagView(bytes)`。
- `Local path`：`Res.getUri("files/loading_bmp.pag")` -> `PagView(path)`。
- `Network URL`：远端 PAG URL -> `PagView(path)`。

样例使用可滚动的 `FlowRow` 布局展示，后续扩展只需要在 demo 数据里继续追加新的 `PagSample`。

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

JS 和 WasmJS 通过 libpag Web SDK 渲染到覆盖在 Compose 占位区域上的 DOM canvas。
当前 demo 把 SDK 文件打包在 `webApp/src/webMain/resources/pag/` 下，并按需加载
`pag/libpag-bootstrap.js`。

如果业务 app 使用自己的托管方式，需要确保 libpag Web SDK 的 JavaScript 和 wasm runtime
能被页面访问到。浏览器加载网络 PAG 时遵循普通 Web 规则：URL 必须可访问，并且需要通过 CORS。

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
