package com.kornerz.feature.kornerz.detail

import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.KornerDetail

/**
 * UiState for the Korner detail screen (spec `korner-detail/spec.md`).
 *
 * Holds the aggregate `KornerDetail` payload wrapped in [AsyncValue]
 * (architecture §2.2). The content composable pattern-matches on the
 * wrapped value to render shimmer / error / content layouts.
 */
data class KornerDetailState(
    val detail: AsyncValue<KornerDetail> = AsyncValue.Loading,
)
