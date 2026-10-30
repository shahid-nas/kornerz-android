package com.kornerz.feature.notifications.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.ui.components.KornerzEmptyState
import com.kornerz.core.ui.components.KornerzErrorState
import com.kornerz.core.ui.components.KornerzLoadingMoreView
import com.kornerz.core.ui.components.KornerzLoadingView

/**
 * The notifications list screen (P6, screen 8.1 + 8.2). Mirrors iOS
 * `NotificationsController` + `NotificationScreen`.
 *
 * Three render states (per `notifications-list` spec):
 * - shimmer / `KornerzLoadingView` while loading the first page
 * - `KornerzErrorState` with Retry when the first page errors
 * - `KornerzEmptyState` when the page returns an empty list
 *
 * Pagination: a footer `CircularProgressIndicator` renders below the
 * last item while `isLoadingMore` is true. Pull-to-refresh restarts
 * the cursor and re-fetches the first page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onOpenLoopComment: (String) -> Unit,
    onOpenForumComment: (String) -> Unit,
    onOpenDeeplink: (com.kornerz.core.model.DeepLink) -> Unit,
    onShowAlert: (String) -> Unit,
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var isRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(state.items) {
        if (isRefreshing && state.items !is AsyncValue.Loading) {
            isRefreshing = false
        }
    }
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            viewModel.onEvent(NotificationsEvent.Refresh)
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.navEvents.collect { event ->
            when (event) {
                is NotificationsNavEvent.OpenLoopComment -> onOpenLoopComment(event.loopId)
                is NotificationsNavEvent.OpenForumComment -> onOpenForumComment(event.forumId)
                is NotificationsNavEvent.Open -> onOpenDeeplink(event.deeplink)
                is NotificationsNavEvent.ShowAlert -> onShowAlert(event.message)
            }
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        NotificationsContent(
            state = state,
            onNotificationClick = { notification ->
                viewModel.onEvent(NotificationsEvent.NotificationOpened(notification))
            },
            onLoadMore = viewModel::loadMore,
            onRetry = viewModel::loadFirstPage,
            onRefresh = { isRefreshing = true },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsContent(
    state: NotificationsState,
    onNotificationClick: (com.kornerz.core.model.UserNotification) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PullToRefreshBox(
        isRefreshing = state.items is AsyncValue.Loading,
        onRefresh = onRefresh,
        modifier = modifier,
    ) {
        when (val items = state.items) {
            AsyncValue.Loading -> KornerzLoadingView(modifier = Modifier.fillMaxSize())
            is AsyncValue.Error -> KornerzErrorState(
                message = items.throwable.message ?: "Failed to load notifications",
                onRetry = onRetry,
                modifier = Modifier.fillMaxSize(),
            )
            is AsyncValue.Success -> {
                if (items.data.isEmpty()) {
                    KornerzEmptyState(
                        title = "No notifications",
                        icon = Icons.Filled.Inbox,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement
                            .spacedBy(0.dp),
                    ) {
                        items(
                            items = items.data,
                            key = { it.notification.id },
                        ) { row ->
                            NotificationItem(
                                row = row,
                                onClick = { onNotificationClick(row.notification) },
                            )
                            if (row == items.data.last() && state.hasMoreData) {
                                LaunchedEffect(row.notification.id) { onLoadMore() }
                            }
                        }
                        if (state.isLoadingMore) {
                            item("footer-spinner") {
                                KornerzLoadingMoreView(modifier = Modifier.fillMaxWidth())
                            }
                        } else if (state.paginationError != null) {
                            item("footer-error") {
                                KornerzErrorState(
                                    message = state.paginationError.message
                                        ?: "Failed to load notifications",
                                    onRetry = onRetry,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
