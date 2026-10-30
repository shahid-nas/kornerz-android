package com.kornerz.feature.kornerz

import com.kornerz.core.model.Korner
import com.kornerz.core.model.KornerDetail
import com.kornerz.core.model.Loop
import com.kornerz.core.model.User
import com.kornerz.core.network.fellow.FellowRepository
import com.kornerz.core.network.kornerz.KornerFirestoreApi
import com.kornerz.core.network.loops.LoopFirestoreApi
import com.kornerz.core.storage.session.SessionRepository
import com.kornerz.core.storage.session.SessionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
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
    private val sessionRepository: SessionRepository,
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
     */
    fun fetchKornerDetail(kornerId: String): Flow<Result<KornerDetail>> = flow {
        val userCode = sessionRepository.data.first()
            .let { it as? SessionState.Authenticated }
            ?.user?.userCode
            ?.takeIf { it.isNotBlank() }
        emit(api.fetchKornerDetail(kornerId, userCode))
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
}
