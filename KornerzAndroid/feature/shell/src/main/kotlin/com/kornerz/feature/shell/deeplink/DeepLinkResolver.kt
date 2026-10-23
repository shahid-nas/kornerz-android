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
 * | `NewEvent` | — | no — Event Detail is excluded |
 * | `NewArticle` | — | no — Post Detail is excluded |
 * | `NewForum` | — | no — Forum Detail is excluded |
 * | `NewComment` | — | no — Forum Detail is excluded |
 *
 * The four excluded targets stay excluded: `docs/screen-inventory.md:229-237`
 * records the stakeholder decision that Event Detail, Post Detail and
 * Forum Detail are out of the Android port.
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

        is DeepLink.VideoCall -> {
            val documentId = communitiesRepository.fetchDocumentIdByCommunityId(
                deeplink.communityId,
            )
            if (documentId != null) {
                DeepLinkResolution.Routable(DeepLinkTarget.KornerDetail(documentId))
            } else {
                DeepLinkResolution.NotRoutable
            }
        }

        is DeepLink.NewEvent,
        is DeepLink.NewArticle,
        is DeepLink.NewForum,
        is DeepLink.NewComment,
        -> DeepLinkResolution.NotRoutable
    }
}
