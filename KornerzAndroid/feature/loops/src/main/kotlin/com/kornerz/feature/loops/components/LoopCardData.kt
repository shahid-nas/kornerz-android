package com.kornerz.feature.loops.components

import kotlinx.datetime.Instant

/**
 * UI projection of a loop row. Internal to the Loops feature; the
 * `core/model/Loop` `@Serializable` class is the network-shape model.
 *
 * Composed by [com.kornerz.feature.loops.data.LoopMapper] from a
 * [com.kornerz.core.model.Loop] + the parent community + the publisher
 * user. Consumed by `LoopsContent`, `LoopDetailContent`, and
 * `feature/home/.../RecentLoopsContent` (after Section 13 refactor).
 */
data class LoopCardData(
    val id: String,
    val title: String,
    val description: String?,
    val descriptionHtml: String?,
    val communityId: String?,
    val communityName: String?,
    val publisherId: String?,
    val publisherNickname: String?,
    val publisherThumbnailUrl: String?,
    val theme: String?,
    val thumbnailUrl: String?,
    val imageUrl: String?,
    val videoUrl: String,
    val isVideo: Boolean,
    val viewCounts: Int,
    val commentsCount: Int,
    val likesCount: Int,
    val creationDate: Instant?,
)
