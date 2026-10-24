package com.kornerz.feature.loops.reply

import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.LoopCommentInfo

data class ReplyState(
    val parentComment: AsyncValue<LoopCommentInfo> = AsyncValue.Loading,
    val replies: AsyncValue<List<LoopCommentInfo>> = AsyncValue.Loading,
    val error: String? = null,
    /**
     * The per-loop accent colour as a `#RRGGBB` hex string — the same
     * colour the loop detail screen uses for this loop, so the reply
     * thread and the detail screen agree.
     */
    val accentColorHex: String = "#E27D5A",
    /**
     * The signed-in user's `codeUser` — gates the like and delete
     * actions on a reply. `null` while the session is anonymous.
     */
    val currentUserCode: String? = null,
    /**
     * The signed-in user's avatar, rendered in the add-comment bar.
     */
    val currentUserAvatarUrl: String? = null,
)
