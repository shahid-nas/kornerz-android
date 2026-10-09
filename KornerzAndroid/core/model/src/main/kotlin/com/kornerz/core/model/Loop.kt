package com.kornerz.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Loop(
    val id: String,
    val title: String,
    @SerialName("thumbnailUrl") val thumbnailUrl: String? = null,
    val color: String? = null,
    @SerialName("durationSeconds") val durationSeconds: Int? = null,
    @SerialName("createdAt") val createdAt: Instant,
    @SerialName("publisherId") val publisherId: String? = null,
    @SerialName("publisherName") val publisherName: String? = null,
    @SerialName("publisherAvatarUrl") val publisherAvatarUrl: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("communityId") val communityId: String? = null,
    @SerialName("communityName") val communityName: String? = null,
    @SerialName("theme") val theme: String? = null,
    @SerialName("viewCounts") val viewCounts: Int? = null,
    @SerialName("commentsCount") val commentsCount: Int? = null,
    @SerialName("likesCount") val likesCount: Int? = null,
)
