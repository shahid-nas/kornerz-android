package com.kornerz.feature.loops.comment

import com.kornerz.core.model.LoopCommentInfo
import com.kornerz.core.model.LoopDetailInfo
import com.kornerz.core.model.User

data class CreateCommentState(
    val text: String = "",
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val loopDetail: LoopDetailInfo? = null,
    val currentUser: User? = null,
    /**
     * The accent colour handed over by the detail screen the sheet was
     * opened from — the sheet inherits the colour the user is looking
     * at (iOS `CreateCommentScreen` renders the loop's title in
     * `loopInfo.color`).
     *
     * `null` when the sheet was opened without one, and the loop's own
     * colour — the one the domain layer derived from its id — is used.
     */
    val accentColorHex: String? = null,
    /**
     * The comment being replied to — set when the sheet is opened from
     * the reply thread's add-comment bar, empty for a top-level
     * comment. Mirrors iOS `CreateCommentType`.
     */
    val parentCommentId: String = "",
    /**
     * The resolved parent comment — the header the sheet renders above
     * the text box when it is opened in reply mode (iOS
     * `commentInfoView` → `LoopCommentView`).
     */
    val parentComment: LoopCommentInfo? = null,
) {
    /** Whether the sheet posts a **reply** rather than a top-level comment. */
    val isReply: Boolean get() = parentCommentId.isNotBlank()

    /**
     * The accent colour the sheet paints with, as a `#RRGGBB` hex
     * string — the colour handed over by the detail screen, the loop's
     * own colour otherwise.
     */
    val resolvedAccentColorHex: String
        get() = accentColorHex ?: loopDetail?.accentColorHex.orEmpty()
}
