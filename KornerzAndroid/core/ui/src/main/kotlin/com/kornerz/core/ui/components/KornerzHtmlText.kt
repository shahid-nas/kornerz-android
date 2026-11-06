package com.kornerz.core.ui.components

import android.text.Layout
import android.text.SpannableString
import android.text.StaticLayout
import android.text.TextUtils
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import com.kornerz.core.ui.theme.KornerzTheme

/** Default number of lines shown while the description is collapsed. */
private const val DEFAULT_COLLAPSED_LINE_LIMIT = 3

/** Fallback text size used when the caller's style carries no explicit size. */
private val DEFAULT_TEXT_SIZE = 14.sp

/**
 * Matches an opening or closing HTML tag, e.g. `<p>`, `</p>`, `<br/>`,
 * `<strong ...>`.
 */
private val HTML_TAG_PATTERN = Regex("</?[a-zA-Z][a-zA-Z0-9]*(\\s[^<>]*)?/?>")

/**
 * Reports whether a text carries HTML markup.
 *
 * Some content reaches the app as an HTML fragment inside the plain
 * `description` field (the rich-text editor's output) rather than in the
 * dedicated `descriptionHtml` field. Rendering such a value as plain
 * text shows the raw markup — `<p>Hello</p>` instead of `Hello` — so
 * callers use this to route it through the HTML renderer instead.
 */
fun containsHtmlMarkup(text: String): Boolean = HTML_TAG_PATTERN.containsMatchIn(text)

/**
 * Reduces an HTML document to the content a `TextView` can render.
 *
 * The descriptions the app stores are produced by the iOS rich-text
 * editor, which exports an `NSAttributedString` HTML **document**:
 *
 * ```html
 * <html><head><style>p.p1 {margin: …; font: 16.0px '.AppleSystemUIFont'; …}
 * span.s1 {font-family: '.SFUI-Regular'; …}</style></head>
 * <body><p class="p1">The only way I could …</p></body></html>
 * ```
 *
 * `WKWebView` applies that stylesheet and hides it. `HtmlCompat` knows
 * nothing about CSS: it drops the `<style>` **tags** but keeps their
 * content, so the raw CSS ends up rendered as the first lines of the
 * description. Stripping the document scaffolding and the CSS block
 * leaves the paragraphs, which `HtmlCompat` renders correctly.
 */
internal fun sanitizeHtmlForTextView(html: String): String {
    var out = html
    // Comments, scripts and stylesheets carry no displayable content.
    out = out.replace(Regex("(?is)<!--.*?-->"), "")
    out = out.replace(Regex("(?is)<!doctype[^>]*>"), "")
    out = out.replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1\\s*>"), "")
    // A `<style>`/`<script>` that was never closed still has to go.
    out = out.replace(Regex("(?is)<(script|style)[^>]*>.*"), "")
    // Document scaffolding — `<head>` content is dropped above, so the
    // remaining tags are just wrappers around the body content.
    out = out.replace(Regex("(?is)</?(html|head|body|meta|link|title|base)[^>]*>"), "")
    return out.trim()
}

/**
 * Strips the tags out of an HTML fragment and decodes the handful of
 * entities the backend's authoring flow emits, yielding plain text.
 *
 * Used where the HTML has to be shown as text — e.g. the quoted
 * description in the comment composer.
 */
fun htmlToPlainText(html: String): String {
    var text = sanitizeHtmlForTextView(html)
    text = text.replace(Regex("(?i)<br\\s*/?>"), "\n")
    text = text.replace(Regex("(?i)</(p|div|h[1-6]|li|tr|blockquote|pre)>"), "\n")
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

/**
 * Collapsible HTML description.
 *
 * Unlike iOS — which has to host a `WKWebView` because UIKit offers no
 * HTML rendering control — Android ships one natively:
 * [HtmlCompat.fromHtml] turns an HTML fragment into a `Spanned` that a
 * plain `TextView` renders with the usual bold/italic/link styling. The
 * description is therefore hosted by a `TextView` through [AndroidView]
 * instead of a `WebView`.
 *
 * Collapsed, the `TextView` is limited to [collapsedLineLimit] lines with
 * an ellipsis; the "View More" toggle only appears when the content
 * actually overflows. Expanded, the limit is lifted so the full HTML
 * content is shown together with the "View Less" toggle.
 */
@Composable
fun KornerzHtmlText(
    html: String,
    accentColor: Color,
    collapsedLineLimit: Int = DEFAULT_COLLAPSED_LINE_LIMIT,
    expandedLabel: String = "View More",
    collapsedLabel: String = "View Less",
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember(html) { mutableStateOf(false) }
    var isOverflowing by remember(html) { mutableStateOf(false) }

    val spannable = remember(html) { htmlToSpanned(html) }
    val textSize = textStyle.fontSize.resolveTextSize()
    val textColorArgb = textColor.toArgb()

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AndroidView(
            factory = { context ->
                TextView(context).apply {
                    includeFontPadding = false
                    setText(spannable, TextView.BufferType.SPANNABLE)
                    addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                        val textView = view as? TextView ?: return@addOnLayoutChangeListener
                        val overflowing = textView.isContentOverflowing(collapsedLineLimit)
                        if (overflowing != isOverflowing) {
                            isOverflowing = overflowing
                        }
                    }
                }
            },
            update = { textView ->
                textView.setTextColor(textColorArgb)
                textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize.value)
                if (textView.tag !== spannable) {
                    textView.tag = spannable
                    textView.text = spannable
                }
                textView.maxLines = if (isExpanded) Int.MAX_VALUE else collapsedLineLimit
                textView.ellipsize =
                    if (isExpanded) null else TextUtils.TruncateAt.END
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (isOverflowing) {
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
}

/**
 * Renders an HTML fragment with Android's built-in HTML support and
 * trims the leading/trailing whitespace `HtmlCompat` adds around block
 * elements — without trimming, `<p>…</p>` renders with a blank first
 * and last line.
 */
private fun htmlToSpanned(html: String): CharSequence {
    val raw = HtmlCompat.fromHtml(
        sanitizeHtmlForTextView(html),
        HtmlCompat.FROM_HTML_MODE_COMPACT,
    )
    val start = raw.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) 0 else it }
    val end = raw.indexOfLast { !it.isWhitespace() }.let { if (it < 0) raw.length else it + 1 }
    if (start == 0 && end == raw.length) return raw
    return SpannableString(raw.subSequence(start, end))
}

/**
 * Measures the full, unconstrained content of a `TextView` and reports
 * whether it needs more than [lineLimit] lines.
 *
 * The `TextView` itself is capped at [lineLimit] lines while collapsed,
 * so its own `lineCount` cannot tell overflow apart from a short text.
 * A throw-away [StaticLayout] built with the same paint and the measured
 * width, but without a line cap, yields the real number of lines.
 */
private fun TextView.isContentOverflowing(lineLimit: Int): Boolean {
    val availableWidth = width - paddingLeft - paddingRight
    if (availableWidth <= 0) return false
    val content = text ?: return false
    if (content.isEmpty()) return false
    return try {
        val layout = StaticLayout.Builder
            .obtain(content, 0, content.length, paint, availableWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(includeFontPadding)
            .build()
        layout.lineCount > lineLimit
    } catch (_: IllegalArgumentException) {
        false
    }
}

private fun TextUnit.resolveTextSize(): TextUnit = if (this.isSp) this else DEFAULT_TEXT_SIZE

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
    KornerzTheme(darkTheme = false) {
        KornerzHtmlText(
            html = "<p>Hello <b>world</b></p>",
            accentColor = Color(0xFFEBAA4A),
        )
    }
}
