package io.github.limuyang2.myapplication

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.limuyang2.libpag.cmp.PagView
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.MissingResourceException
import pagcmp.shared.generated.resources.Res

@OptIn(ExperimentalResourceApi::class)
@Composable
@Preview
fun App() {
    MaterialTheme {
        var pagSamples by remember { mutableStateOf(PagSample.defaults()) }

        LaunchedEffect(Unit) {
            pagSamples = pagSamples.map { sample ->
                when (val source = sample.source) {
                    is PagSource.BytesResource -> {
                        runCatching { Res.readBytes(source.resourcePath) }
                            .fold(
                                onSuccess = { bytes ->
                                    sample.copy(source = source.copy(bytes = bytes), errorMessage = null)
                                },
                                onFailure = { throwable ->
                                    sample.copy(errorMessage = throwable.toPagLoadError())
                                },
                            )
                    }

                    is PagSource.LocalPath,
                    is PagSource.NetworkUrl -> sample
                }
            }
        }

        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .safeContentPadding()
                .fillMaxSize()
                .padding(PaddingValues(horizontal = 24.dp, vertical = 32.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = "PAG Compose Demo",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            pagSamples.forEach { sample ->
                PagSamplePreview(sample)
            }
        }
    }
}

@Composable
private fun PagSamplePreview(sample: PagSample) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = sample.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PagPreview(
            source = sample.source,
            errorMessage = sample.errorMessage,
        )
    }
}

private data class PagSample(
    val name: String,
    val source: PagSource,
    val errorMessage: String? = null,
) {
    companion object {
        fun defaults(): List<PagSample> = listOf(
            PagSample(
                name = "ByteArray",
                source = PagSource.BytesResource("files/8.pag"),
            ),
            PagSample(
                name = "Local path",
                source = PagSource.LocalPath(demoLocalPagPath("files/loading_bmp.pag")),
            ),
            PagSample(
                name = "Network URL",
                source = PagSource.NetworkUrl(
                    "https://github.com/limuyang2/pag-cmp/raw/refs/heads/main/shared/src/commonMain/composeResources/files/8.pag",
                ),
            ),
        )
    }
}

private sealed interface PagSource {
    data class BytesResource(
        val resourcePath: String,
        val bytes: ByteArray? = null,
    ) : PagSource

    data class LocalPath(val path: String) : PagSource

    data class NetworkUrl(val url: String) : PagSource
}

private fun Throwable.toPagLoadError(): String =
    when (this) {
        is MissingResourceException -> "PAG resource not found"
        else -> message ?: this::class.simpleName ?: "Failed to load PAG"
    }

@Composable
private fun PagPreview(
    source: PagSource,
    errorMessage: String?,
) {
    Box(
        modifier = Modifier
            .size(180.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        when {
            source is PagSource.BytesResource && source.bytes != null -> {
                PagView(
                    bytes = source.bytes,
                    modifier = Modifier.fillMaxSize(),
                    isPlaying = true,
                    repeatCount = 0,
                )
            }

            source is PagSource.LocalPath -> {
                PagView(
                    path = source.path,
                    modifier = Modifier.fillMaxSize(),
                    isPlaying = true,
                    repeatCount = 0,
                )
            }

            source is PagSource.NetworkUrl -> {
                PagView(
                    path = source.url,
                    modifier = Modifier.fillMaxSize(),
                    isPlaying = true,
                    repeatCount = 0,
                )
            }

            errorMessage != null -> ErrorText(errorMessage)

            else -> {
                Text(
                    text = "Loading PAG...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(
        text = message,
        modifier = Modifier.widthIn(max = 200.dp),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
    )
}
