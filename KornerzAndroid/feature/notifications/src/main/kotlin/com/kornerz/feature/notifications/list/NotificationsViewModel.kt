package com.kornerz.feature.notifications.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kornerz.core.model.UserNotification
import com.kornerz.core.model.deeplink
import com.kornerz.core.network.community.CommunitiesRepository
import com.kornerz.core.storage.session.SessionRepository
import com.kornerz.core.storage.session.SessionState
import com.kornerz.feature.notifications.data.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val sessionRepository: SessionRepository,
    private val communitiesRepository: CommunitiesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationsState())
    val state: StateFlow<NotificationsState> = _state.asStateFlow()

    private val _navEvents = Channel<NotificationsNavEvent>(Channel.BUFFERED)
    val navEvents: Flow<NotificationsNavEvent> = _navEvents.receiveAsFlow()

    init {
        loadFirstPage()
    }

    fun loadFirstPage() {
        viewModelScope.launch {
            _state.update { it.copy(items = com.kornerz.core.model.AsyncValue.Loading) }
            val userCode = sessionUserCode() ?: return@launch
            notificationRepository.reset()
            val result = notificationRepository.fetchNextPage(userCode)
            result.fold(
                onSuccess = { (page, hasMore) ->
                    val rows = page.map { it.toRow() }
                    _state.update {
                        it.copy(
                            items = com.kornerz.core.model.AsyncValue.Success(rows),
                            hasMoreData = hasMore,
                        )
                    }
                },
                onFailure = { t ->
                    _state.update {
                        it.copy(items = com.kornerz.core.model.AsyncValue.Error(t))
                    }
                },
            )
        }
    }

    fun loadMore() {
        val current = _state.value
        if (current.isLoadingMore || !current.hasMoreData) return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingMore = true) }
            val userCode = sessionUserCode() ?: return@launch
            val result = notificationRepository.fetchNextPage(userCode)
            result.fold(
                onSuccess = { (page, hasMore) ->
                    val rows = page.map { it.toRow() }
                    val existing = (current.items as? com.kornerz.core.model.AsyncValue.Success)?.data.orEmpty()
                    _state.update {
                        it.copy(
                            items = com.kornerz.core.model.AsyncValue.Success(existing + rows),
                            isLoadingMore = false,
                            hasMoreData = hasMore,
                        )
                    }
                },
                onFailure = {
                    _state.update { it.copy(isLoadingMore = false) }
                },
            )
        }
    }

    fun onEvent(event: NotificationsEvent) {
        when (event) {
            NotificationsEvent.Refresh -> loadFirstPage()
            is NotificationsEvent.NotificationOpened -> openNotification(event.notification)
        }
    }

    private fun openNotification(notification: UserNotification) {
        viewModelScope.launch {
            when (notification.deeplinkType) {
                com.kornerz.core.model.UserNotificationDeeplinkType.NEW_LOOP,
                com.kornerz.core.model.UserNotificationDeeplinkType.LOOP_COMMENT -> {
                    val loopId = notification.reference
                    if (loopId != null) _navEvents.trySend(NotificationsNavEvent.OpenLoopComment(loopId))
                }
                com.kornerz.core.model.UserNotificationDeeplinkType.NEW_COMMENT -> {
                    val forumId = notification.reference
                    if (forumId != null) _navEvents.trySend(NotificationsNavEvent.OpenForumComment(forumId))
                }
                else -> {
                    // Mirrors iOS `NotificationsViewModel.fetchCommunityInfo`
                    // (lines 153–159) — try `communityId` first, fall back
                    // to fetching the community by name (which is the
                    // value carried in `notification.content`).
                    val community = notification.communityId
                        ?.let { communitiesRepository.fetchById(it) }
                        ?: notification.content
                            ?.takeIf { it.isNotBlank() }
                            ?.let { communitiesRepository.fetchByName(it) }
                    val deeplink = notification.deeplink(community)
                    if (deeplink != null) {
                        _navEvents.trySend(NotificationsNavEvent.Open(deeplink))
                    } else {
                        _navEvents.trySend(NotificationsNavEvent.ShowAlert("Community not found"))
                    }
                }
            }
        }
    }

    private fun UserNotification.toRow(): UserNotificationRow =
        UserNotificationRow(notification = this)

    /**
     * The signed-in user's code, or `null` when there is no session.
     *
     * Read straight from [SessionRepository] rather than through the
     * Profile feature's repository: the notifications module must not
     * depend on another feature module (`docs/architecture.md` §2.5),
     * and the session state is `core`'s to own.
     */
    private suspend fun sessionUserCode(): String? {
        val state = sessionRepository.data.first()
        val user = (state as? SessionState.Authenticated)?.user ?: return null
        return user.id.takeIf { it.isNotEmpty() }
    }
}
