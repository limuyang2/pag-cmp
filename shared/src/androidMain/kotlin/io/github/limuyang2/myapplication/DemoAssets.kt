package io.github.limuyang2.myapplication

actual fun demoLocalPagPath(resourcePath: String): String =
    "assets://composeResources/pagcmp.shared.generated.resources/$resourcePath"
