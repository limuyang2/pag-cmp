# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

`lib-pag-cmp` 是 Tencent [libpag](https://github.com/Tencent/libpag)（**4.5.70**）的 Compose Multiplatform 封装，提供统一的 `PagView` Composable，各平台用对应的 libpag 能力渲染 PAG 动画。仓库同时包含一个跨平台 demo 来验证库。

## 模块结构

- **`lib-pag-cmp`** — KMP 库本体。`commonMain` 声明 `expect` API，各平台 source set 提供 `actual`。
- **`shared`** — demo 的跨平台 Compose 应用（`App.kt`），依赖 `lib-pag-cmp`。包含 Android/iOS/JVM/JS/WasmJS 全部 source set。
- **`androidApp` / `desktopApp` / `webApp`** — 各平台壳工程，依赖 `shared`。
- **`iosApp`** — Xcode 工程（非 Gradle），消费 `shared` 导出的 `Shared` framework。

## 构建与运行

Kotlin **2.4.0** / Compose Multiplatform **1.11.1** / AGP **9.0.1**。Gradle 工具链通过 foojay 自动下载 JDK。

```shell
# 编译库（验证 expect/actual 齐全、签名匹配）—— 改动后必跑
./gradlew :lib-pag-cmp:compileKotlinJs :lib-pag-cmp:compileKotlinWasmJs \
          :lib-pag-cmp:compileKotlinJvm :lib-pag-cmp:compileKotlinIosArm64 \
          :lib-pag-cmp:compileKotlinIosSimulatorArm64 :lib-pag-cmp:compileAndroidMain

# 编译 demo（连带编译 lib-pag-cmp）
./gradlew :shared:compileKotlinJs :shared:compileKotlinWasmJs :shared:compileKotlinJvm \
          :shared:compileKotlinIosArm64 :shared:compileKotlinIosSimulatorArm64 :shared:compileAndroidMain

# 运行 demo
./gradlew :webApp:jsBrowserDevelopmentRun        # Kotlin/JS（demo 主要验证平台）
./gradlew :webApp:wasmJsBrowserDevelopmentRun    # WasmJS
./gradlew :desktopApp:run                        # JVM Desktop
./gradlew :androidApp:assembleDebug              # Android APK
```

注意：Android target 的编译任务名是 `compileAndroidMain`（`com.android.kotlin.multiplatform.library` 插件，不是 `compileKotlinAndroid`）。

## 架构核心：PagView expect/actual

`commonMain` 的 `PagView.kt` 声明**三个 `expect` 重载**，首参分别是 `ByteArray` / `PagComposition` / `String path`。改任何一个签名，**必须同步所有 5 个平台的 `actual`**（androidMain / iosMain / jvmMain / jsMain / wasmJsMain），否则编译失败。

各平台渲染路径（差异是本库的核心复杂度）：

| 平台 | 渲染方式 | bytes | composition | path |
|---|---|---|---|---|
| Android | `AndroidView` + `org.libpag.PAGView` | ✓ | ✓ `setComposition` | ✓ `setPathAsync`（原生支持本地+网络+`assets://`） |
| iOS | `UIKitView` + libpag `PAGView` | ✓ | ✓ `setComposition` | ✓ `setPathAsync`（本地确定，网络 best-effort） |
| JVM | `Canvas` 离屏渲染（JNI）| ✓ | ✓ player | 读 `File`/`URL` 成 bytes 后委托 `PagView(bytes)` |
| JS / WasmJS | 覆盖在 Compose 占位上的 DOM canvas + libpag Web SDK | ✓ | ✗ unsupported | `fetch(path)` 成 bytes 后委托 `PagView(bytes)` |

**`PagView(path)` 的平台策略**：Android/iOS 直接用 libpag 原生 `setPathAsync`（不读 bytes）；JVM/JS/WasmJS 把 path 读/fetch 成 bytes 再复用 `PagView(bytes)` 渲染。所以新增渲染相关改动时，Android/iOS 走 view 骨架，JVM/Web 走委托——两者实现模式不同。

## 关键实现细节（容易踩坑）

- **iOS libpag 引入**：通过 Swift Package Manager（`lib-pag-cmp/build.gradle.kts` 的 SwiftPM 配置），cinterop 包名是 `swiftPMImport.io.github.limuyang2.lib.pag.cmp`。调用 libpag API 时 import 这个包。
- **JVM JNI bridge**：`lib-pag-cmp/src/jvmMain/cpp/`（`pag_cmp_jvm.cpp` + CMake）是 Kotlin↔libpag 的 bridge；native 产物在 `src/jvmMain/resources/native/macos-arm64/`（**仅 macOS arm64**，无 Linux/Windows）。运行时 `JvmPagNative` 解压并 `System.load()`。本地调试可用 `-Dlibpag.cmp.libpag=...` `-Dlibpag.cmp.bridge=...` 指定外部产物。重新构建 native 见 **`BUILD.md`**。
- **Web SDK**：libpag Web SDK 文件打包在 `webApp/src/webMain/resources/pag/`，运行时按需加载 `pag/libpag-bootstrap.js`。`jsMain`/`wasmJsMain` 通过 `@JsFun` 调 JS（DOM canvas + fetch）。
- **Web canvas 尺寸/dpr**：`boundsInWindow()` 在高 DPI 屏返回**设备像素**，DOM canvas 定位要 `÷ devicePixelRatio` 转 CSS 像素，缓冲区保持设备像素（见 `pagSetCanvasBounds` 的 js/wasmJs 两份实现）。
- **wasmJs 的 `Promise<T>`**：wasmJs 下 `Promise<T>` 要求 `T : JsAny?`，不能用 `Promise<Int>`。异步拿基本类型用回调 + `suspendCoroutine`（对比 js target 的 `Promise<Int>.await()`，两份实现有差异）。
- **Demo 的 `demoLocalPagPath()`**：`shared` 里的 `expect/actual`，给 demo 的 `PagView(path)` 提供平台本地 path（JS/WasmJS 返回 webApp 同源静态资源路径，native 返回平台合适的本地路径）。demo 用 `PagSource`（ByteArray / LocalPath / NetworkUrl 三种）展示三种加载路径。

## 代码约定

- 提交信息用中文 Conventional Commits（如 `feat(lib-pag-cmp): ...`、`docs(readme): ...`、`chore(shared): ...`）。直接提交到 `main`（个人库项目，不开 PR）。
- 各平台 `actual` 间大量复用：Android/iOS 的 `PagView(composition)` update 逻辑抽成 `applyPagViewState` 扩展共享；JVM/JS/WasmJS 的 `PagView(path)` 委托 `PagView(bytes)` 零渲染重复。新增能力时优先复用现有渲染路径。
- README 中英双份（`README.md` / `README_CN.md`），改动 API/平台支持/用法时两份都要更新。
