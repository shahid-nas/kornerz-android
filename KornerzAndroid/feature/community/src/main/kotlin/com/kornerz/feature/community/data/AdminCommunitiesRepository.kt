package com.kornerz.feature.community.data

import com.kornerz.core.model.Community
import com.kornerz.core.network.community.CommunitiesRepository as CoreCommunitiesRepository
import com.kornerz.feature.community.data.network.CommunitiesFirestoreApi
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The admin-owned-communities read side. Wraps
 * [CommunitiesFirestoreApi] and exposes the count the Profile
 * "Communities" pill renders (P7, task 4.3 + task 8.1, revisited in
 * the P8 "Community row not visible on Android" fix).
 *
 * The "is a community admin of N communities" semantics mirror iOS
 * `AdminCommunitiesRepository.fetchUserCommunitiesCount` (lines
 * 140–159) — count rows in the `CommunityAdm` collection where the
 * current user is the admin, using Firestore's server-side
 * aggregation (`count().get(AggregateSource.SERVER)`) so only the
 * integer is transferred.
 *
 * The count itself is owned by the **data layer**
 * ([CoreCommunitiesRepository]) because the Profile tab renders it and
 * a feature module may not depend on another feature module
 * (`docs/architecture.md` §2.5). This repository delegates to it, so
 * the Profile tab and the Community feature read the same
 * `@Singleton` instance and can never disagree about the count.
 */
@Singleton
class AdminCommunitiesRepository @Inject constructor(
    private val communitiesApi: CommunitiesFirestoreApi,
    private val coreCommunitiesRepository: CoreCommunitiesRepository,
) {

    /**
     * A live count of the communities the user is an admin of. The
     * flow re-emits every time [refreshAdminCount] is called (e.g.
     * after a successful wizard submit).
     */
    fun fetchUserCommunitiesCount(userCode: String): Flow<Int> =
        coreCommunitiesRepository.fetchUserCommunitiesCount(userCode)

    /**
     * Re-reads the admin count and emits the new value. Never throws —
     * a failed read is reported through the returned [Result].
     */
    suspend fun refreshAdminCount(userCode: String): Result<Int> =
        coreCommunitiesRepository.refreshAdminCount(userCode)

    /**
     * Returns the list of communities [userCode] administers, fetched
     * with the same two-step join iOS uses for the admin-owned list.
     *
     *  1. `CommunityAdm.whereField("userId", == userCode).limit(100)`
     *     → list of `communityId`s the user is admin of (mirrors iOS
     *     `AdminCommunitiesRepository.fetchCommunityAdminList` lines
     *     76–106).
     *  2. `Communities.whereField("communityId", in: ids).
     *        whereField("communityApproved", == true)` →
     *     the actual `Community` docs (mirrors iOS
     *     `AdminCommunitiesRepository.fetchCommunities(withIds:)`
     *     lines 108–138). The mapper inside [CommunitiesFirestoreApi]
     *     is tolerant of both the iOS-written (old) schema and the
     *     Android-written (new) schema, so docs written by either
     *     platform parse.
     *
     * Used by `AdminCommunitiesListViewModel.load()`; the screen then
     * calls `CommunitiesRepository.fetchCount(code, kind)` per row for
     * the member/nook badges.
     */
    suspend fun fetchOwnedByUser(userCode: String): Result<List<Community>> =
        runCatching {
            if (userCode.isBlank()) return@runCatching emptyList()
            val communityIds = communitiesApi
                .fetchCommunityAdmIdsByUser(userCode)
                .getOrThrow()
            if (communityIds.isEmpty()) return@runCatching emptyList()
            communitiesApi.fetchByCommunityIds(communityIds).getOrThrow()
        }
}
