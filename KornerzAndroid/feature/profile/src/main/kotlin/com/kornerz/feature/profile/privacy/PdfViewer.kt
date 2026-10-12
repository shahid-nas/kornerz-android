package com.kornerz.feature.profile.privacy

import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.github.barteksc.pdfviewer.PDFView

/**
 * Thin wrapper around `com.github.barteksc.pdfviewer.PDFView` (P6,
 * task 5.17). Mirrors iOS `PDFKit.PDFView` (lines 49–80 of
 * `PrivacyPolicyVC.swift`).
 */
@Composable
fun PdfViewer(
    assetPath: String,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            PDFView(context, null).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                fromAsset(assetPath)
                    .enableSwipe(true)
                    .swipeHorizontal(false)
                    .enableDoubletap(true)
                    .autoSpacing(true)
                    .pageFling(true)
                    .load()
                val scale = minOf(width, height).toFloat() / 1000f
                minZoom = scale.coerceAtLeast(0.1f)
                midZoom = scale * 2f
                maxZoom = 5f
            }
        },
    )
}
