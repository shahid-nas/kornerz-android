package com.kornerz.feature.loops.reply

sealed interface ReplyNavEvent {
    data object Pop : ReplyNavEvent
    data class ShowError(val message: String) : ReplyNavEvent
}
