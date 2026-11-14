package com.kornerz.feature.loops.data

import com.kornerz.core.model.Community
import com.kornerz.core.model.Loop
import com.kornerz.core.model.User
import com.kornerz.feature.loops.components.LoopCardData

/**
 * Maps the network `Loop` model + the parent community + the publisher
 * user into the UI projection consumed by the Loops tab list, the
 * loop detail screen, and the Home recent-loops carousel.
 *
 * The mapping mirrors the iOS `LoopsTabViewModel.loopsFetched`
 * DispatchGroup fan-out: each loop is enriched with the parent
 * community name (from `communityName` / `communityId`) and the
 * publisher's nickname / avatar.
 */
object LoopMapper {

    fun toCardData(loop: Loop, community: Community?, owner: User?): LoopCardData {
        val id = loop.id.orEmpty()
        return LoopCardData(
            id = id,
            title = loop.title.orEmpty(),
            description = loop.description,
            descriptionHtml = loop.descriptionHtml,
            communityId = loop.communityId,
            communityName = community?.name ?: loop.communityName,
            publisherId = loop.publisherId,
            publisherNickname = owner?.displayName ?: loop.publisherName,
            publisherThumbnailUrl = owner?.thumbnailURL ?: loop.publisherAvatarUrl,
            theme = loop.theme,
            // `iOsThumbnailUrl` / `iOsImageUrl` resolve the Firebase
            // Storage thumbnail and image, falling back from the
            // thumbnail to the image.
            thumbnailUrl = loop.iOsThumbnailUrl,
            imageUrl = loop.iOsImageUrl,
            videoUrl = loop.iOsVideoUrl,
            isVideo = loop.isVideo,
            viewCounts = loop.viewCounts ?: 0,
            commentsCount = loop.commentsCount ?: 0,
            likesCount = loop.likesCount ?: 0,
            creationDate = loop.creationDate ?: loop.createdAt,
        )
    }
}

fun Loop.toCardData(community: Community?, owner: User?): LoopCardData =
    LoopMapper.toCardData(this, community, owner)

fun List<Loop>.toCardData(
    communitiesById: Map<String, Community>,
    usersById: Map<String, User>,
): List<LoopCardData> = map { loop ->
    val community = loop.communityId?.let { communitiesById[it] }
    val owner = loop.publisherId?.let { usersById[it] }
    loop.toCardData(community, owner)
}
