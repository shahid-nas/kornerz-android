package com.kornerz.feature.loops.reply

import com.kornerz.core.model.LoopCommentInfo

sealed interface ReplyEvent {
    data class TextChanged(val text: String) : ReplyEvent
    data object SendTapped : ReplyEvent
    data object BackTapped : ReplyEvent
    data class CommentLikeToggled(val info: LoopCommentInfo) : ReplyEvent
    data class CommentDeleteConfirmed(val commentId: String) : ReplyEvent
}
