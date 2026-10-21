package com.kornerz.feature.community.admin.detail

import com.kornerz.core.model.Community
import com.kornerz.feature.community.data.CommunitiesRepository
import com.kornerz.feature.community.data.network.CommunityMembersFirestoreApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read-side repository for `CommunityDetailAsAdmin`. Loads the
 * [Community] by 6-char code and then the seven stats counts in
 * parallel via
 * [CommunityStatsFirestoreApi.fetchAllInParallel].
 *
 * Mirrors iOS `OwnedCommunityViewModel` (`getCommunityName`,
 * `getCommunityCode`, plus the seven `getCommunity*Count` callbacks)
 * but on Android those reads are merged into a single
 * `Result<CommunityStats>` to avoid the iOS fan-out of independent
 * Swift-style callbacks. The detail screen still updates each stat
 * tile independently from the projection.
 */
@Singleton
class OwnedCommunityStatsRepository @Inject constructor(
    private val communitiesRepository: CommunitiesRepository,
    private val membersApi: CommunityMembersFirestoreApi,
    private val statsApi: CommunityStatsFirestoreApi,
) {

    /**
     * Loads the [Community] and the seven parallel counts in one
     * trip. If the community can't be fetched the whole call
     * short-circuits with a failure — the detail screen renders
     * `KornerzErrorState`.
     */
    suspend fun fetchStats(communityCode: String): Result<CommunityStats> =
        runCatching {
            val community: Community = communitiesRepository.fetchByCode(communityCode)
                ?: error("Community not found")
            val counts = statsApi.fetchAllInParallel(community.code, membersApi)
            CommunityStats(
                community = community,
                members = counts["members"] ?: 0,
                events = counts["events"] ?: 0,
                articles = counts["articles"] ?: 0,
                videos = counts["videos"] ?: 0,
                forums = counts["forums"] ?: 0,
                notifications = counts["notifications"] ?: 0,
                pendingEventRequests = counts["pendingEventRequests"] ?: 0,
            )
        }
}
