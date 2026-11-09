package com.kornerz.feature.community.admin.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.User
import com.kornerz.core.ui.R
import com.kornerz.core.ui.components.KornerzErrorState
import com.kornerz.core.ui.components.KornerzLoadingView
import com.kornerz.core.ui.theme.Satoshi
import com.kornerz.feature.community.admin.detail.components.StatTile

/**
 * Read-only stats dashboard for the admin-owned community detail
 * screen. Layout mirrors the iOS XIB `CommunityDetailAsAdminVC.xib`
 * lines 54-639:
 *
 *  1. Admin section - 92-dp tall row with the current-user's avatar,
 *     name, "Administrator" subtitle, plus two right-aligned icon
 *     buttons (notification bell + more), bottom 1-dp separator.
 *  2. Community section - 101-dp tall row with the community
 *     gradient/logo avatar, the community name in `SlateLight`
 *     (`C353446` / `#45455C`) Satoshi Bold 14-pt, plus a
 *     right-aligned settings gear icon button.
 *  3. Stats grid - 4 rows of 2 stat tiles each, the FIRST tile is
 *     the "requests" pending-event-request count. Every tile has a
 *     `SmokeLight` (`CF2F4F6`) bg, 86-dp height, label + value
 *     stacked leading-aligned, plus a `›` chevron at bottom-end.
 *     Order (matches iOS XIB row order):
 *       Row 1: requests | Forums
 *       Row 2: Notifications | Talking now
 *       Row 3: Members | Events
 *       Row 4: Articles | Videos
 *  4. 1-dp separator, then the "Add:" header in `SlateDark`
 *     (`C75748B` / `#75748B`) Satoshi Bold 18-pt.
 *  5. Add row: a single full-width Forums pill - white bg, 1-pt
 *     `CEC8FA` / `#CEC8FA` border, corner radius = height/2, label
 *     Satoshi Bold 17-pt `PurpleLight` / `#5D47ED` centered. (iOS
 *     uses the lowercase string "forums" for this label.) The iOS
 *     XIB line 504 wraps sibling `Notifications` + `Events` pills in
 *     `<stackView hidden="YES">`; hidden arranged subviews collapse in
 *     a `UIStackView`, so neither pill renders in the running app and
 *     neither is rendered here.
 *
 * Tap targets fire `Coming soon` snackbars for the write-side
 * surfaces (Pending requests, Add Notification / Add Event / Add
 * Forum, top-row notification / more icons, settings gear) since
 * the iOS sub-screens are out of scope for the current Android
 * parity surface.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityDetailScreen(
    onBack: () -> Unit,
    onShowError: (String) -> Unit,
    onOpenCreateForum: (String) -> Unit,
    viewModel: CommunityDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.navEvents.collect { event ->
            when (event) {
                CommunityDetailNavEvent.Back -> onBack()
                is CommunityDetailNavEvent.OpenPendingRequests ->
                    onShowError("Coming soon")
                is CommunityDetailNavEvent.ShowError -> onShowError(event.message)
            }
        }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "My Community",
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
        CommunityDetailContent(
            state = state,
            onRefresh = { viewModel.onEvent(CommunityDetailEvent.Refresh) },
            onRetry = { viewModel.onEvent(CommunityDetailEvent.Retry) },
            onSurfaceTap = onShowError,
            onOpenCreateForum = onOpenCreateForum,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommunityDetailContent(
    state: CommunityDetailState,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onSurfaceTap: (String) -> Unit,
    onOpenCreateForum: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
    ) {
        when (val stats = state.stats) {
            AsyncValue.Loading -> KornerzLoadingView(modifier = Modifier.fillMaxSize())
            is AsyncValue.Error -> KornerzErrorState(
                message = stats.throwable.message ?: "Failed to load community stats",
                onRetry = onRetry,
                modifier = Modifier.fillMaxSize(),
            )
            is AsyncValue.Success -> LoadedBody(
                stats = stats.data,
                adminUser = state.adminUser,
                onSurfaceTap = onSurfaceTap,
                onOpenCreateForum = onOpenCreateForum,
            )
        }
    }
}

@Composable
private fun LoadedBody(
    stats: CommunityStats,
    adminUser: User?,
    onSurfaceTap: (String) -> Unit,
    onOpenCreateForum: (String) -> Unit,
) {
    // The identifier the new loop is written under. iOS passes
    // `community.communityId` into `AddForumViewModel`
    // (`CommunityDetailAsAdminVC.swift:333`), which writes it to
    // `Loop.communityId` (`AddForumViewModel.swift:227`). The 6-char
    // `code` is the fallback for communities that carry no separate
    // `communityId` field, so the wizard always receives a non-empty id.
    val communityId = stats.community.communityId
        ?.takeIf { it.isNotBlank() }
        ?: stats.community.code
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            AdminRow(
                adminUser = adminUser,
                onNotificationTap = { onSurfaceTap("Coming soon") },
                onMoreTap = { onSurfaceTap("Coming soon") },
            )
        }
        item {
            HorizontalDivider()
            CommunityRow(
                stats = stats,
                onSettingsTap = { onSurfaceTap("Coming soon") },
            )
        }
        item { HorizontalDivider() }
        item {
            StatsGrid(
                stats = stats,
                onPendingRequestsTap = { onSurfaceTap("Coming soon") },
            )
        }
        item { HorizontalDivider() }
        item {
            ForAddRow(onAddForumTap = { onOpenCreateForum(communityId) })
        }
    }
}

@Composable
private fun AdminRow(
    adminUser: User?,
    onNotificationTap: () -> Unit,
    onMoreTap: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 23.dp, top = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AdminAvatar(adminUser)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f).padding(top = 4.dp)) {
            Text(
                text = adminUser?.displayName.orEmpty().ifBlank { "" },
                fontFamily = Satoshi,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color(0xFF1B1A23),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Administrator",
                fontFamily = Satoshi,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color(0xFFAEAEC0),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        IconButton(
            onClick = onNotificationTap,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = "Notifications",
                tint = Color(0xFF353446),
            )
        }
        IconButton(
            onClick = onMoreTap,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "More",
                tint = Color(0xFF353446),
            )
        }
    }
}

@Composable
private fun CommunityRow(
    stats: CommunityStats,
    onSettingsTap: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(101.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 23.dp, top = 24.dp, end = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        CommunityAvatar(stats)
        Spacer(Modifier.width(14.dp))
        Text(
            text = stats.community.name,
            fontFamily = Satoshi,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = Color(0xFF45455C),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(top = 16.dp),
        )
        IconButton(
            onClick = onSettingsTap,
            modifier = Modifier
                .padding(top = 4.dp)
                .size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Settings",
                tint = Color(0xFF353446),
            )
        }
    }
}

@Composable
private fun StatsGrid(
    stats: CommunityStats,
    onPendingRequestsTap: () -> Unit,
) {
    val rows = listOf(
        TileRow("requests", stats.pendingEventRequests, onPendingRequestsTap) to
            TileRow("Forums", stats.forums, null),
        TileRow("Notifications", stats.notifications, null) to
            TileRow("Talking now", stats.talkingNow, null),
        TileRow("Members", stats.members, null) to
            TileRow("Events", stats.events, null),
        TileRow("Articles", stats.articles, null) to
            TileRow("Videos", stats.videos, null),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // The grid sits between the section's top and bottom
            // dividers, so it needs breathing room on both sides —
            // without it the tiles touch the divider lines.
            .padding(horizontal = 23.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        rows.forEach { (left, right) ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    StatTile(
                        label = left.label,
                        value = left.value,
                        modifier = if (left.onClick != null)
                            Modifier.fillMaxWidth().clickable(onClick = left.onClick)
                        else Modifier.fillMaxWidth(),
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    StatTile(
                        label = right.label,
                        value = right.value,
                    )
                }
            }
        }
    }
}

@Composable
private fun ForAddRow(
    onAddForumTap: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 23.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Add:",
            fontFamily = Satoshi,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = Color(0xFF75748B),
            modifier = Modifier.padding(top = 24.dp, bottom = 16.dp),
        )
        // iOS XIB line 504 wraps the `Notifications` + `Events` pills in
// `<stackView hidden="YES">`. Hidden arranged subviews collapse in a
// UIStackView, so the running iOS app shows only the single
// full-width `forums` pill below. Mirrored here by not rendering
// either hidden pill.
AddPill(
            label = "Loops",
            onClick = onAddForumTap,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AddPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(51.dp)
            .clip(RoundedCornerShape(percent = 50))
            .border(width = 1.dp, color = Color(0xFFCEC8FA), shape = RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontFamily = Satoshi,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            color = Color(0xFF5D47ED),
        )
    }
}

@Composable
private fun AdminAvatar(user: User?) {
    val url = user?.imageURL
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(Color(0xFFE5E5EA)),
        contentAlignment = Alignment.Center,
    ) {
        if (!url.isNullOrEmpty()) {
            AsyncImage(
                model = url,
                contentDescription = user.displayName,
                modifier = Modifier.size(52.dp).clip(CircleShape),
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = Color(0xFF1B1A23),
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun CommunityAvatar(stats: CommunityStats) {
    val url = stats.community.coverImageURL
    Box(
        modifier = Modifier
            .size(53.dp)
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(
                    stats.community.theme.gradientBrush.map { Color(it.toULong().toLong()) },
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (!url.isNullOrEmpty()) {
            AsyncImage(
                model = url,
                contentDescription = stats.community.name,
                modifier = Modifier.size(53.dp).clip(CircleShape),
            )
        } else {
            coil.compose.SubcomposeAsyncImage(
                model = R.drawable.ic_community_placeholder,
                contentDescription = stats.community.name,
                modifier = Modifier.size(53.dp).clip(CircleShape),
                loading = {},
            )
        }
    }
}

@Composable
private fun HorizontalDivider() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0xFFE5E5EA)),
    )
}

private data class TileRow(
    val label: String,
    val value: Int,
    val onClick: (() -> Unit)?,
)
