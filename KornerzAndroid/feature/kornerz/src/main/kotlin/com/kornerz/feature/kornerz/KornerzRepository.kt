package com.kornerz.feature.kornerz

import android.util.Log
import com.kornerz.core.model.Korner
import com.kornerz.core.model.KornerDetail
import com.kornerz.core.model.Loop
import com.kornerz.core.model.User
import com.kornerz.core.network.fellow.FellowRepository
import com.kornerz.core.network.kornerz.KornerFirestoreApi
import com.kornerz.core.network.loops.LoopCursor
import com.kornerz.core.network.loops.LoopFirestoreApi
import com.kornerz.core.network.loops.PagingResult
import com.kornerz.core.network.nooks.SessionInfoApi
import com.kornerz.core.network.user.UsersRepository
import com.kornerz.core.storage.session.SessionRepository
import com.kornerz.core.storage.session.SessionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for the Kornerz tab. Mirrors the iOS `KornerzTabViewModel`
 * which composes three sub-carousels — each backed by an independent
 * query on `Communities`:
 *  - "Your Kornerz" — `fetchFollowingCommunities()` against the
 *    signed-in user's `Fellow` records.
 *  - "Recommended for you" — `fetchAllCommunities(after:orderby:.name)`
     *    ordered by `communityName` descending.
 *  - "New this week" — `fetchRecentCommunities()` ordered by
 *    `viewsCount` descending.
 *
 * Each method returns a one-shot `Flow<Result<T>>` (architecture §2.3)
 * that the calling section ViewModel maps into an `AsyncValue<T>`.
 */
