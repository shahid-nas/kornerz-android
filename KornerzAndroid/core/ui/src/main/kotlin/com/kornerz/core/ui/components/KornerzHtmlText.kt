package com.kornerz.core.ui.components

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kornerz.core.ui.theme.KornerzTheme

/**
 * Collapsible HTML description. 1:1 port of iOS
 * `kornerz/Kornerz/Components/ExpandableHTMLDescriptionView.swift`
 * together with `WebViewRepresentable.swift`.
 *
 * Collapsed, the component renders the tag-stripped plain text of [html]
 * truncated to [collapsedLineLimit] lines. Expanded, it hosts a
 * read-only `WebView` through [AndroidView] whose height follows the
 * content height the view reports back, clamped to [maxExpandedHeight].
 * The toggle label flips between "See More" and "Show Less".
 *
 * The `WebView` is locked down: file access is disabled, JavaScript is
 * off, and every URL click is intercepted by
 * `shouldOverrideUrlLoading` returning `true`, so no external page is
 * ever loaded in place.
 */
@Composable
fun KornerzHtmlText(
    html: String,
    accentColor: Color,
    maxExpandedHeight: Dp = 200.dp,
    collapsedLineLimit: Int = 2,
    expandedLabel: String = "See More",
    collapsedLabel: String = "Show Less",
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(false) }
    val plainText = remember(html) { htmlToPlainText(html) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (isExpanded) {
            HtmlWebView(
                html = html,
                maxExpandedHeight = maxExpandedHeight,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                text = plainText,
                style = textStyle,
                color = textColor,
                maxLines = collapsedLineLimit,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = if (isExpanded) collapsedLabel else expandedLabel,
            style = textStyle,
            color = accentColor,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded },
        )
    }
}

/**
 * The locked-down `WebView` host. Its height is driven by the content
 * height the view reports through its global layout listener and is
 * clamped to [maxExpandedHeight] — the same contract iOS gets from
 * `document.body.scrollHeight`.
 */
@Composable
private fun HtmlWebView(
    html: String,
    maxExpandedHeight: Dp,
    modifier: Modifier = Modifier,
) {
    var contentHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val loadedHtml = remember { arrayOfNulls<String>(1) }

    val contentHeight = with(density) { contentHeightPx.toDp() }
    val resolvedHeight = if (contentHeight > 0.dp) {
        contentHeight.coerceAtMost(maxExpandedHeight)
    } else {
        maxExpandedHeight
    }

    AndroidView(
        factory = { context ->
            val webView = WebView(context)
            webView.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            webView.settings.allowFileAccess = false
            webView.settings.allowContentAccess = false
            webView.settings.javaScriptEnabled = false
            webView.settings.defaultTextEncodingName = "UTF-8"
            webView.isVerticalScrollBarEnabled = false
            webView.isHorizontalScrollBarEnabled = false
            webView.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): Boolean = true

                @Deprecated("Kept for API levels below 24")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = true
            }
            webView.viewTreeObserver.addOnGlobalLayoutListener(
                ViewTreeObserver.OnGlobalLayoutListener {
                    // A `WebView` laid out with a bounded height always
                    // reports that bound back, so the content height is
                    // measured separately with an unbounded spec — the
                    // Android equivalent of iOS reading
                    // `document.body.scrollHeight`.
                    webView.measure(
                        View.MeasureSpec.makeMeasureSpec(
                            webView.width,
                            View.MeasureSpec.EXACTLY,
                        ),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    )
                    val height = webView.measuredHeight
                    if (height > 0 && height != contentHeightPx) {
                        contentHeightPx = height
                    }
                },
            )
            webView
        },
        update = { webView ->
            if (loadedHtml[0] != html) {
                loadedHtml[0] = html
                webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.destroy()
        },
        modifier = modifier.height(resolvedHeight),
    )
}

/**
 * Strips the tags out of an HTML fragment and decodes the handful of
 * entities the backend's authoring flow emits. iOS uses
 * `NSAttributedString`'s HTML parser; Android has no equivalent in the
 * standard library, so the tags are removed with a small, explicit
 * transformation.
 */
internal fun htmlToPlainText(html: String): String {
    var text = html
    text = text.replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
    text = text.replace(Regex("(?i)<br\\s*/?>"), "\n")
    text = text.replace(Regex("(?i)</(p|div|h[1-6]|li|tr|blockquote|pre)>"), "\n")
    text = text.replace(Regex("(?i)<(p|div|h[1-6]|li|tr|blockquote|pre)[^>]*>"), "")
    text = text.replace(Regex("(?s)<[^>]*>"), "")
    text = text
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
    return text
        .lines()
        .joinToString("\n") { it.replace(Regex("[ \\t\\u00A0]+"), " ").trim() }
        .trim()
}

@Preview(showBackground = true, name = "HtmlText — collapsed (light)")
@Composable
private fun KornerzHtmlTextCollapsedLightPreview() {
    KornerzTheme(darkTheme = false) {
        KornerzHtmlText(
            html = "<p>Hello <b>world</b></p>",
            accentColor = Color(0xFFE27D5A),
        )
    }
}

@Preview(showBackground = true, name = "HtmlText — collapsed (dark)")
@Composable
private fun KornerzHtmlTextCollapsedDarkPreview() {
    KornerzTheme(darkTheme = true) {
        KornerzHtmlText(
            html = "<p>Hello <b>world</b></p>",
            accentColor = Color(0xFFEBAA4A),
        )
    }
}
