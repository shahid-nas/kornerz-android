package com.kornerz.feature.community.data

import com.kornerz.core.model.Community
import com.kornerz.feature.community.data.network.CommunitiesFirestoreApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 */
@Singleton
class AdminCommunitiesRepository @Inject constructor(
    private val communitiesApi: CommunitiesFirestoreApi,
) {

    private val adminCount = MutableStateFlow(0)

    /**
     * A live count of the communities the user is an admin of. The
     * flow re-emits every time [refreshAdminCount] is called (e.g.
     * after a successful wizard submit).
     */
    fun fetchUserCommunitiesCount(userCode: String): Flow<Int> {
        // Consumers should call [refreshAdminCount] explicitly to
        // seed the value before subscribing. The flow re-emits on
        // every successful [refreshAdminCount] call.
        return adminCount.asStateFlow()
    }

    /**
     * Re-reads the admin count and emits the new value. Mirrors iOS
     * `AdminCommunitiesRepository.fetchUserCommunitiesCount` (line
     * 140) — queries `CommunityAdm.whereEqualTo("userId", userId)`
     * via `count().get(AggregateSource.SERVER)`.
     */
    suspend fun refreshAdminCount(userCode: String) {
        if (userCode.isBlank()) {
            adminCount.value = 0
            return
        }
        adminCount.value = communitiesApi
            .fetchCommunityAdmCountByUser(userCode)
            .getOrDefault(0)
    }

    /**
     * Returns the raw list of communities owned by [userCode] (no
     * per-row count enrichment). Used by the admin list VM which
     * wants the plain list and then loads per-row counts lazily.
     */
    suspend fun fetchOwnedByUser(userCode: String): Result<List<Community>> =
        communitiesApi.fetchOwnedByUser(userCode)
}
