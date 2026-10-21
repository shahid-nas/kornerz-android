package com.kornerz.feature.community.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
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
import com.kornerz.core.ui.theme.Satoshi
import com.kornerz.feature.community.admin.components.CommunityItemRow

/**
 * The admin-owned-communities list screen (P7, task 7.3 + parity fix
 * that mirrors iOS `AdminCommunityListScreen.swift` lines 10–70).
 *
 * Layout (top → bottom):
 *  1. `TopAppBar` — Satoshi Bold 18-pt title "My Communities" in
 *     `SlateLight` (`C353446`) + back arrow.
 *  2. A search bar styled like iOS `searchView` lines 54–69 — Satoshi
 *     Bold 18-pt text field + 21-pt magnifier icon inside a 30-radius
 *     rounded pill filled with `SmokeLight` (`CF2F4F6`).
 *  3. A plain `LazyColumn` of [CommunityItemRow]s (no card chrome on
 *     the rows — iOS `CommunityItemView` mirrors this), wrapped in a
 *     Material 3 `PullToRefreshBox` for refresh. Pull-to-refresh
 *     re-invokes `AdminCommunitiesListViewModel.onEvent(Refresh)`.
 *
 * Four render states mirror iOS:
 *  - shimmer (iOS `SVProgressHUD` → Android `KornerzLoadingView`)
 *  - error with retry (`KornerzErrorState`)
 *  - empty (iOS `Text("no_communities_available")` → Android
 *    `KornerzEmptyState`)
 *  - loaded list
 *
 * The search bar filters the loaded list client-side (case-insensitive
 * contains on `Community.name`) — mirrors iOS
 * `AdminCommunityListViewModel.filteredCommunities` on lines 20–26.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminCommunitiesListScreen(
    onBack: () -> Unit,
    onShowError: (String) -> Unit,
    onOpenCommunityDetail: (String) -> Unit,
    viewModel: AdminCommunitiesListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.navEvents.collect { event ->
            when (event) {
                is AdminCommunitiesListNavEvent.OpenCommunityDetail ->
                    onOpenCommunityDetail(event.code)
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
                        fontFamily = Satoshi,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFF353446),
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
    var filter by rememberSaveable { mutableStateOf("") }
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
    ) {
        when (val items = state.items) {
            AsyncValue.Loading -> KornerzLoadingView(
                modifier = Modifier.fillMaxSize(),
            )
            is AsyncValue.Error -> KornerzErrorState(
                message = items.throwable.message ?: "Failed to load communities",
                onRetry = onRetry,
                modifier = Modifier.fillMaxSize(),
            )
            is AsyncValue.Success ->
                if (items.data.isEmpty()) {
                    KornerzEmptyState(
                        title = "No communities available",
                        icon = Icons.Filled.Inbox,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        SearchBar(
                            filter = filter,
                            onFilterChange = { filter = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                        CommunitiesList(
                            communities = items.data,
                            filter = filter,
                            onCommunityClick = onCommunityClick,
                        )
                    }
                }
        }
    }
}

@Composable
private fun CommunitiesList(
    communities: List<Community>,
    filter: String,
    onCommunityClick: (String) -> Unit,
) {
    val filtered = if (filter.isBlank()) {
        communities
    } else {
        communities.filter { it.name.contains(filter, ignoreCase = true) }
    }
    LazyColumn(
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        items(filtered, key = { it.code }) { community ->
            CommunityItemRow(
                community = community,
                onClick = { onCommunityClick(community.code) },
            )
        }
    }
}

/**
 * iOS `AdminCommunityListScreen.searchView` (lines 54–69): a 30-radius
 * rounded pill on `SmokeLight` (`CF2F4F6`) containing a Satoshi Bold
 * 18-pt text field and a 21-pt magnifier icon. Implemented with a
 * [BasicTextField] so the interior has zero chrome and matches the
 * iOS look (no underline, no label, no default Material text-field
 * outline).
 */
@Composable
private fun SearchBar(
    filter: String,
    onFilterChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textStyle = LocalTextStyle.current.merge(
        TextStyle(
            fontFamily = Satoshi,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = Color(0xFF1B1A23),
        ),
    )
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(Color(0xFFF2F4F6))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = filter,
            onValueChange = onFilterChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 28.dp),
            textStyle = textStyle,
            singleLine = true,
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (filter.isEmpty()) {
                        Text(
                            text = "Search here",
                            style = textStyle.copy(color = Color(0xFFAEAEC0)),
                        )
                    }
                    innerTextField()
                }
            },
        )
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = "Search",
            tint = Color(0xFF1B1A23),
            modifier = Modifier
                .height(24.dp)
                .align(Alignment.CenterEnd),
        )
    }
}
