package com.kornerz.feature.kornerz.detail

/**
 * Side-channel navigation events emitted by [KornerDetailViewModel]
 * (architecture §2.4). Both Nook detail and Member profile destinations
 * are deferred to P4 / P6 respectively, so the shell resolves these
 * to "Coming in Phase N" snackbars.
 */
sealed interface KornerDetailNavEvent {
    data class NavigateToNookDetail(val nookId: String) : KornerDetailNavEvent
    data class NavigateToMemberProfile(val userId: String) : KornerDetailNavEvent
}
