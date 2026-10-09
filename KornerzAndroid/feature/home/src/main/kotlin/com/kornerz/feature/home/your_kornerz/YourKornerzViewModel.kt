package com.kornerz.feature.home.your_kornerz

import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.Community
import com.kornerz.core.model.FollowMember
import com.kornerz.core.storage.session.SessionRepository
import com.kornerz.core.storage.session.SessionState
import com.kornerz.core.network.fellow.FellowRepository
import com.kornerz.feature.home.HomeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@dagger.hilt.android.scopes.ViewModelScoped
class YourKornerzViewModel @Inject constructor(
    private val homeRepository: HomeRepository,
    private val fellowRepository: FellowRepository,
    private val sessionRepository: SessionRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<AsyncValue<List<Community>>>(AsyncValue.Loading)
    val state: StateFlow<AsyncValue<List<Community>>> = _state.asStateFlow()

    fun fetchData() {
        scope.launch {
            _state.value = AsyncValue.Loading
            runCatching {
                val session = sessionRepository.data.first()
                if (session !is SessionState.Authenticated) return@runCatching emptyList()
                val userCode = session.user.userCode?.takeIf { it.isNotBlank() } ?: return@runCatching emptyList()
                val fellows = fellowRepository.userFelllowsList(userCode).getOrThrow()
                fetchCommunitiesInBatches(fellows)
            }.onSuccess { _state.value = AsyncValue.Success(it) }
                .onFailure { _state.value = AsyncValue.Error(it) }
        }
    }

    private suspend fun fetchCommunitiesInBatches(fellows: List<FollowMember>): List<Community> {
        if (fellows.isEmpty()) return emptyList()
        val collected = mutableListOf<Community>()
        var index = 0
        while (index < fellows.size) {
            val batch = fellows.subList(index, minOf(index + BATCH_SIZE, fellows.size))
            val batchResult = homeRepository.fetchYourKornerzCommunities(batch).getOrElse { return collected }
            collected += batchResult
            index += BATCH_SIZE
        }
        return collected
    }

    private companion object {
        const val BATCH_SIZE = 30
    }
}
