package com.kornerz.core.model

import java.net.URI

/**
 * The typed deep link. Mirrors iOS `DeepLinks.Deeplink`
 * (`kornerz/Kornerz/DeepLinks/DeeplinkManager.swift` lines 13–54)
 * case-for-case.
 *
 * iOS passes the enum around in memory and never serialises it; Android
 * needs a URI because the transport between `MainActivity` and the shell
 * is an `Intent`. The `kornerz://` URI is therefore the **transport
 * form** of this model — [toUri] mints it and [parse] reads it back.
 *
 * The grammar:
 * ```
 * kornerz://loop/{loopId}                        → NewLoop / NewLoopComment
 * kornerz://forum/{forumId}                      → NewComment
 * kornerz://community/{communityId}/call         → VideoCall
 * kornerz://community/{communityId}/event/{id}   → NewEvent
 * kornerz://community/{communityId}/article/{id} → NewArticle
 * kornerz://community/{communityId}/forum/{id}   → NewForum
 * ```
 *
 * The `communityId` a link carries is the community's **public
 * `communityId` field**, not the Firestore document id — iOS resolves
 * the field to a document in `DeepLinksManager.handleCallLink` →
 * `getCommunityDetailsFor` (`APICommunity.swift:475`).
 *
 * **Known lossiness:** `kornerz://loop/{loopId}` cannot distinguish
 * [NewLoop] from [NewLoopComment] — the grammar carries one path
 * segment and [NewLoop] additionally carries a community id. Both cases
 * resolve to the same destination (the loop detail), so the collapse is
 * lossless for routing purposes. It is carried over from the existing
 * Android implementation, which already minted the same URI for both.
 */
sealed interface DeepLink {

    /** A new event in a community. Mirrors iOS `.newEvent(EventLink)`. */
    data class NewEvent(val eventId: String, val communityId: String) : DeepLink

    /** A new article (post) in a community. Mirrors iOS `.newArticle(ArticleLink)`. */
    data class NewArticle(val articleId: String, val communityId: String) : DeepLink

    /** A new forum in a community. Mirrors iOS `.newForum(ForumLink)`. */
    data class NewForum(val forumId: String, val communityId: String) : DeepLink

    /** A video call on a community. Mirrors iOS `.videoCall(CallLink)`. */
    data class VideoCall(val communityId: String) : DeepLink

    /** A new comment on a forum. Mirrors iOS `.newComment(CommentLink)`. */
    data class NewComment(val forumId: String) : DeepLink

    /** A new loop in a community. Mirrors iOS `.newLoop(LoopLink)`. */
    data class NewLoop(val communityId: String, val loopId: String) : DeepLink

    /** A new comment on a loop. Mirrors iOS `.newLoopComment(LoopCommentLink)`. */
    data class NewLoopComment(val loopId: String) : DeepLink

    /**
     * The `kornerz://` URI that transports this link between the
     * activity and the shell.
     *
     * The id segments are percent-encoded, so a link round-trips
     * through its URI even when an id carries a character that is not
     * legal in a URI path segment. Firestore document ids are
     * alphanumeric in practice, so the encoding is a no-op for every
     * id the app mints.
     */
    fun toUri(): String = when (this) {
        is NewEvent -> "kornerz://community/${encodeSegment(communityId)}/event/${encodeSegment(eventId)}"
        is NewArticle -> "kornerz://community/${encodeSegment(communityId)}/article/${encodeSegment(articleId)}"
        is NewForum -> "kornerz://community/${encodeSegment(communityId)}/forum/${encodeSegment(forumId)}"
        is VideoCall -> "kornerz://community/${encodeSegment(communityId)}/call"
        is NewComment -> "kornerz://forum/${encodeSegment(forumId)}"
        is NewLoop -> "kornerz://loop/${encodeSegment(loopId)}"
        is NewLoopComment -> "kornerz://loop/${encodeSegment(loopId)}"
    }