@Singleton
class KornerzRepository @Inject constructor(
    private val api: KornerFirestoreApi,
    private val loopApi: LoopFirestoreApi,
    private val fellowRepository: FellowRepository,
    private val usersRepository: UsersRepository,
    private val sessionRepository: SessionRepository,
    private val sessionInfoApi: SessionInfoApi,
) {

    /**
     * Streams the "Recommended for you" list — every approved Korner
     * ordered by name descending, limit 30. Mirrors iOS
     * `CommunitiesRepository.fetchCommunities(after:orderby:.name)`.
     */
    fun fetchRecommended(): Flow<Result<List<Korner>>> = flow {
        emit(api.fetchAllKornerz())
    }

    /**
     * Streams the "New this week" list — every approved Korner ordered
     * by `viewsCount` descending, limit 30. Mirrors iOS
     * `CommunitiesRepository.fetchCommunities(after:)` (default
     * `orderby: .viewsCount, descending: true`).
     */
    fun fetchNewThisWeek(): Flow<Result<List<Korner>>> = flow {
        emit(api.fetchRecentKornerz())
    }

    /**
     * Streams the "Your Kornerz" list — every approved Korner whose
     * `communityName` matches one of the followed names in the
     * signed-in user's `Fellow` records. Mirrors iOS
     * `KornerzCarouselViewModel.fetchFollowingCommunities()`.
     */
    fun fetchYourKornerz(): Flow<Result<List<Korner>>> = flow {
        val session = sessionRepository.data.first()
        if (session !is SessionState.Authenticated) {
            emit(Result.success(emptyList()))
            return@flow
        }
        val userCode = session.user.userCode?.takeIf { it.isNotBlank() }
        if (userCode == null) {
            emit(Result.success(emptyList()))
            return@flow
        }
        val fellows = fellowRepository.userFelllowsList(userCode).getOrElse { emptyList() }
        val names = fellows.mapNotNull { it.communityFollowed?.takeIf(String::isNotBlank) }
        emit(api.fetchFollowingKornerzByNames(names))
    }

    /**
     * Streams the aggregate detail for a single Korner. Same as the
     * detail screen's read — single source of truth for the korner
     * document, nooks, and members.
     *
     * [kornerId] may be the Firestore document id **or** the
     * community's public `communityId` field — the Home tab's "Your
     * Kornerz" carousel carries the latter, the Kornerz tab the former.
     * The API resolves both.
     *
     * The live roster of every session on the screen is resolved from
     * the `getSessionsInfo` Cloud Function — one batched call for the
     * Korner's own session plus every nook's session, exactly as iOS
     * does (`KornerDetailViewModel.sessionIds` + `fetchSessionsInfo`).
     */
    fun fetchKornerDetail(kornerId: String): Flow<Result<KornerDetail>> = flow {
        val userCode = sessionRepository.data.first()
            .let { it as? SessionState.Authenticated }
            ?.user?.userCode
            ?.takeIf { it.isNotBlank() }
        val result = api.fetchKornerDetail(kornerId, userCode)
        val detail = result.getOrElse { error ->
            emit(Result.failure(error))
            return@flow
        }
        emit(Result.success(detail.withSessionInfo()))
    }

/**
     * Attaches the live-session roster to the Korner and to every nook.
     *
     * iOS resolves the rosters in a single batched `getSessionsInfo`
     * call addressed by `sessionIds` — the Korner's own `sessionID`
     * plus every upcoming nook's `sessionID`
     * (`KornerDetailViewModel.sessionIds`, lines 57-63). The "Current"
     * card then reads `sessionsInfo?[korner.sessionID]` and each
     * upcoming card reads `sessionsInfo?[nook.sessionID]`.
     *
     * **The "Current" card reads the room it will actually join.**
     * iOS resolves the Current card's session from the *community* room
     * (`korner.sessionID` → `communitySessionId`), which is a different
     * room from a live nook's own `sessionId` — and the community room
     * only exists while a community call is running. Reading it alone
     * left the card permanently empty, with no attendee line and no
     * "spots left" counter, even when a nook in the same Korner had a
     * live room with people in it.
     *
     * So the Current card resolves the **live nook's** session first and
     * falls back to the community room — the same precedence the Nooks
     * tab applies to its live card, and the same room the screen's
     * "Join" capsule opens (`KornerDetailViewModel.joinCall`).
     *
     * A failed lookup leaves every roster `null`; the cards fall back
     * to the "no one is attending" copy, the way iOS does when
     * `sessionsInfo` is nil.
     */
    private suspend fun KornerDetail.withSessionInfo(): KornerDetail {
        val currentSessionId = currentSessionId()
        val sessionIds = buildList {
            currentSessionId?.let(::add)
            korner.sessionId?.takeIf { it.isNotBlank() }?.let(::add)
            nooks.mapNotNull { it.sessionId }.forEach(::add)
        }.distinct()
        if (sessionIds.isEmpty()) return this

        val sessionsById = sessionInfoApi.fetchSessionsInfo(sessionIds)
        Log.i(
            TAG,
            "Korner detail sessions: requested=${sessionIds.size} " +
                "current='${currentSessionId}' korner='${korner.sessionId}' " +
                "resolved=${sessionsById.size} " +
                "currentConnections=${currentSessionId?.let { sessionsById[it]?.connections }}",
        )
        return copy(
            korner = korner.copy(
                sessionInfo = currentSessionId?.let { sessionsById[it] },
            ),
            nooks = nooks.map { nook ->
                nook.copy(sessionInfo = nook.sessionId?.let { sessionsById[it] })
            },
        )
    }

    /**
     * The session the "Current" card shows: the live nook's own room,
     * falling back to the Korner's community room.
     *
     * **Liveness is decided by the start date, not by `eventType`.** No
     * Event in the production Firestore carries `eventType == "live"` —
     * the types in use are `open`, `closed`, `payed` and `free` — so the
     * `Nook.isLive` flag (which tests `eventType == "live"`) is false for
     * every community event. The Nooks tab already treats a **started**
     * event as live for exactly this reason
     * (`NookFirestoreApi.fetchLiveEvents`: `eventDateInit <= now`), and
     * that is the same test used here.
     *
     * A nook with no session id has no room to show, so it is skipped.
     */
    private fun KornerDetail.currentSessionId(): String? {
        val now = Clock.System.now()
        val liveNook = nooks.firstOrNull { nook ->
            !nook.sessionId.isNullOrBlank() &&
                nook.eventDateInit?.let { it <= now } == true
        }
        return liveNook?.sessionId?.takeIf { it.isNotBlank() }
            ?: korner.sessionId?.takeIf { it.isNotBlank() }
    }

    /**
     * Streams the Korner's admins — the "Kornerstones" tab of the Korner
     * detail screen. Mirrors iOS `KornerDetailViewModel.fetchLeaders`,
     * which reads the `CommunityAdm` collection.
     */
    fun fetchKornerAdmins(kornerId: String): Flow<Result<List<User>>> = flow {
        emit(api.fetchCommunityAdmins(kornerId))
    }

    /**
     * The `codeUser` of every one of the Korner's admins — the full
     * "Kornerstones" roster, before the carousel's 30-user cut.
     *
     * Mirrors iOS `KornerDetailViewModel.fetchLeaders()`
     * (`kornerz/Kornerz/SwiftUI/Dashboard/Korner/KornerDetail/KornerDetailViewModel.swift:175`),
     * which keeps the whole de-duplicated, sorted id list in
     * `leadersIds` so the "View All" sheet can paginate through it.
     */
    suspend fun fetchKornerAdminCodes(kornerId: String): Result<List<String>> =
        api.fetchCommunityAdminCodes(kornerId)

    /**
     * The `codeUser` of every follower of the Korner — the full
     * "Klouders" roster, before the carousel's 30-user cut.
     *
     * Mirrors iOS `KornerDetailViewModel.fetchFollowers()`
     * (`KornerDetailViewModel.swift:149`), which keeps the whole
     * de-duplicated, sorted id list in `followersIds`.
     */
    suspend fun fetchKornerFollowerCodes(kornerId: String): Result<List<String>> =
        api.fetchCommunityFollowerCodes(kornerId)

    /**
     * Resolves a page of user codes into the matching `Users`
     * documents.
     *
     * The "View All" sheet pages through the member roster with this
     * call — one page per 30 codes, matching iOS
     * `KornerUsersListViewModel.loadNextPage()`, which slices the id
     * list by `pageSize` and hands the chunk to
     * `UsersRepository.fetchUsers(in:)`.
     *
     * The returned users keep the order of [codes], so the sheet's
     * rows appear in the same order as the roster.
     */
    suspend fun fetchUsersByCodes(codes: List<String>): Result<List<User>> {
        if (codes.isEmpty()) return Result.success(emptyList())
        val byCode = usersRepository.fetchUsersByCodes(codes).getOrDefault(emptyMap())
        return Result.success(codes.mapNotNull { byCode[it] })
    }

    /**
     * Streams the Korner's latest loops — the "Recently in &lt;community&gt;"
     * section of the Korner detail screen. Mirrors iOS
     * `KornerDetailViewModel.fetchLoops`.
     *
     * The loops carry the community's **public** `communityId` field, so
     * that is the key the read is addressed by; the document id is the
     * fallback for a community that does not carry the field.
     */
    fun fetchKornerRecentLoops(korner: Korner): Flow<Result<List<Loop>>> = flow {
        val communityId = korner.communityId?.takeIf { it.isNotBlank() } ?: korner.id
        emit(loopApi.fetchCommunityLoops(communityId))
    }

    /**
     * Fetches one page of the Korner's "Recently in" loops, resuming
     * after [cursor] when one is supplied.
     */
    suspend fun fetchKornerRecentLoopsPage(
        korner: Korner,
        cursor: LoopCursor?,
    ): Result<PagingResult<Loop>> {
        val communityId = korner.communityId?.takeIf { it.isNotBlank() } ?: korner.id
        return loopApi.fetchCommunityLoopsPage(communityId, cursor)
    }

    /**
     * Follows the Korner for the signed-in user.
     *
     * Mirrors iOS `KornerDetailViewModel.followKorner()`
     * (`kornerz/Kornerz/SwiftUI/Dashboard/Korner/KornerDetail/KornerDetailViewModel.swift:340`),
     * which calls `FellowRepository.followCommunity(userId:community:)`.
     *
     * Fails when no user is signed in — iOS guards the same way
     * (`guard let userId = AppHelper.shared.loggedInUser?.userCode`).
     */
    suspend fun followKorner(kornerId: String): Result<Unit> {
        val userCode = signedInUserCode()
            ?: return Result.failure(IllegalStateException("No signed-in user to follow with"))
        return api.followCommunity(kornerId, userCode)
    }

    /**
     * Unfollows the Korner for the signed-in user.
     *
     * Mirrors iOS `KornerDetailViewModel.unfollowKorner()`
     * (`KornerDetailViewModel.swift:352`), which calls
     * `FellowRepository.unfollowCommunity(userId:community:)`.
     */
    suspend fun unfollowKorner(kornerId: String): Result<Unit> {
        val userCode = signedInUserCode()
            ?: return Result.failure(IllegalStateException("No signed-in user to unfollow with"))
        return api.unfollowCommunity(kornerId, userCode)
    }

    /**
     * The signed-in user's `codeUser` — the identifier the `Fellow`
     * records are keyed by (iOS `AppHelper.shared.loggedInUser?.userCode`).
     */
    private suspend fun signedInUserCode(): String? = sessionRepository.data.first()
        .let { it as? SessionState.Authenticated }
        ?.user
        ?.userCode
        ?.takeIf { it.isNotBlank() }

    private companion object {
        const val TAG = "KornerDetailSessions"
    }
}
