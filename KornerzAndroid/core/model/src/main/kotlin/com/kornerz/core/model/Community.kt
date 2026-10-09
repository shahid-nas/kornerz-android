package com.kornerz.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Community(
    @SerialName("communityId") val id: String,
    @SerialName("communityName") val name: String,
    @SerialName("firebaseImageUrl") val coverUrl: String? = null,
    val themeColor: String? = null,
    val isMember: Boolean = true,
)
