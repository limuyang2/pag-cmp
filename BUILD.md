# 构建说明

当前仓库不在 Gradle 构建流程里自动编译 Tencent/libpag。libpag 源码单独维护，
本仓库只负责打包已经复制进来的 JVM 运行时 native 产物。

## JVM Native 产物

目前 JVM 只打包 macOS arm64：

```text
lib-pag-cmp/src/jvmMain/resources/native/macos-arm64/libpag
lib-pag-cmp/src/jvmMain/resources/native/macos-arm64/libpag_cmp_jvm.dylib
```

运行时 `JvmPagNative` 会把这两个文件从 JVM jar 解压到临时目录，然后通过
`System.load()` 加载。正常消费方不需要手动传 native 路径。

本地调试时仍然可以使用外部构建产物：

```shell
-Dlibpag.cmp.libpag=/path/to/libpag
-Dlibpag.cmp.bridge=/path/to/libpag_cmp_jvm.dylib
```

## 单独构建 libpag

本机 libpag 源码目录：

```text
/Users/mumu/projects/android/libpag
```

独立配置并构建 libpag：

```shell
cmake -S /Users/mumu/projects/android/libpag \
  -B /private/tmp/pag-cmp-libpag-build \
  -DPAG_BUILD_SHARED=ON \
  -DPAG_USE_C=OFF \
  -DPAG_BUILD_TESTS=OFF \
  -DCMAKE_BUILD_TYPE=Release

cmake --build /private/tmp/pag-cmp-libpag-build --target pag -j 8
```

macOS 预期产物：

```text
/private/tmp/pag-cmp-libpag-build/libpag.framework/Versions/A/libpag
```

## 构建 JNI Bridge

libpag 更新后，需要用同一份 libpag 源码和产物重新构建本仓库的 JNI bridge：

```shell
cmake -S lib-pag-cmp/src/jvmMain/cpp \
  -B /private/tmp/pag-cmp-bridge-build \
  -DLIBPAG_ROOT=/Users/mumu/projects/android/libpag \
  -DLIBPAG_LIBRARY=/private/tmp/pag-cmp-libpag-build/libpag.framework/Versions/A/libpag \
  -DCMAKE_BUILD_TYPE=Release

cmake --build /private/tmp/pag-cmp-bridge-build -j 8
```

预期 bridge 产物：

```text
/private/tmp/pag-cmp-bridge-build/libpag_cmp_jvm.dylib
```

## 替换打包产物

每次更新时，把 `libpag` 和 `libpag_cmp_jvm.dylib` 一起复制进 JVM resources：

```shell
cp /private/tmp/pag-cmp-libpag-build/libpag.framework/Versions/A/libpag \
  lib-pag-cmp/src/jvmMain/resources/native/macos-arm64/libpag

cp /private/tmp/pag-cmp-bridge-build/libpag_cmp_jvm.dylib \
  lib-pag-cmp/src/jvmMain/resources/native/macos-arm64/libpag_cmp_jvm.dylib
```

不要只替换其中一个文件。`libpag_cmp_jvm.dylib` 是按指定 libpag 头文件和二进制构建的，
混用版本可能在加载或渲染时失败。

## 验证

构建 JVM jar，并确认 native 资源已经进入 jar：

```shell
./gradlew :lib-pag-cmp:jvmJar
jar tf lib-pag-cmp/build/libs/lib-pag-cmp-jvm.jar | rg 'native/macos-arm64'
```

不传 native 参数运行 desktop demo：

```shell
./gradlew :desktopApp:run
```

使用外部 native 产物调试：

```shell
JAVA_TOOL_OPTIONS='-Dlibpag.cmp.libpag=/private/tmp/pag-cmp-libpag-build/libpag.framework/Versions/A/libpag -Dlibpag.cmp.bridge=/private/tmp/pag-cmp-bridge-build/libpag_cmp_jvm.dylib' \
  ./gradlew :desktopApp:run
```

## 当前限制

- JVM native 产物目前只打包 `macos-arm64`。
- JVM 渲染路径是 `libpag -> readPixels(BGRA) -> Skia Bitmap -> Compose ImageBitmap`。
- native 产物采用手动构建、手动复制的方式，不随本仓库 Gradle 自动构建。
- 暂不做 libpag 和 Skiko 的 GPU 直连。

