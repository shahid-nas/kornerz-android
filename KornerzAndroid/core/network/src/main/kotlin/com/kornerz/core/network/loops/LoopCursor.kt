package com.kornerz.core.network.loops

import com.google.firebase.firestore.DocumentSnapshot

/**
 * Opaque cursor for paginating loop feeds. Mirrors the iOS
 * `lastDocument` cursor that iOS `LoopsTabViewModel` and
 * `LoopsFilterViewModel` carry across pages. Consumers never reach
 * into the document — they hand the cursor back to the repository.
 */
@JvmInline
value class LoopCursor(val snapshot: DocumentSnapshot?)

/** One page of items with an optional [LoopCursor] for the next page. */
data class PagingResult<T>(
    val items: List<T>,
    val nextCursor: LoopCursor?,
    val hasMore: Boolean,
)
