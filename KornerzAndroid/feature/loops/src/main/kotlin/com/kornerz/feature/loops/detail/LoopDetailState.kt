package com.kornerz.feature.loops.detail

import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.Community
import com.kornerz.core.model.Loop
import com.kornerz.core.model.LoopCommentInfo
import com.kornerz.core.model.LoopDetailInfo
import com.kornerz.core.model.User

/**
 * The loop-detail screen's state.
 *
 * The screen renders the resolved bundle (loop + owner + community +
 * accent colour) rather than the raw `Loop`, which is what iOS hands
 * the screen as its `LoopInfo` argument object. The comment list is a
 * list of [LoopCommentInfo] projections, so the UI never has to join
 * the comment, its author and its engagement state itself.
 */
data class LoopDetailState(
    val detail: AsyncValue<LoopDetailInfo> = AsyncValue.Loading,
    val comments: AsyncValue<List<LoopCommentInfo>> = AsyncValue.Loading,
    val isLikedByUser: Boolean = false,
    val isLikeInProgress: Boolean = false,
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val hasIncrementedViewCount: Boolean = false,
    /**
     * The signed-in user's `codeUser` — the identity every write action
     * is gated on. `null` while the session is anonymous or carries no
     * code, in which case the like, comment-like and delete actions are
     * no-ops.
     */
    val currentUserCode: String? = null,
    /**
     * The signed-in user's avatar URL — the add-comment bar renders it
     * in its diamond avatar.
     */
    val currentUserAvatarUrl: String? = null,
    /** Whether the modal video player is open. */
    val isVideoPlayerOpen: Boolean = false,
    /**
     * Whether a pull-to-refresh is in flight. A refresh keeps the
     * loaded content on screen — only the very first load replaces it
     * with the shimmer — so the scroll position survives.
     */
    val isRefreshing: Boolean = false,
    /**
     * The accent colour handed over by the screen that opened this
     * loop — the feed row's colour, the Home tile's colour.
     *
     * iOS passes the feed row's colour to the detail screen
     * (`LoopsTabView.swift:114-118` → `LoopsTabCoordinator
     * .navigateToLoopDetail(_:color:)`), so the detail paints itself in
     * the colour the user tapped. `null` when the destination was
     * opened without one — a deep link, a notification — and the
     * screen falls back to the loop-id-derived palette entry.
     */
    val accentColorHex: String? = null,
) {

    /** The resolved loop, or `null` while the detail is loading. */
    val loop: Loop?
        get() = (detail as? AsyncValue.Success<LoopDetailInfo>)?.data?.loop

    /**
     * Whether the screen is still loading its first payload.
     *
     * `true` while the detail is loading **or** has failed — in both
     * cases the loop is unresolved, so the add-comment bar stays
     * disabled: the create-comment sheet needs the resolved loop to
     * post against it.
     *
     * A pull-to-refresh does **not** flip it: the refresh keeps the
     * loaded content on screen, so the bar stays tappable.
     */
    val isLoading: Boolean
        get() = detail is AsyncValue.Loading || detail is AsyncValue.Error

    /** The resolved owner (the loop's publisher), or `null`. */
    val owner: User?
        get() = (detail as? AsyncValue.Success<LoopDetailInfo>)?.data?.owner

    /** The resolved community (the loop's Korner), or `null`. */
    val community: Community?
        get() = (detail as? AsyncValue.Success<LoopDetailInfo>)?.data?.community

    /**
     * The accent colour the screen paints with, as a `#RRGGBB` hex
     * string.
     *
     * The colour handed over by the caller wins — it is the colour the
     * user tapped — and the loop-id-derived palette entry is the
     * fallback for the destinations that carry no colour (a deep link,
     * a notification).
     */
    val resolvedAccentColorHex: String
        get() = accentColorHex
            ?: (detail as? AsyncValue.Success<LoopDetailInfo>)?.data?.accentColorHex
            ?: DEFAULT_ACCENT_COLOR_HEX

    /**
     * Whether the signed-in user may delete [comment] — the delete
     * swipe action is offered **only** for the user's own comments,
     * mirroring iOS `LoopDetailViewModel.isCommentOwnedByUser`.
     */
    fun canDelete(comment: LoopCommentInfo): Boolean {
        val code = currentUserCode ?: return false
        return comment.comment.userId == code
    }

    companion object {
        /** The first entry of the iOS accent palette. */
        const val DEFAULT_ACCENT_COLOR_HEX: String = "#E27D5A"
    }
}
