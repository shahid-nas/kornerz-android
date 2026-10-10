package com.kornerz.feature.nooks.data

import com.google.firebase.firestore.DocumentSnapshot
import com.kornerz.core.model.Korner
import com.kornerz.core.model.NookDetail
import com.kornerz.core.model.UpcomingNookItem
import com.kornerz.core.network.nooks.NookFirestoreApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for the Nooks tab and Nook detail screen. Wraps
 * [NookFirestoreApi] and exposes one-shot `Flow<Result<T>>` streams
 * (architecture §2.3) so the calling ViewModel maps them into
 * `AsyncValue<T>`.
 *
 * The repository carries the Firestore [DocumentSnapshot] cursor
 * across calls so the ViewModel can drive cursor-based pagination
 * the same way iOS does (`communityLastSnapshot` /
 * `eventLastSnapshot`). The cursor never leaves the repository.
 */
@Singleton
class NooksRepository @Inject constructor(
    private val api: NookFirestoreApi,
) {

    /**
     * Fetches one page of approved Korners ordered by `viewsCount`
     * descending. Mirrors iOS `fetchKornerz()`.
     */
    fun fetchLiveKornerzPage(after: DocumentSnapshot?): Flow<Result<NookFirestoreApi.KornerPage>> = flow {
        emit(api.fetchLiveKornerzPage(after = after))
    }

    /**
     * Fetches one page of open Events for the next [days] days and
     * joins each with its parent Korner and publisher. Mirrors iOS
     * `fetchUpcommingNooks()` end-to-end (the 3 parallel `async let`
     * joins are implemented inside [NookFirestoreApi.joinUpcoming]).
     */
    fun fetchUpcomingPage(
        after: DocumentSnapshot?,
        days: Int = 30,
    ): Flow<Result<NooksUpcomingPage>> = flow {
        val pageResult = api.fetchUpcomingEventsPage(after = after, days = days)
        pageResult.fold(
            onSuccess = { page ->
                val joined = api.joinUpcoming(page.items).getOrElse {
                    return@flow emit(Result.failure(it))
                }
                emit(
                    Result.success(
                        NooksUpcomingPage(
                            items = joined,
                            lastSnapshot = page.lastSnapshot,
                            hasMore = page.hasMore,
                        ),
                    ),
                )
            },
            onFailure = { emit(Result.failure(it)) },
        )
    }

    /**
     * Fetches a single Korner by document id. Used by callers that
     * already have a `communityId` from a Nook / Event document.
     */
    fun fetchKornerById(kornerId: String): Flow<Result<Korner?>> = flow {
        emit(api.fetchKornerzByCommunityId(listOf(kornerId)).map { it.firstOrNull() })
    }

    /** Nook detail aggregate. Mirrors iOS `NookDetailViewModel`. */
    fun fetchNookDetail(nookId: String): Flow<Result<NookDetail>> = flow {
        emit(api.fetchNookDetail(nookId))
    }
}

/** Joined aggregate of one upcoming-tab page (Nook + publisher + parent Korner). */
data class NooksUpcomingPage(
    val items: List<UpcomingNookItem>,
    val lastSnapshot: DocumentSnapshot?,
    val hasMore: Boolean,
)