    companion object {

        /** The URI scheme the app registers in its manifest. */
        const val SCHEME: String = "kornerz"

        /**
         * Parses a `kornerz://` URI back into the typed model. Returns
         * `null` for anything that does not match the grammar — an
         * unparseable URI is never an error, it is simply not a link
         * this app can honour.
         */
        fun parse(uri: String): DeepLink? {
            val parsed = runCatching { URI(uri) }.getOrNull() ?: run {
                DeepLinkDiagnostics.fail("5-parse", "the URI is not a valid URI: '$uri'")
                return null
            }
            if (!parsed.scheme.equals(SCHEME, ignoreCase = true)) {
                DeepLinkDiagnostics.fail(
                    "5-parse",
                    "the URI's scheme is '${parsed.scheme}', not '$SCHEME': '$uri'",
                )
                return null
            }
            val host = parsed.host?.lowercase() ?: run {
                DeepLinkDiagnostics.fail("5-parse", "the URI has no host: '$uri'")
                return null
            }
            // The **raw** path is decoded exactly once, here. `URI.getPath()`
            // would hand back an already-decoded path, and decoding that a
            // second time corrupts any id that contains a literal `%` —
            // the id `a%2Fb` would come back as `a/b` and address a
            // different loop.
            val segments = parsed.rawPath.orEmpty()
                .split('/')
                .filter { it.isNotEmpty() }
                .map { decodeSegment(it) }
            val link = when {
                host == "loop" && segments.size == 1 ->
                    NewLoopComment(loopId = segments[0])

                host == "forum" && segments.size == 1 ->
                    NewComment(forumId = segments[0])

                host == "community" && segments.size == 2 && segments[1] == "call" ->
                    VideoCall(communityId = segments[0])

                host == "community" && segments.size == 3 && segments[1] == "event" ->
                    NewEvent(eventId = segments[2], communityId = segments[0])

                host == "community" && segments.size == 3 && segments[1] == "article" ->
                    NewArticle(articleId = segments[2], communityId = segments[0])

                host == "community" && segments.size == 3 && segments[1] == "forum" ->
                    NewForum(forumId = segments[2], communityId = segments[0])

                else -> null
            }
            if (link == null) {
                DeepLinkDiagnostics.fail(
                    "5-parse",
                    "the URI does not match the link grammar: '$uri' " +
                        "(host='$host' segments=$segments)",
                )
            } else {
                DeepLinkDiagnostics.log("5-parse", "the URI parsed into: $link")
            }
            return link
        }

        /**
         * Percent-decodes a **raw** path segment — the form
         * `URI.rawPath` hands back, before any decoding. A malformed
         * escape falls back to the raw segment rather than throwing.
         *
         * This is applied to the raw path exactly once; decoding an
         * already-decoded path would corrupt any id that contains a
         * literal `%` (the id `a%2Fb` would come back as `a/b` and
         * address a different loop).
         *
         * `+` is **not** translated to a space: this decodes a path
         * segment, not an HTML form value, and the ids the app mints
         * are emitted verbatim by [toUri] — so a literal `+` has to
         * survive the round-trip unchanged.
         */
        private fun decodeSegment(segment: String): String {
            if (!segment.contains('%')) return segment
            val bytes = java.io.ByteArrayOutputStream()
            var i = 0
            while (i < segment.length) {
                val char = segment[i]
                if (char == '%' && i + 2 < segment.length) {
                    val hex = segment.substring(i + 1, i + 3)
                    val value = hex.toIntOrNull(16)
                    if (value != null) {
                        bytes.write(value)
                        i += 3
                        continue
                    }
                }
                bytes.write(char.toString().toByteArray(Charsets.UTF_8))
                i++
            }
            return bytes.toString(Charsets.UTF_8.name())
        }
        /**
         * Percent-encodes a path segment.
         *
         * Only the RFC 3986 unreserved characters survive verbatim;
         * everything else — including `/`, which is the segment
         * separator — is escaped. `+` is escaped too, because a path
         * segment is not a query string.
         */
        private fun encodeSegment(segment: String): String {
            val bytes = segment.toByteArray(Charsets.UTF_8)
            val encoded = StringBuilder(bytes.size)
            for (byte in bytes) {
                val value = byte.toInt() and 0xFF
                val char = value.toChar()
                if (char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' ||
                    char == '-' || char == '_' || char == '.' || char == '~'
                ) {
                    encoded.append(char)
                } else {
                    encoded.append('%')
                    encoded.append(HEX[value shr 4])
                    encoded.append(HEX[value and 0x0F])
                }
            }
            return encoded.toString()
        }

        private const val HEX = "0123456789ABCDEF"
    }
}
