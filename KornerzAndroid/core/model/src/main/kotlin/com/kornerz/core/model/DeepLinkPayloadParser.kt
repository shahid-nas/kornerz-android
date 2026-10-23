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
     * Parses a push payload into a [DeepLink], or returns `null` when
     * the payload does not describe one.
     */
    fun parse(payload: Map<String, String>): DeepLink? {
        val pushType = payload["pushType"]?.takeIf { it.isNotBlank() } ?: return null
        return when (pushType.lowercase()) {
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
}
