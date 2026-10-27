package com.kornerz.feature.loops.data

import com.kornerz.core.model.Loop
import com.kornerz.core.model.LoopsFilter
import com.kornerz.core.network.loops.LoopCursor
import com.kornerz.core.network.loops.LoopFirestoreApi
import com.kornerz.core.network.loops.PagingResult
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for the Loops tab list + loop detail. Wraps
 * [LoopFirestoreApi] and exposes `Flow<Result<PagingResult<Loop>>>`
 * streams (architecture §2.3) so the calling ViewModel maps them into
 * `AsyncValue<T>`.
 *
 * Mirrors the iOS `LoopRepository` shape — feed (paginated by
 * `creationDate` desc), per-publisher feed (Your Loops filter), and
 * detail (`get()`).
 *
 * For the `YOUR_LOOPS` filter the repository forwards the signed-in
 * user's **user code** (`codeUser`) — the same identity iOS uses in
 * `AppHelper.shared.loggedInUser?.id` (`User.id` is
 * `codeUser ?? userId`, `kornerz/Kornerz/Models/User.swift:57-59`),
 * and the same value loop documents store in `publisherId`. It is
 * **not** the Firebase Auth uid: the app signs in anonymously
 * (`FirestoreAuthApi.signInFirebaseAnonymously`), so the uid is a
 * throwaway identity that never matches any `publisherId`. When no
 * code is available the API emits a `Result.failure` so the ViewModel
 * surfaces the error state instead of a misleading empty list.
 */
@Singleton
class LoopRepository @Inject constructor(
    private val api: LoopFirestoreApi,
) {

    fun observeLoops(
        filter: LoopsFilter,
        searchQuery: String,
        cursor: LoopCursor?,
        currentUserCode: String? = null,
        pageSize: Int = LoopFirestoreApi.DEFAULT_PAGE_SIZE,
    ): Flow<Result<PagingResult<Loop>>> =
        api.observeLoops(
            filter = filter,
            searchQuery = searchQuery,
            cursor = cursor,
            currentUserCode = currentUserCode,
            pageSize = pageSize,
        )

    fun fetchLoopDetail(loopId: String): Flow<Result<Loop>> =
        api.fetchLoopDetail(loopId)

    fun fetchRecentLoops(limit: Int = 10): Flow<Result<List<Loop>>> = kotlinx.coroutines.flow.flow {
        emit(api.fetchRecentLoops(limit.toLong()))
    }

    suspend fun incrementViewCount(loopId: String): Result<Unit> =
        api.incrementViewCount(loopId)
}
