package com.kornerz.feature.loops.comment

sealed interface CreateCommentEvent {
    data class TextChanged(val text: String) : CreateCommentEvent
    data object CancelTapped : CreateCommentEvent
    data object SendTapped : CreateCommentEvent
}
