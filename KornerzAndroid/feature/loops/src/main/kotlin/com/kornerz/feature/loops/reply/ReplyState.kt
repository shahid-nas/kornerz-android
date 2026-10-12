package com.kornerz.feature.loops.reply

import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.LoopCommentInfo

data class ReplyState(
    val parentComment: AsyncValue<LoopCommentInfo> = AsyncValue.Loading,
    val replies: AsyncValue<List<LoopCommentInfo>> = AsyncValue.Loading,
    val text: String = "",
    val isSubmitting: Boolean = false,
    val error: String? = null,
)
