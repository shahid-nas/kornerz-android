package com.kornerz.feature.loops.reply

sealed interface ReplyNavEvent {
    /**
     * The thread is done — the host route pops it.
     */
    data object Pop : ReplyNavEvent

    /**
     * The add-comment bar was tapped — the host route opens the
     * create-comment sheet for this thread's parent comment.
     *
     * Mirrors iOS `LoopReplyCoordinator.showAddCommentSheet(type:
     * .replyToComment(loopId, commentInfo))`.
     *
     * @param accentColorHex the colour the thread is painted with, so
     * the sheet inherits it.
     */
    data class OpenCreateComment(
        val loopId: String,
        val parentCommentId: String,
        val accentColorHex: String,
    ) : ReplyNavEvent

    data class ShowError(val message: String) : ReplyNavEvent
}
