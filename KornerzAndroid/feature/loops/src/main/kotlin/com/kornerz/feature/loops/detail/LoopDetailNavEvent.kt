package com.kornerz.feature.loops.detail

sealed interface LoopDetailNavEvent {
    /**
     * The screen is done — the host route pops it.
     *
     * Mirrors the reply thread's `ReplyNavEvent.Pop`: the screen
     * never touches a `NavController` itself (architecture §2.4).
     */
    data object Pop : LoopDetailNavEvent

    /**
     * Opens the Korner detail of the loop's community.
     *
     * The id is the community's **Firestore document id** — the one the
     * `kornerz/{kornerId}` destination reads — not the loop's raw
     * `communityId` field.
     */
    data class NavigateToKornerDetail(val communityId: String) : LoopDetailNavEvent

    /**
     * Opens the create-comment sheet.
     *
     * @param loopId the loop the comment belongs to.
     * @param accentColorHex the colour the screen is painted with, so
     * the sheet inherits it (iOS `CreateCommentScreen` renders the
     * loop's title in `loopInfo.color`).
     */
    data class OpenCreateComment(
        val loopId: String,
        val accentColorHex: String,
    ) : LoopDetailNavEvent

    /**
     * Opens the reply thread.
     *
     * @param loopId the loop the thread belongs to.
     * @param commentId the comment being replied to.
     * @param accentColorHex the colour the screen is painted with, so
     * the thread inherits it.
     */
    data class OpenReply(
        val loopId: String,
        val commentId: String,
        val accentColorHex: String,
    ) : LoopDetailNavEvent
}
