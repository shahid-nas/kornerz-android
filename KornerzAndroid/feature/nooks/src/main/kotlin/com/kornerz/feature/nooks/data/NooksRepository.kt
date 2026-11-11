package com.kornerz.feature.nooks.data

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.kornerz.core.model.Korner
import com.kornerz.core.model.NookDetail
import com.kornerz.core.model.SessionInfo
import com.kornerz.core.model.UpcomingNookItem
import com.kornerz.core.network.nooks.NookFirestoreApi
import com.kornerz.core.network.nooks.SessionInfoApi
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
    private val sessionInfoApi: SessionInfoApi,
) {

    /**
     * Fetches one page of approved Korners ordered by `viewsCount`
     * descending, then enriches each Korner with the session id the
     * "Join" button has to open.
     *
     * The room is resolved **community first**, matching iOS: the live
     * Nooks card joins `community.sessionID` (the `communitySessionId`
     * field). The live event's `sessionId` is only used when the
     * community has no `communitySessionId` of its own.
     *
     * The event lookup tries two key forms because the Firestore
     * Communities schema varies between the earlier "doc-id =
     * communityId" deployments and the current "doc-id ≠ communityId"
     * deployments:
     *   1. `nook.communityId == korner.communityId` (the iOS-spec
     *      relationship; `nook.communityId` reads from the
     *      `eventCommunityId` field on the Event doc)
     *   2. `nook.communityId == korner.id` (fallback for older
     *      Communities docs where the public stable id equals the
     *      Firestore document id)
     */
    fun fetchLiveKornerzPage(after: DocumentSnapshot?): Flow<Result<NookFirestoreApi.KornerPage>> = flow {
        val pageResult = api.fetchLiveKornerzPage(after = after)
        pageResult.fold(
            onSuccess = { page ->
                val liveEvents = api.fetchLiveEvents()
                    .getOrNull()
                    ?.items
                    .orEmpty()

                val sessionIdByCommunityId = mutableMapOf<String, String>()
                for (nook in liveEvents) {
                    val communityId = nook.communityId.trim()
                    val sessionId = nook.sessionId
                    if (communityId.isNotEmpty() &&
                        !sessionId.isNullOrBlank() &&
                        !sessionIdByCommunityId.containsKey(communityId)
                    ) {
                        sessionIdByCommunityId[communityId] = sessionId
                    }
                }

                val enriched = page.items.map { korner ->
                    val candidateKeys = buildList {
                        korner.communityId?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
                        add(korner.id)
                    }
                    // iOS parity — the live Nooks card joins the
                    // COMMUNITY room, not the live event's room:
                    //   iOS `NooksTabView.kornerNooks` →
                    //   `NooksTabCoordinator.joinVideoCall(nook.community)` →
                    //   `UIViewController.joinCall(sessionId: nil, community:)` →
                    //   `sessionId ?? community.sessionID`
                    // and `community.sessionID` is the `communitySessionId`
                    // field (iOS `Community.CodingKeys`).
                    //
                    // Preferring the live *event's* `sessionId` here put
                    // the Android client in a different Vonage room than
                    // the iOS client joining from the same screen, so the
                    // two never saw each other's subscribers — the
                    // Android call just kept showing "Waiting for others
                    // to join". The event's session id stays as the
                    // fallback for communities that have no
                    // `communitySessionId` yet (and it keeps the "Join"
                    // button visible for them).
                    val eventSessionId = candidateKeys
                        .mapNotNull { sessionIdByCommunityId[it] }
                        .firstOrNull()
                    val sessionId = korner.sessionId?.takeIf { it.isNotBlank() } ?: eventSessionId
                    val source = if (!korner.sessionId.isNullOrBlank()) {
                        "community"
                    } else {
                        "event"
                    }
                    Log.i(
                        TAG,
                        "session for korner '${korner.name}' (id=${korner.id}) → '$sessionId' (source=$source, " +
                            "communitySessionId=${korner.sessionId}, eventSessionId=$eventSessionId)",
                    )
                    if (sessionId != null) korner.copy(sessionId = sessionId) else korner
                }

                // iOS parity — the live card shows who is in the room
                // and how many spots are left. iOS fetches
                // `getSessionsInfo` for every community session id
                // alongside the community page
                // (`NooksTabViewModel.fetchKornerz()`, line 112) and
                // attaches the roster to the wrapper.
                val sessionsById = sessionInfoApi.fetchSessionsInfo(
                    enriched.mapNotNull { it.sessionId },
                )

                val withSessionInfo = enriched.map { korner ->
                    val info = korner.sessionId?.let { sessionsById[it] }
                    korner.copy(sessionInfo = info)
                }

                emit(
                    Result.success(
                        NookFirestoreApi.KornerPage(
                            items = withSessionInfo,
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
     * Fetches one page of open Events for the next [days] days and
     * joins each with its parent Korner and publisher. Mirrors iOS
     * `fetchUpcommingNooks()` end-to-end (the 3 parallel `async let`
     * joins are implemented inside [NookFirestoreApi.joinUpcoming]).
     *
     * The live roster of each nook's room is fetched from the
     * `getSessionsInfo` Cloud Function so the card can show the real
     * "X spots left" count — iOS does the same in parallel with the
     * user / community / session-info joins
     * (`NooksTabViewModel.fetchUpcommingNooks()`, lines 218-224).
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
                val sessionsById = sessionInfoApi.fetchSessionsInfo(
                    joined.mapNotNull { it.nook.sessionId },
                )
                val withSessionInfo = joined.map { item ->
                    val info = item.nook.sessionId?.let { sessionsById[it] }
                    item.copy(
                        nook = item.nook.copy(sessionInfo = info),
                        sessionInfo = info,
                    )
                }
                emit(
                    Result.success(
                        NooksUpcomingPage(
                            items = withSessionInfo,
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

private const val TAG = "VideoCallNooks"
