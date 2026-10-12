package com.kornerz.feature.loops.detail

import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.Loop
import com.kornerz.core.model.LoopCommentInfo

data class LoopDetailState(
    val detail: AsyncValue<Loop> = AsyncValue.Loading,
    val comments: AsyncValue<List<LoopCommentInfo>> = AsyncValue.Loading,
    val isLikedByUser: Boolean = false,
    val isLikeInProgress: Boolean = false,
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val hasIncrementedViewCount: Boolean = false,
)
