package com.kornerz.feature.community.admin.createforum

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/**
 * Converts the rich-text editor's [AnnotatedString] into the HTML
 * fragment the backend stores in `Loop.descriptionHtml`.
 *
 * iOS writes **two** description fields:
 *
 * - `description` — the attributed string's `.string`, i.e. plain text;
 * - `descriptionHtml` — the attributed string's HTML export
 *   (`AddForumViewModel.createLoopWithHashes`, via
 *   `NSAttributedString.html()`).
 *
 * Android's `BasicTextField` produces an [AnnotatedString], so the same
 * pairing is produced here: the plain text goes into `description` and
 * the fragment below into `descriptionHtml`.
 *
 * The fragment is a flat run of inline tags — `<strong>`, `<em>`,
 * `<u>` — rather than iOS's full HTML **document** with an embedded
 * stylesheet. `core/ui`'s `KornerzHtmlText` renders either shape:
 * `sanitizeHtmlForTextView` strips the `<style>` block iOS emits, and a
 * fragment needs no stripping. The read path already prefers HTML over
 * plain text (`resolveDescriptionContent`), so a loop created on
 * Android renders its formatting in the feed exactly like one created
 * on iOS.
 */
internal fun AnnotatedString.toDescriptionHtml(): String {
    val builder = StringBuilder()

    // Split the text into runs that share the same formatting, so a
    // bold+italic span becomes one `<strong><em>…</em></strong>` run
    // rather than three nested fragments.
    var index = 0
    while (index < length) {
        val style = styleAt(index)

        var end = index + 1
        while (end < length && styleAt(end) == style) {
            end++
        }

        val run = substring(index, end)
        builder.append(run.toHtmlRun(style))
        index = end
    }

    return builder.toString()
}

/**
 * The merged style of every span covering [index].
 */
private fun AnnotatedString.styleAt(index: Int): SpanStyle =
    spanStyles
        .filter { index >= it.start && index < it.end }
        .map { it.item }
        .fold(SpanStyle()) { acc, span -> acc.merge(span) }

/**
 * Wraps one run of text in the inline tags its style calls for, and
 * escapes the characters that would otherwise break the markup.
 */
private fun String.toHtmlRun(style: SpanStyle): String {
    val escaped = escapeHtml()
    if (escaped.isEmpty()) return ""

    val bold = style.fontWeight?.weight?.let { it >= FontWeight.Bold.weight } == true
    val italic = style.fontStyle == FontStyle.Italic
    val underlined = style.textDecoration?.contains(TextDecoration.Underline) == true

    var tagged = escaped
    if (bold) tagged = "<strong>$tagged</strong>"
    if (italic) tagged = "<em>$tagged</em>"
    if (underlined) tagged = "<u>$tagged</u>"
    return tagged
}

/**
 * Escapes the five XML entities so a description containing `<`, `>`,
 * or `&` cannot break the stored markup.
 */
private fun String.escapeHtml(): String = this
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&#39;")
    .replace("\n", "<br/>")
