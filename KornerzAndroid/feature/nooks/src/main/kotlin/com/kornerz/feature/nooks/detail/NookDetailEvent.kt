package com.kornerz.feature.nooks.detail

/**
 * UiEvent for the Nook detail screen (architecture §2.1).
 */
sealed interface NookDetailEvent {
    data object Retry : NookDetailEvent
    data object BackToKornerClicked : NookDetailEvent
}
