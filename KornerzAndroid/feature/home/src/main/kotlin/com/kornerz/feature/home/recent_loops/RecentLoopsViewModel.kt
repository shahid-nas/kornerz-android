package com.kornerz.feature.home.recent_loops

import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.Loop
import com.kornerz.core.network.user.UserSnippet
import com.kornerz.core.storage.session.SessionRepository
import com.kornerz.feature.home.HomeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@dagger.hilt.android.scopes.ViewModelScoped
class RecentLoopsViewModel @Inject constructor(
    private val homeRepository: HomeRepository,
    @Suppress("unused") private val sessionRepository: SessionRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<AsyncValue<List<Loop>>>(AsyncValue.Loading)
    val state: StateFlow<AsyncValue<List<Loop>>> = _state.asStateFlow()

    fun fetchData() {
        scope.launch {
            try {
                _state.value = AsyncValue.Loading
                sessionRepository.data.first()
                val raw = homeRepository.fetchRecentLoops(userId = "").getOrThrow()
                val recentLoops = raw.drop(1)
                val userCodes = recentLoops.mapNotNull { it.publisherId?.takeIf { id -> id.isNotBlank() } }
                val communityIds = recentLoops.mapNotNull { it.communityId?.takeIf { id -> id.isNotBlank() } }
                val (usersByCode, communityNames) = joinEnrichment(userCodes, communityIds)
                val enriched = recentLoops.map { loop ->
                    loop.copy(
                        publisherName = usersByCode[loop.publisherId]?.name,
                        publisherAvatarUrl = usersByCode[loop.publisherId]?.thumbnailUrl,
                        communityName = loop.communityId?.let { communityNames[it] },
                    )
                }
                _state.value = AsyncValue.Success(enriched)
            } catch (t: Throwable) {
                _state.value = AsyncValue.Error(t)
            }
        }
    }

    private suspend fun joinEnrichment(
        userCodes: List<String>,
        communityIds: List<String>,
    ): Pair<Map<String, UserSnippet>, Map<String, String>> = coroutineScope {
        if (userCodes.isEmpty() && communityIds.isEmpty()) {
            emptyMap<String, UserSnippet>() to emptyMap()
        } else {
            val usersJob = async { homeRepository.fetchUsersByCodes(userCodes) }
            val communitiesJob = async { homeRepository.fetchCommunityNamesByIds(communityIds) }
            awaitAll(usersJob, communitiesJob)
            usersJob.await() to communitiesJob.await()
        }
    }
}
