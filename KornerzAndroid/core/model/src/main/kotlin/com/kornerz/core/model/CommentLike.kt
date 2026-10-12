package com.kornerz.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Records a like on a loop comment. Mirrors the iOS `CommentLike` struct
 * (`kornerz/Kornerz/Models/CommentLike.swift`). The `userId` and `commentId`
 * fields are required (non-nullable) per iOS `CommentLike.swift:11-16`;
 * they form the composite primary key used by
 * `loopComments/{commentId}/likes/{userId}`.
 */
@Serializable
data class CommentLike(
    @SerialName("id")
    val id: String? = null,
    @SerialName("userId")
    val userId: String,
    @SerialName("commentId")
    val commentId: String,
    @SerialName("creationDate")
    val creationDate: Instant? = null,
)
