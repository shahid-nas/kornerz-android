package com.kornerz.core.model

/**
 * Parses the **push** deep-link dialect — the FCM data payload iOS
 * reads in `DeepLinksManager.handleDeepLink(info:)`
 * (`kornerz/Kornerz/DeepLinks/DeeplinkManager.swift:85-134`).
 *
 * The push payload carries `pushType` plus the ids the type needs:
 *
 * | `pushType` | payload ids | resulting link |
 * |---|---|---|
 * | `NewEvent` | `communityId`, `eventId` | `kornerz://community/{communityId}/event/{eventId}` |
 * | `NewArticle` | `communityId`, `articleId` | `kornerz://community/{communityId}/article/{articleId}` |
 * | `NewForum` | `communityId`, `forumId` | `kornerz://community/{communityId}/forum/{forumId}` |
 * | `VideoCall` | `communityId` | `kornerz://community/{communityId}/call` |
 * | `NewComment` | `forumId` | `kornerz://forum/{forumId}` |
 * | `NewLoop` | `communityId`, `loopId` | `kornerz://loop/{loopId}` |
 * | `LoopComment` | `loopId` | `kornerz://loop/{loopId}` |
 *
 * **This dialect is deliberately distinct from the in-app notification
 * *document* dialect.** The document carries `notificationType` /
 * `notificationReferenceId` / `notificationCommunityId` and is resolved
 * by [deeplink]; the push payload carries `pushType` / `communityId` /
 * `eventId` / `articleId` / `forumId` / `loopId` and is resolved here.
 * Flattening the two would let the tray-tap path and the in-app-list
 * path disagree about what a link means.
 *
 * A payload with no recognisable `pushType`, or one missing an id the
 * type requires, yields `null` — the caller then falls back to the
 * plain launch intent.
 */
object DeepLinkPayloadParser {

    /**
     * The FCM `click_action` that carries a deep link.
     *
     * **Why a dedicated action rather than the link's own URI.** FCM's
     * `click_action` is a plain string, and the SDK launches the app
     * with it when it displays the notification **itself** — which is
     * what happens whenever the app is in the background or has been
     * killed, because `onMessageReceived` is then never called and the
     * app cannot build its own `PendingIntent`.
     *
     * Using the link's own `kornerz://community/{id}/event/{id}` URI as
     * the `click_action` would work too, but it makes the backend
     * responsible for minting a URI that must stay in step with the
     * app's grammar. A single opaque action keeps the contract between
     * the backend and the app to **one string**, and the app derives the
     * destination from the payload it already receives in the launch
     * intent's extras.
     *
     * The action is matched by the manifest's intent-filter on
     * `MainActivity` (`app/src/main/AndroidManifest.xml`), so the SDK's
     * launch intent resolves to this app.
     */
    const val CLICK_ACTION: String = "kornerz://deeplink"

    /**
     * Parses a push payload into a [DeepLink], or returns `null` when
     * the payload does not describe one.
     */
    fun parse(payload: Map<String, String>): DeepLink? {
        val pushType = payload["pushType"]?.takeIf { it.isNotBlank() } ?: return null
        val link = when (pushType.lowercase()) {
            "newevent" -> payload.link("eventId") { communityId, eventId ->
                DeepLink.NewEvent(eventId = eventId, communityId = communityId)
            }

            "newarticle" -> payload.link("articleId") { communityId, articleId ->
                DeepLink.NewArticle(articleId = articleId, communityId = communityId)
            }

            "newforum" -> payload.link("forumId") { communityId, forumId ->
                DeepLink.NewForum(forumId = forumId, communityId = communityId)
            }

            "videocall" -> payload["communityId"]
                ?.takeIf { it.isNotBlank() }
                ?.let { DeepLink.VideoCall(communityId = it) }

            "newcomment" -> payload["forumId"]
                ?.takeIf { it.isNotBlank() }
                ?.let { DeepLink.NewComment(forumId = it) }

            "newloop" -> payload.link("loopId") { communityId, loopId ->
                DeepLink.NewLoop(communityId = communityId, loopId = loopId)
            }

            "loopcomment" -> payload["loopId"]
                ?.takeIf { it.isNotBlank() }
                ?.let { DeepLink.NewLoopComment(loopId = it) }

            else -> null
        }
        if (link == null) {
            // The type was recognised but the payload is missing an id
            // that type needs — the single most common cause of a tap
            // that opens the app but not the screen.
            DeepLinkDiagnostics.fail(
                "5-parse",
                "pushType='$pushType' was recognised but the payload is " +
                    "missing the id it needs. payload=$payload",
            )
        } else {
            DeepLinkDiagnostics.log(
                "5-parse",
                "push dialect matched: pushType='$pushType' → $link",
            )
        }
        return link
    }

    /**
     * Builds a link that needs both a community id and an entity id,
     * or `null` when either is absent.
     */
    private inline fun Map<String, String>.link(
        entityKey: String,
        build: (communityId: String, entityId: String) -> DeepLink,
    ): DeepLink? {
        val communityId = this["communityId"]?.takeIf { it.isNotBlank() } ?: return null
        val entityId = this[entityKey]?.takeIf { it.isNotBlank() } ?: return null
        return build(communityId, entityId)
    }

