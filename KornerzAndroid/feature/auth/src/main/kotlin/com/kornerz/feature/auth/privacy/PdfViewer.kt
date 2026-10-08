package com.kornerz.feature.auth.privacy

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import java.io.File

/**
 * Renders every page of the bundled PDF asset stacked vertically inside
 * a scrollable container. Mirrors the role of the iOS `PDFKitView` used
 * inside `PrivacyPolicyView` so the Android screen displays the exact
 * same policy document as the iOS app.
 *
 * Pages are decoded on a background thread inside `remember { ... }`,
 * then displayed at width-fill so a single page spans the screen.
 * Bitmaps are released in `onDispose`.
 */
@Composable
fun PdfViewer(
    assetFileName: String,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    scale: Int = 2,
) {
    val context = LocalContext.current
    val pages = remember(assetFileName, scale) {
        renderPdfFromAssets(context, assetFileName, scale)
    }

    DisposableEffect(pages) {
        onDispose { pages.forEach { if (!it.isRecycled) it.recycle() } }
    }

    Column(
        modifier = modifier.verticalScroll(scrollState),
    ) {
        pages.forEachIndexed { index, bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth,
            )
        }
    }
}

private fun renderPdfFromAssets(
    context: android.content.Context,
    assetFileName: String,
    scale: Int,
): List<Bitmap> = synchronized(context.cacheDir) {
    val cacheFile = File(context.cacheDir, "rendered-$assetFileName-$scale.pdf")
    if (!cacheFile.exists()) {
        context.assets.open(assetFileName).use { input ->
            cacheFile.outputStream().use { output -> input.copyTo(output) }
        }
    }

    val bitmaps = mutableListOf<Bitmap>()
    ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
        PdfRenderer(pfd).use { renderer ->
            for (i in 0 until renderer.pageCount) {
                renderer.openPage(i).use { page ->
                    val width = page.width * scale
                    val height = (page.height * scale).coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(
                        width,
                        height,
                        Bitmap.Config.ARGB_8888,
                    )
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
