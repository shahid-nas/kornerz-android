package com.kornerz.feature.nooks.detail

import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.NookDetail

/**
 * UiState for the Nook detail screen (spec `nook-detail/spec.md`,
 * architecture §2.2).
 */
data class NookDetailState(
    val detail: AsyncValue<NookDetail> = AsyncValue.Loading,
)
