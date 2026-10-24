package com.kornerz.feature.loops.reply

import com.kornerz.core.model.LoopCommentInfo

sealed interface ReplyEvent {
    data object BackTapped : ReplyEvent
    /**
     * The add-comment bar was tapped — the thread opens the
     * create-comment sheet in reply mode.
     */
    data object AddCommentTapped : ReplyEvent
    data class CommentLikeToggled(val info: LoopCommentInfo) : ReplyEvent
    data class CommentDeleteConfirmed(val commentId: String) : ReplyEvent
}
