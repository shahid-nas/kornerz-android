package com.kornerz.feature.loops.detail

import com.kornerz.core.model.LoopCommentInfo

sealed interface LoopDetailEvent {
    data object Retry : LoopDetailEvent
    data object Refresh : LoopDetailEvent

    /**
     * The back affordance was tapped — the screen leaves the detail
     * and returns to the list it was opened from.
     */
    data object BackTapped : LoopDetailEvent
    data class MediaTapped(val loopId: String) : LoopDetailEvent
    data class KornerTapped(val communityId: String) : LoopDetailEvent
    data object LikeToggled : LoopDetailEvent
    data class AddCommentTapped(val loopId: String) : LoopDetailEvent
    data class CommentAvatarTapped(val commentId: String) : LoopDetailEvent
    data class CommentLikeToggled(val commentInfo: LoopCommentInfo) : LoopDetailEvent
    data class CommentDeleteConfirmed(val commentId: String) : LoopDetailEvent
    data class CommentReplyTapped(val commentId: String) : LoopDetailEvent

    /** Closes the modal video player. */
    data object VideoDismissed : LoopDetailEvent

    /**
     * A comment body's expand/collapse toggle was tapped. The shared
     * `KornerzExpandableText` component owns its own expansion state,
     * so the screen does not have to mirror it — the event exists so
     * the interaction is still part of the UDF contract.
     */
    data class CommentBodyToggled(val commentId: String) : LoopDetailEvent
}
