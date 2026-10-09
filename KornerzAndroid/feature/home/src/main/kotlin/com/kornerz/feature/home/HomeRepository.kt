package com.kornerz.feature.home

import com.kornerz.core.model.Community
import com.kornerz.core.model.Loop
import com.kornerz.core.model.Nook
import com.kornerz.core.network.community.CommunitiesRepository
import com.kornerz.core.network.home.HomeFirestoreApi
import com.kornerz.core.network.loop.LoopRepository
import com.kornerz.core.network.user.UserSnippet
import com.kornerz.core.network.user.UsersRepository
import com.kornerz.core.storage.session.SessionRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeRepository @Inject constructor(
    private val homeApi: HomeFirestoreApi,
    private val loopRepository: LoopRepository,
    private val communitiesRepository: CommunitiesRepository,
    private val usersRepository: UsersRepository,
    @Suppress("unused") private val sessionRepository: SessionRepository,
) {

    suspend fun fetchLoopOfDay(): Result<Loop> = loopRepository.fetchLatestLoop()

    suspend fun fetchUpcomingNooks(communityIds: List<String>): Result<List<Nook>> =
        homeApi.fetchUpcomingNooks(communityIds)

    suspend fun fetchRecentLoops(userId: String): Result<List<Loop>> =
        loopRepository.fetchRecentLoops()

    suspend fun fetchCommunityNamesByIds(communityIds: List<String>): Map<String, String> =
        communitiesRepository.fetchCommunityNamesByIds(communityIds)

    suspend fun fetchUsersByCodes(codes: List<String>): Map<String, UserSnippet> =
        usersRepository.fetchUsersByCodes(codes)

    suspend fun fetchYourKornerzCommunities(
        fellows: List<com.kornerz.core.model.FollowMember>,
    ): Result<List<Community>> = runCatching {
        communitiesRepository.fetchFollowingCommunities(fellows)
    }
}
