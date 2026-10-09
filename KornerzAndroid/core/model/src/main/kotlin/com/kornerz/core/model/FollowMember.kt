package com.kornerz.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FollowMember(
    @SerialName("fellowId") val id: String? = null,
    @SerialName("fellowCommunity") val communityFollowed: String? = null,
    @SerialName("fellowUserName") val userName: String? = null,
    @SerialName("fellowAtive") val active: Boolean? = null,
    @SerialName("fellowDateTime") val dateTime: Instant? = null,
)
