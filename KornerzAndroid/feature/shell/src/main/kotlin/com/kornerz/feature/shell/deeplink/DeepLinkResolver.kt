package com.kornerz.feature.shell.deeplink

import com.kornerz.core.model.DeepLink
import com.kornerz.core.network.community.CommunitiesRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides where a [DeepLink] goes — or that it cannot go anywhere.
 *
 * This is the **single place** that knows which deep links are live in
 * the Android port. The routability matrix:
 *
 * | `DeepLink` case | Target | Routable on Android? |
 * |---|---|---|
 * | `NewLoop` | `LoopDetail(loopId)` | yes — `loops/{loopId}` |
 * | `NewLoopComment` | `LoopDetail(loopId)` | yes — `loops/{loopId}` |
 * | `VideoCall` | `KornerDetail(kornerId)` | yes — `kornerz/{kornerId}` |
 * | `NewEvent` | `KornerDetail(kornerId)` | yes — the community's detail |
 * | `NewArticle` | `KornerDetail(kornerId)` | yes — the community's detail |
 * | `NewForum` | `KornerDetail(kornerId)` | yes — the community's detail |
 * | `NewComment` | `KornerDetail(kornerId)` | yes — the community's detail |
 *
 * **The community-scoped links route to the Korner detail.** Event
 * Detail, Post Detail and Forum Detail are excluded from the Android
 * port (`docs/screen-inventory.md:229-233`), so there is no dedicated
 * screen to open for them. The community that owns the content is still
 * live in the port, and its detail screen is where the content is
 * listed — so a tap lands the user on the community the notification is
 * about instead of failing with a "not routable" alert. iOS opens the
 * dedicated detail screen for these; Android opens the community.
 *
 * `NewComment` carries only a forum id, with no community id — the
 * forum's community is not known to the client, so the link resolves
 * through the forum's own document.
 *
 * A `VideoCall` link resolves to the **Korner detail**, not to the
 * video-call screen — iOS's `handleCallLink` pushes a
 * `KornerDetailController` (`DeeplinkViewController.swift:135-155`),
 * and the call itself stays reachable only from the Korner detail.
 *
 * The `kornerId` the target carries is the Korner's **document id**,
 * while the link carries the community's `communityId` **field**. The
 * resolver bridges the two with one `Communities.whereEqualTo(
 * "communityId", id)` lookup; a community id that does not resolve is
 * reported as not routable.
 */
@Singleton
class DeepLinkResolver @Inject constructor(
    private val communitiesRepository: CommunitiesRepository,
) {

    /**
     * Resolves [deeplink] to a live destination, or reports that it
     * cannot be routed.
     */
    suspend fun resolve(deeplink: DeepLink): DeepLinkResolution = when (deeplink) {
        is DeepLink.NewLoop -> DeepLinkResolution.Routable(
            DeepLinkTarget.LoopDetail(deeplink.loopId),
        )

        is DeepLink.NewLoopComment -> DeepLinkResolution.Routable(
            DeepLinkTarget.LoopDetail(deeplink.loopId),
        )

        is DeepLink.VideoCall -> resolveCommunity(deeplink.communityId)

        is DeepLink.NewEvent -> resolveCommunity(deeplink.communityId)

        is DeepLink.NewArticle -> resolveCommunity(deeplink.communityId)

        is DeepLink.NewForum -> resolveCommunity(deeplink.communityId)

        is DeepLink.NewComment -> resolveForumCommunity(deeplink.forumId)
    }

    /**
     * Resolves a community-scoped link to the community's Korner detail.
     */
    private suspend fun resolveCommunity(communityId: String): DeepLinkResolution {
        val documentId = communitiesRepository.fetchDocumentIdByCommunityId(communityId)
        return if (documentId != null) {
            DeepLinkResolution.Routable(DeepLinkTarget.KornerDetail(documentId))
        } else {
            DeepLinkResolution.NotRoutable
        }
    }

    /**
     * Resolves a forum comment link.
     *
     * A comment notification names the **forum**, not the community, so
     * the forum's document is read to find the community that owns it.
     */
    private suspend fun resolveForumCommunity(forumId: String): DeepLinkResolution {
        val communityId = communitiesRepository.fetchForumCommunityId(forumId)
        return if (communityId != null) {
            resolveCommunity(communityId)
        } else {
            DeepLinkResolution.NotRoutable
        }
    }
}
