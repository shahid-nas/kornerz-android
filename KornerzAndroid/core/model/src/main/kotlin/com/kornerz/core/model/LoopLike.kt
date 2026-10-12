package com.kornerz.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Records a like on a loop. Mirrors the iOS `LoopLike` struct
 * (`kornerz/Kornerz/Models/LoopLike.swift`). The `userId` and `loopId`
 * fields are required (non-nullable) per iOS `LoopLike.swift:12-16`; they
 * form the composite primary key used by `users/{userId}/likedLoops/{loopId}`.
 */
@Serializable
data class LoopLike(
    @SerialName("id")
    val id: String? = null,
    @SerialName("userId")
    val userId: String,
    @SerialName("loopId")
    val loopId: String,
    @SerialName("creationDate")
    val creationDate: Instant? = null,
)
