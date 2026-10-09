package com.kornerz.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Korner domain model used by the Kornerz tab and Korner detail screens.
 *
 * Mirrors the public subset of iOS `Model/Community.swift` consumed by the
 * Phase 3 Android screens. Distinct from the narrower `Community` data
 * class used by the Home tab (`home-your-kornerz`) because the Kornerz
 * tab is a discovery surface and needs the full description, members
 * count, live flag, and joined flag.
 */
@Serializable
data class Korner(
    val id: String,
    val name: String,
    val themeColor: String,
    @SerialName("firebaseImageUrl") val coverUrl: String? = null,
    val description: String? = null,
    val membersCount: Int = 0,
    @SerialName("isLive") val isLive: Boolean = false,
    @SerialName("isJoined") val isJoined: Boolean = false,
)
