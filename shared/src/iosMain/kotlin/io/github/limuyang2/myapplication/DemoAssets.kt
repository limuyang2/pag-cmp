package io.github.limuyang2.myapplication

import pagcmp.shared.generated.resources.Res

actual fun demoLocalPagPath(resourcePath: String): String =
    Res.getUri(resourcePath).removePrefix("file://")
