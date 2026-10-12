package com.kornerz.feature.loops.comment

sealed interface CreateCommentNavEvent {
    data object Dismiss : CreateCommentNavEvent
    data class OnError(val message: String) : CreateCommentNavEvent
}
