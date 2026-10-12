package com.kornerz.core.model

/**
 * UI projection of a comment row — composite of a [LoopComment] plus the
 * resolved [User] (publisher) and the like-state / like-count for the
 * signed-in user. Mirrors the iOS `LoopCommentInfo` struct
 * (`kornerz/SwiftUI/Dashboard/Loops/LoopDetail/LoopCommentInfo.swift`).
 *
 * Used by `LoopDetailContent` and the reply screen so the UI consumes a
 * single object instead of three parallel lists.
 */
data class LoopCommentInfo(
    val comment: LoopComment,
    val user: User? = null,
    val isLikedByUser: Boolean = false,
    val likesCount: Int = 0,
)
