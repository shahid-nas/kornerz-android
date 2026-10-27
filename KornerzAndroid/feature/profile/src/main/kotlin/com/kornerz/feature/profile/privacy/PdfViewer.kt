package com.kornerz.feature.profile.privacy

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Renders every page of the bundled privacy policy PDF asset stacked
 * vertically inside a scrollable container.
 *
 * The document lives in the module assets (`src/main/assets/privacy/privacy_en.pdf`)
 * and is rendered with the platform [PdfRenderer], so no extra PDF dependency
 * is required. Pages are decoded off the main thread and drawn at width-fill
 * so a single page spans the screen.
 */
@Composable
fun PdfViewer(
    assetPath: String,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    scale: Int = 2,
) {
    val context = LocalContext.current
    var pages by remember(assetPath) { mutableStateOf<List<Bitmap>>(emptyList()) }
    var failed by remember(assetPath) { mutableStateOf(false) }

    LaunchedEffect(assetPath) {
        val result = withContext(Dispatchers.IO) {
            runCatching { renderPdfFromAssets(context, assetPath, scale) }
        }
        result.onSuccess {
            pages = it
            failed = false
        }.onFailure {
            pages = emptyList()
            failed = true
        }
    }

    Column(
        modifier = modifier.verticalScroll(scrollState),
    ) {
        pages.forEach { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth,
            )
        }
        if (failed) {
            ErrorState()
        }
    }
}

@Composable
private fun ErrorState() {
    Text(
        text = "Unable to load the privacy policy document.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
    )
}

private val PdfCacheLock = Any()

private fun renderPdfFromAssets(
    context: Context,
    assetPath: String,
    scale: Int,
): List<Bitmap> = synchronized(PdfCacheLock) {
    // Always refresh the cached copy so a re-shipped policy is picked up.
    val cacheFile = File(context.cacheDir, "rendered-${assetPath.replace('/', '_')}-$scale.pdf")
    context.assets.open(assetPath).use { input ->
        cacheFile.outputStream().use { output -> input.copyTo(output) }
    }

    val bitmaps = mutableListOf<Bitmap>()
    ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
        PdfRenderer(pfd).use { renderer ->
            for (i in 0 until renderer.pageCount) {
                renderer.openPage(i).use { page ->
                    val width = page.width * scale
                    val height = (page.height * scale).coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    page.render(
                        bitmap,
                        null,
                        null,
                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
                    )
                    bitmaps += bitmap
                }
            }
        }
    }
    bitmaps
}
