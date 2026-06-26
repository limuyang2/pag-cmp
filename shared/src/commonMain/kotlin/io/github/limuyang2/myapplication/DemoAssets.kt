package io.github.limuyang2.myapplication

/**
 * Returns a platform-specific local path/URI that `PagView(path)` can load for a Compose resource.
 */
expect fun demoLocalPagPath(resourcePath: String): String
