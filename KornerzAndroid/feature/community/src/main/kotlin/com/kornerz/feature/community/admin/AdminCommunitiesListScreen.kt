package com.kornerz.feature.community.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.Community
import com.kornerz.core.ui.components.KornerzEmptyState
import com.kornerz.core.ui.components.KornerzErrorState
import com.kornerz.core.ui.components.KornerzLoadingView
import com.kornerz.feature.community.admin.components.CommunityItemRow

/**
 * The admin-owned-communities list screen (P7, task 7.3). Renders a
 * `LazyColumn` of [CommunityItemRow]s with four render states (shimmer
 * / error / empty / loaded) and pull-to-refresh. Mirrors iOS
 * `AdminCommunityListScreen.swift`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminCommunitiesListScreen(
    onBack: () -> Unit,
    onShowError: (String) -> Unit,
    viewModel: AdminCommunitiesListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.navEvents.collect { event ->
            when (event) {
                is AdminCommunitiesListNavEvent.OpenCommunityDetail -> {
                    // P7 placeholder — community-detail-as-admin is
                    // documented as removed-dead-code; surface a
                    // "Coming soon" toast via the shell's snackbar
                    // (see `admin-communities-list` spec).
                    onShowError("Coming soon")
                }
                AdminCommunitiesListNavEvent.Back -> onBack()
                is AdminCommunitiesListNavEvent.ShowError -> onShowError(event.message)
            }
        }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "My Communities",
                        color = Color(0xFF353446),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.onBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF353446),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        AdminCommunitiesListContent(
            state = state,
            onCommunityClick = viewModel::onCommunityClicked,
            onRefresh = { viewModel.onEvent(AdminCommunitiesListEvent.Refresh) },
            onRetry = { viewModel.onEvent(AdminCommunitiesListEvent.Retry) },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminCommunitiesListContent(
    state: AdminCommunitiesListState,
    onCommunityClick: (String) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
    ) {
        when (val items = state.items) {
            AsyncValue.Loading -> {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 16.dp, horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(3) {
                        ShimmerRowPlaceholder()
                    }
                }
            }
            is AsyncValue.Error -> KornerzErrorState(
                message = items.throwable.message ?: "Failed to load communities",
                onRetry = onRetry,
                modifier = Modifier.fillMaxSize(),
            )
            is AsyncValue.Success -> {
                if (items.data.isEmpty()) {
                    KornerzEmptyState(
                        title = "You have not created any community yet",
                        icon = Icons.Filled.Inbox,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 16.dp, horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(items.data, key = { it.code }) { community ->
                            val counts = state.rowCounts[community.code] ?: (0 to 0)
                            CommunityItemRow(
                                community = community,
                                memberCount = counts.first,
                                nookCount = counts.second,
                                onClick = { onCommunityClick(community.code) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShimmerRowPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(73.dp)
            .padding(0.dp),
    ) {
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .height(73.dp),
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun AdminCommunitiesListContentPreview() {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("AdminCommunitiesListContent Preview")
    }
}
