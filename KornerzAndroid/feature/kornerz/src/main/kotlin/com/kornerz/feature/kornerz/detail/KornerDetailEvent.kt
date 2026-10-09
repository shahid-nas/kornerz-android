package com.kornerz.feature.kornerz.detail

/**
 * UiEvent for the Korner detail screen.
 *
 * Emitted by the content composable; the VM forwards them to either
 * the navigation channel or the local state mutator.
 */
sealed interface KornerDetailEvent {
    data object Retry : KornerDetailEvent
    data object JoinCtaClicked : KornerDetailEvent
    data class NookTileClicked(val nookId: String) : KornerDetailEvent
    data class MemberTileClicked(val userId: String) : KornerDetailEvent
}
