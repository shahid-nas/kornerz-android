package com.kornerz.feature.loops.detail

import com.kornerz.core.model.LoopCommentInfo

sealed interface LoopDetailEvent {
    data object Retry : LoopDetailEvent
    data object Refresh : LoopDetailEvent
    data class MediaTapped(val loopId: String) : LoopDetailEvent
    data class KornerTapped(val communityId: String) : LoopDetailEvent
    data object LikeToggled : LoopDetailEvent
    data class AddCommentTapped(val loopId: String) : LoopDetailEvent
    data class CommentAvatarTapped(val commentId: String) : LoopDetailEvent
    data class CommentLikeToggled(val commentInfo: LoopCommentInfo) : LoopDetailEvent
    data class CommentDeleteConfirmed(val commentId: String) : LoopDetailEvent
    data class CommentReplyTapped(val commentId: String) : LoopDetailEvent
}
