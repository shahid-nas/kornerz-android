package com.kornerz.feature.shell.deeplink

/**
 * A routable deep-link destination.
 *
 * These are **semantic** targets, not route strings: both destinations
 * live inside a tab's nested `NavHost` — the loop detail in the Loops
 * tab, the Korner detail in the Kornerz tab — so the shell's root
 * controller cannot push them directly. The shell owns the navigation
 * and consumes the target through its existing cross-tab mechanism
 * (`pendingLoopTarget` / `pendingKornerId` plus the tab switch).
 *
 * The `kornerId` a [KornerDetail] carries is the Korner's **Firestore
 * document id** — the Korner detail route is addressed by document id
 * (`KornerFirestoreApi.fetchDetail` does
 * `collection("Communities").document(kornerId)`), while a deep link
 * carries the community's `communityId` field. The resolver bridges the
 * two.
 */
sealed interface DeepLinkTarget {

    /** The loop detail — `loops/{loopId}` in the Loops tab's nested graph. */
    data class LoopDetail(val loopId: String) : DeepLinkTarget

    /** The Korner detail — `kornerz/{kornerId}` in the Kornerz tab's nested graph. */
    data class KornerDetail(val kornerId: String) : DeepLinkTarget
}

/**
 * The resolver's answer for a deep link: a routable [DeepLinkTarget],
 * or the explicit "not routable in this build" outcome.
 */
sealed interface DeepLinkResolution {

    /** The link targets a live screen. */
    data class Routable(val target: DeepLinkTarget) : DeepLinkResolution

    /**
     * The link names a destination that could not be resolved — the
     * community it belongs to does not exist, or the forum it names
     * does not exist.
     *
     * The links whose dedicated screens are excluded from the Android
     * port (Event Detail, Post Detail, Forum Detail —
     * `docs/screen-inventory.md:229-233`) are **not** reported here:
     * they resolve to the owning community's Korner detail instead.
     */
    data object NotRoutable : DeepLinkResolution
}
