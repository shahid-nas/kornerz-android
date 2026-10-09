package com.kornerz.feature.kornerz

import com.kornerz.core.model.Korner
import com.kornerz.core.model.KornerDetail
import com.kornerz.core.network.fellow.FellowRepository
import com.kornerz.core.network.kornerz.KornerFirestoreApi
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
     */
    fun fetchKornerDetail(kornerId: String): Flow<Result<KornerDetail>> = flow {
        emit(api.fetchKornerDetail(kornerId))
    }
}
