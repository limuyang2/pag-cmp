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
        var pagBytes by remember { mutableStateOf<ByteArray?>(null) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(Unit) {
            runCatching { Res.readBytes("files/8.pag") }
                .onSuccess { bytes -> pagBytes = bytes }
                .onFailure { throwable ->
                    errorMessage = when (throwable) {
                        is MissingResourceException -> "PAG resource not found"
                        else -> throwable.message ?: throwable::class.simpleName ?: "Failed to load PAG"
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
            Text(
                text = "home_ai-float.pag",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PagPreview(
                bytes = pagBytes,
                errorMessage = errorMessage,
            )
        }
    }
}

@Composable
private fun PagPreview(
    bytes: ByteArray?,
    errorMessage: String?,
) {
    Box(
        modifier = Modifier
            .size(230.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        when {
            bytes != null -> {
                PagView(
                    bytes = bytes,
                    modifier = Modifier.fillMaxSize(),
                    isPlaying = true,
                    repeatCount = 0,
                )
            }

            errorMessage != null -> {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

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