    /**
     * Reads the push payload out of a launch intent's extras.
     *
     * When the Firebase SDK displays a notification itself — the
     * backgrounded / killed case — it launches the app with the
     * message's `data` payload copied into the intent's extras, one
     * entry per key, alongside the SDK's own `google.*` bookkeeping
     * entries. This pulls the **payload** keys back out of that bag.
     *
     * The SDK's own keys are skipped rather than filtered by name:
     * [PAYLOAD_KEYS] is the closed set of keys this app's dialects
     * read, so a key the app does not know about is simply not part of
     * the payload.
     */
    fun payloadFromExtras(extras: Map<String, Any?>): Map<String, String> {
        val payload = PAYLOAD_KEYS.mapNotNull { key ->
            val value = extras[key]?.toString()?.takeIf { it.isNotBlank() }
            if (value == null) null else key to value
        }.toMap()
        // The keys that were dropped are logged alongside the ones that
        // survived. A payload whose keys are spelled differently — or
        // whose values are not strings — is dropped here, silently, and
        // the filtered view alone would look empty without showing
        // that the extras were not empty at all.
        val dropped = extras.keys - payload.keys
        if (dropped.isNotEmpty()) {
            DeepLinkDiagnostics.log(
                "4-transport",
                "extras keys dropped by the payload filter: $dropped " +
                    "(the app reads only $PAYLOAD_KEYS)",
            )
        }
        return payload
    }

    /**
     * The payload keys this app's dialects read.
     *
     * The list is the closed set of keys [parse] and [parseNotification]
     * look at. It exists so [payloadFromExtras] can tell a payload entry
     * from the Firebase SDK's own `google.*` bookkeeping extras, which
     * are not part of the message the backend sent.
     *
     * Both dialects are listed because the backend may send either —
     * or both — depending on the notification. [parse] reads the push
     * dialect; [parseNotification] reads the notification-record
     * dialect.
     */
    private val PAYLOAD_KEYS: List<String> = listOf(
        // The push dialect — iOS's `DeepLinksManager.handleDeepLink(info:)`.
        "pushType",
        "communityId",
        "eventId",
        "articleId",
        "forumId",
        "loopId",
        // The notification-record dialect — iOS's `UserNotification`'s
        // `CodingKeys`, which the FCM service also reads to build the
        // in-app notifications list.
        "notificationType",
        "notificationReferenceId",
        "notificationCommunityId",
    )

    /**
     * Parses a push payload in the **notification-record** dialect.
     *
     * The backend sends the notification-record keys alongside the push
     * dialect's keys on some notifications — they are the same
     * notification, described once for the in-app list
     * (`notificationType` / `notificationReferenceId` /
     * `notificationCommunityId`) and once for the deep link
     * (`pushType` / `eventId` / …). When the push dialect's keys are
     * absent but the record dialect's are, this is the reader that
     * still finds the destination.
     *
     * The mapping mirrors [deeplink] — the same table the in-app
     * notifications list uses — except that the community id comes
     * straight from the payload's `notificationCommunityId` rather than
     * from a fetched [Community].
     *
     * Returns `null` when the payload carries neither dialect, or when
     * the record dialect is missing an id its type requires.
     */
    fun parseNotification(payload: Map<String, String>): DeepLink? {
        val type = payload["notificationType"]?.takeIf { it.isNotBlank() } ?: return null
        val reference = payload["notificationReferenceId"]?.takeIf { it.isNotBlank() }
        val communityId = payload["notificationCommunityId"]?.takeIf { it.isNotBlank() }
        val link = when (UserNotificationDeeplinkType.fromRaw(type)) {
            UserNotificationDeeplinkType.NEW_EVENT ->
                if (reference != null && communityId != null) {
                    DeepLink.NewEvent(eventId = reference, communityId = communityId)
                } else {
                    null
                }

            UserNotificationDeeplinkType.NEW_ARTICLE ->
                if (reference != null && communityId != null) {
                    DeepLink.NewArticle(articleId = reference, communityId = communityId)
                } else {
                    null
                }

            UserNotificationDeeplinkType.NEW_FORUM ->
                if (reference != null && communityId != null) {
                    DeepLink.NewForum(forumId = reference, communityId = communityId)
                } else {
                    null
                }

            UserNotificationDeeplinkType.VIDEO_CALL ->
                communityId?.let { DeepLink.VideoCall(communityId = it) }

            UserNotificationDeeplinkType.NEW_COMMENT ->
                reference?.let { DeepLink.NewComment(forumId = it) }

            UserNotificationDeeplinkType.NEW_LOOP ->
                if (reference != null && communityId != null) {
                    DeepLink.NewLoop(communityId = communityId, loopId = reference)
                } else {
                    null
                }

            UserNotificationDeeplinkType.LOOP_COMMENT ->
                reference?.let { DeepLink.NewLoopComment(loopId = it) }

            UserNotificationDeeplinkType.UNKNOWN -> null
        }
        if (link == null) {
            DeepLinkDiagnostics.fail(
                "5-parse",
                "notificationType='$type' was recognised but the record " +
                    "dialect is missing the id it needs " +
                    "(notificationReferenceId / notificationCommunityId). " +
                    "payload=$payload",
            )
        } else {
            DeepLinkDiagnostics.log(
                "5-parse",
                "notification-record dialect matched: notificationType=" +
                    "'$type' → $link",
            )
        }
        return link
    }

    /**
     * Parses a push payload into a [DeepLink], trying **both**
     * dialects.
     *
     * The push dialect ([parse]) is tried first: it is the one iOS
     * reads for a tapped notification, and it names the entity ids
     * directly. The notification-record dialect ([parseNotification]) is
     * the fallback, for a payload that carries only the record keys.
     */
    fun parseAny(payload: Map<String, String>): DeepLink? {
        val push = parse(payload)
        if (push != null) return push
        val record = parseNotification(payload)
        if (record != null) return record
        // Neither dialect matched. `parse` and `parseNotification` have
        // already logged why, so this line only states the outcome.
        DeepLinkDiagnostics.fail(
            "5-parse",
            "NO dialect matched — the payload carries no usable deep " +
                "link. payload=$payload " +
                "(keys=${payload.keys})",
        )
        return null
    }
}
