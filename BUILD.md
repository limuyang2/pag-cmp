# Build Notes

This repository does not build Tencent/libpag as part of the normal Gradle build.
The libpag checkout is maintained separately, and this project only packages the
JVM runtime binaries that were copied into `lib-pag-cmp/src/jvmMain/resources`.

## JVM Native Artifacts

Current JVM support is packaged for macOS arm64:

```text
lib-pag-cmp/src/jvmMain/resources/native/macos-arm64/libpag
lib-pag-cmp/src/jvmMain/resources/native/macos-arm64/libpag_cmp_jvm.dylib
```

At runtime, `JvmPagNative` extracts these files from the JVM jar to a temporary
directory and loads them with `System.load()`. Consumers do not need to pass
native library paths for the bundled macOS arm64 build.

Manual overrides are still available for local debugging:

```shell
-Dlibpag.cmp.libpag=/path/to/libpag
-Dlibpag.cmp.bridge=/path/to/libpag_cmp_jvm.dylib
```

## Rebuilding libpag

The local libpag checkout currently lives at:

```text
/Users/mumu/projects/android/libpag
```

Build libpag independently:

```shell
cmake -S /Users/mumu/projects/android/libpag \
  -B /private/tmp/pag-cmp-libpag-build \
  -DPAG_BUILD_SHARED=ON \
  -DPAG_USE_C=OFF \
  -DPAG_BUILD_TESTS=OFF \
  -DCMAKE_BUILD_TYPE=Release

cmake --build /private/tmp/pag-cmp-libpag-build --target pag -j 8
```

The expected macOS output is:

```text
/private/tmp/pag-cmp-libpag-build/libpag.framework/Versions/A/libpag
```

## Rebuilding the JNI Bridge

After libpag is rebuilt, rebuild this repository's JNI bridge against that exact
libpag output:

```shell
cmake -S lib-pag-cmp/src/jvmMain/cpp \
  -B /private/tmp/pag-cmp-bridge-build \
  -DLIBPAG_ROOT=/Users/mumu/projects/android/libpag \
  -DLIBPAG_LIBRARY=/private/tmp/pag-cmp-libpag-build/libpag.framework/Versions/A/libpag \
  -DCMAKE_BUILD_TYPE=Release

cmake --build /private/tmp/pag-cmp-bridge-build -j 8
```

The expected bridge output is:

```text
/private/tmp/pag-cmp-bridge-build/libpag_cmp_jvm.dylib
```

## Updating Packaged Binaries

Copy both files into the JVM resources directory:

```shell
cp /private/tmp/pag-cmp-libpag-build/libpag.framework/Versions/A/libpag \
  lib-pag-cmp/src/jvmMain/resources/native/macos-arm64/libpag

cp /private/tmp/pag-cmp-bridge-build/libpag_cmp_jvm.dylib \
  lib-pag-cmp/src/jvmMain/resources/native/macos-arm64/libpag_cmp_jvm.dylib
```

Always replace `libpag` and `libpag_cmp_jvm.dylib` together. The bridge is built
against the libpag headers and binary, so mixing versions can fail at load time
or during rendering.

## Verification

Build the JVM jar and confirm the native resources are packaged:

```shell
./gradlew :lib-pag-cmp:jvmJar
jar tf lib-pag-cmp/build/libs/lib-pag-cmp-jvm.jar | rg 'native/macos-arm64'
```

Run the desktop demo without native path overrides:

```shell
./gradlew :desktopApp:run
```

For local debugging with external binaries:

```shell
JAVA_TOOL_OPTIONS='-Dlibpag.cmp.libpag=/private/tmp/pag-cmp-libpag-build/libpag.framework/Versions/A/libpag -Dlibpag.cmp.bridge=/private/tmp/pag-cmp-bridge-build/libpag_cmp_jvm.dylib' \
  ./gradlew :desktopApp:run
```

