package com.kornerz.feature.kornerz.detail

/**
 * Side-channel navigation events emitted by [KornerDetailViewModel]
 * (architecture §2.4).
 *
 * The Korner detail is hosted by the Kornerz tab's **nested**
 * `NavHost`, so every destination it pushes is resolved by the shell
 * against that nested controller — the screen never references a
 * `NavController` itself.
 */
sealed interface KornerDetailNavEvent {
    /** The hero's back chevron was tapped — pop the Korner detail. */
    data object NavigateBack : KornerDetailNavEvent

    /** Opens the nook detail for [nookId]. */
    data class NavigateToNookDetail(val nookId: String) : KornerDetailNavEvent

    /** Opens the member profile for [userId]. */
    data class NavigateToMemberProfile(val userId: String) : KornerDetailNavEvent

    /**
     * Opens the loop detail for [loopId] — iOS pushes it full-screen.
     *
     * [accentColorHex] is the colour the tapped row was painted with
     * (iOS `loopInfo.color`), so the detail opens in the same colour.
     */
    data class NavigateToLoopDetail(
        val loopId: String,
        val accentColorHex: String,
    ) : KornerDetailNavEvent

    /**
     * A transient message for the shell's shared snackbar — used for
     * the actions that are not wired to the backend yet (joining a live
     * session) and for failed follow / unfollow writes.
     */
    data class ShowMessage(val message: String) : KornerDetailNavEvent
}
