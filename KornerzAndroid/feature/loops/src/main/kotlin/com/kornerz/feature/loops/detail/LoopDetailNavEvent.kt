package com.kornerz.feature.loops.detail

sealed interface LoopDetailNavEvent {
    data class NavigateToKornerDetail(val communityId: String) : LoopDetailNavEvent
    data class OpenCreateComment(val loopId: String) : LoopDetailNavEvent
    data class OpenReply(val loopId: String, val commentId: String) : LoopDetailNavEvent
}
