# Repository Guidelines

## Project Structure & Module Organization

This is a Kotlin Multiplatform Compose project for wrapping Tencent libpag.

- `lib-pag-cmp/`: the published library module. Platform implementations live under `src/androidMain`, `src/iosMain`, `src/jvmMain`, `src/jsMain`, and `src/wasmJsMain`.
- `shared/`: shared demo UI and Compose resources. PAG sample files are under `shared/src/commonMain/composeResources/files/`.
- `androidApp/`, `desktopApp/`, `webApp/`: platform demo applications.
- `webApp/src/webMain/resources/pag/`: bundled libpag Web SDK files.
- `BUILD.md`: native JVM bridge build and packaging notes.

## Build, Test, and Development Commands

Use Gradle from the repository root:

```shell
./gradlew :lib-pag-cmp:compileAndroidMain
./gradlew :lib-pag-cmp:compileKotlinJvm
./gradlew :lib-pag-cmp:compileKotlinJs
./gradlew :lib-pag-cmp:compileKotlinWasmJs
./gradlew :lib-pag-cmp:compileKotlinIosArm64
./gradlew :lib-pag-cmp:compileKotlinIosSimulatorArm64
```

Run web demo builds with:

```shell
./gradlew :webApp:jsBrowserDevelopmentWebpack
./gradlew :webApp:wasmJsBrowserDevelopmentWebpack
```

Run tests with `./gradlew test` when adding test coverage. For publication metadata checks, use `./gradlew :lib-pag-cmp:generatePomFileForWasmJsPublication`.

## Coding Style & Naming Conventions

Use Kotlin style with 4-space indentation and trailing commas in multiline calls where already used. Keep common APIs in `commonMain` as `expect` declarations and add narrowly scoped `actual` implementations per target. Prefer clear names such as `PagView`, `PagScaleMode`, and platform prefixes only when disambiguation is needed.

Avoid broad refactors while changing platform behavior. Keep comments short and only where they explain platform-specific constraints.

## Testing Guidelines

There is no strict coverage threshold yet. Validate changes with the smallest relevant platform compile tasks, then broaden to all touched targets. For rendering or demo changes, verify the related app bundle task (`webApp`, `androidApp`, or `desktopApp`) in addition to library compilation.

## Commit & Pull Request Guidelines

Recent commits use Conventional Commit style, for example `feat(pag): ...`, `docs(readme): ...`, and `refactor(ui): ...`. Follow that pattern.

Pull requests should include a short summary, affected platforms, validation commands run, and screenshots or recordings for visible UI/demo changes. Mention native artifact changes explicitly, especially files under `lib-pag-cmp/src/jvmMain/resources/native/`.

## Agent-Specific Instructions

Do not overwrite generated or native binary artifacts unless the task explicitly requires it. The upstream libpag source is external to this repository; update packaged artifacts by rebuilding externally, then copying the resulting files into this repo.
