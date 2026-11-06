package com.kornerz.core.ui.components

import com.kornerz.core.model.Loop

/**
 * The content of a loop description, resolved from the two fields the
 * backend stores.
 *
 * This mirrors the iOS resolution in
 * `kornerz/Kornerz/SwiftUI/Dashboard/Loops/LoopDetail/LoopDetailView.swift`
 * (`descriptionView`):
 *
 * ```swift
 * if let descriptionHtml = viewModel.loopInfo.loop.descriptionHtml,
 *    !descriptionHtml.isEmpty {
 *     ExpandableHTMLDescriptionView(html: descriptionHtml, …)
 * } else {
 *     ExpandableText(text: viewModel.loopInfo.loop.description ?? .empty, …)
 * }
 * ```
 *
 * i.e. **HTML wins when it is present, plain text otherwise**. iOS has
 * no third "attributed text" tier on this screen: the rich-text
 * composer produces an `NSAttributedString`, which iOS persists as two
 * fields — `description` (the attributed string's `.string`, i.e. plain
 * text) and `descriptionHtml` (its HTML export). Reading a loop back
 * therefore only ever yields HTML *or* plain text.
 *
 * (The three-tier model the iOS codebase does have lives on `Post`,
 * which stores `postContent` / `postRTF` / `postHTML` — not on `Loop`,
 * whose Firestore document only carries `description` and
 * `descriptionHtml`.)
 */
sealed interface DescriptionContent {

    /** The description is HTML and must be rendered by an HTML renderer. */
    data class Html(val html: String) : DescriptionContent

    /** The description is plain text and can be rendered as-is. */
    data class Plain(val text: String) : DescriptionContent
}

/**
 * Resolves a loop's description into the content it should be rendered
 * as.
 *
 * @param description the plain-text description field.
 * @param descriptionHtml the HTML description field.
 * @return the resolved content, or `null` when the loop has no
 *   description at all.
 */
fun resolveDescriptionContent(
    description: String?,
    descriptionHtml: String?,
): DescriptionContent? {
    val html = descriptionHtml?.trim()?.takeIf { it.isNotEmpty() }
    if (html != null) return DescriptionContent.Html(html)

    val plain = description?.trim()?.takeIf { it.isNotEmpty() } ?: return null

    // iOS stores the attributed string's plain text in `description`, but
    // older documents (and content created outside the iOS composer) can
    // carry markup there. Rendering that as plain text would show the
    // raw tags, so it goes through the HTML renderer instead.
    return if (containsHtmlMarkup(plain)) {
        DescriptionContent.Html(plain)
    } else {
        DescriptionContent.Plain(plain)
    }
}

/**
 * The loop's description as plain text, with any markup removed.
 *
 * Used where the description is quoted as text rather than rendered as
 * HTML — e.g. the description preview in the comment composer.
 */
fun Loop.descriptionPlainText(): String =
    when (val content = resolveDescriptionContent(description, descriptionHtml)) {
        is DescriptionContent.Html -> htmlToPlainText(content.html)
        is DescriptionContent.Plain -> content.text
        null -> ""
    }
