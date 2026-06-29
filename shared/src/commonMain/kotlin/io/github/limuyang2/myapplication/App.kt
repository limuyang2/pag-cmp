package io.github.limuyang2.myapplication

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.limuyang2.libpag.cmp.PagView
import org.jetbrains.compose.resources.MissingResourceException
import pagcmp.shared.generated.resources.Res

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
                .padding(PaddingValues(horizontal = 20.dp, vertical = 24.dp)),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "PAG Compose Demo",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            PagSampleFlow(samples = pagSamples)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PagSampleFlow(samples: List<PagSample>) {
    val scrollState = rememberScrollState()

    FlowRow(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        samples.forEach { sample ->
            PagSampleCard(sample)
        }
    }
}

@Composable
private fun PagSampleCard(sample: PagSample) {
    Column(
        modifier = Modifier
            .width(SampleCardWidth)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PagPreview(
            source = sample.source,
            errorMessage = sample.errorMessage,
        )
        Text(
            text = sample.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        MetaText(label = "Source", value = sample.source.label)
        MetaText(label = "Target", value = sample.source.target, maxLines = 2)
        MetaText(
            label = "Status",
            value = sample.errorMessage ?: sample.statusText,
            isError = sample.errorMessage != null,
        )
    }
}

@Composable
private fun MetaText(
    label: String,
    value: String,
    maxLines: Int = 1,
    isError: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
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
                source = PagSource.LocalPath(Res.getUri("files/loading_bmp.pag")),
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
    val label: String
    val target: String

    data class BytesResource(
        val resourcePath: String,
        val bytes: ByteArray? = null,
    ) : PagSource {
        override val label: String = "ByteArray"
        override val target: String = resourcePath
    }

    data class LocalPath(val path: String) : PagSource {
        override val label: String = "Local path"
        override val target: String = path
    }

    data class NetworkUrl(val url: String) : PagSource {
        override val label: String = "Network URL"
        override val target: String = url
    }
}

private val PagSample.statusText: String
    get() = when {
        errorMessage != null -> "Failed"
        source is PagSource.BytesResource && source.bytes == null -> "Loading"
        else -> "Rendering"
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
            .size(PreviewSize)
            .clip(RoundedCornerShape(6.dp))
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
        modifier = Modifier.widthIn(max = 96.dp),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}

private val SampleCardWidth = 220.dp
private val PreviewSize = 196.dp
