package com.kornerz.feature.kornerz.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kornerz.core.model.User
import com.kornerz.core.ui.components.KornerzDiamondImage
import com.kornerz.core.ui.components.KornerzTriangleImage
import com.kornerz.core.ui.components.TriangleCorner
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Spacing

/**
 * The "View All" sheet — the full vertical list of a Korner's
 * Kornerstones (admins) or Klouders (followers). 1:1 port of iOS
 * `KornerUsersListView`
 * (`kornerz/Kornerz/SwiftUI/Dashboard/Korner/Sections/KornerUsersListView.swift`).
 *
 * iOS layout:
 * ```
 * NavigationStack {
 *     content
 *         .navigationTitle(title)
 *         .toolbar { principal: Text(title); trailing: xmark → dismiss }
 * }
 * ```
 * where `content` is a `ScrollView` over a `LazyVStack(spacing: 0)` of
 * rows separated by a `Divider()`, the last row carrying a
 * load-more trigger.
 *
 * Android's `ModalBottomSheet` is the same surface as iOS's
 * `.sheet(item:)` with `.presentationDetents([.medium, .large])` — the
 * same pairing the About sheet already uses
 * ([KornerAboutSheet]).
 *
 * The sheet is **stateless**: it renders [state] and reports the user
 * actions through [onDismiss] and [onLoadMore]. The paging itself
 * lives in the screen's ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KornerUsersSheet(
    state: KornerUsersSheetState,
    onLoadMore: () -> Unit,
    onUserClick: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SheetHeader(
                title = state.title,
                onDismiss = onDismiss,
            )
            SheetBody(
                state = state,
                onLoadMore = onLoadMore,
                onUserClick = onUserClick,
            )
        }
    }
}

/**
 * The sheet's title row — the title on the left and the close button
 * on the right.
 *
 * iOS puts the title in the navigation bar's **principal** slot
 * (centred) with the close button in the trailing slot. Android's
 * bottom sheet has no navigation bar, so the title leads the row and
 * the close button trails it.
 */
@Composable
private fun SheetHeader(
    title: String,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.space8)
            .padding(bottom = Spacing.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The sheet's list — either the empty state, the rows, or the first
 * page's spinner.
 *
 * The load-more trigger sits on the **last** row, exactly as iOS
 * places it (`if index == viewModel.users.count - 1 { loadMoreTrigger }`),
 * so the next page is requested as the user scrolls to the bottom.
 */
@Composable
private fun SheetBody(
    state: KornerUsersSheetState,
    onLoadMore: () -> Unit,
    onUserClick: (String) -> Unit,
) {
    when {
        state.isEmpty -> EmptyState(title = state.title)
        state.users.isEmpty() -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.space24),
            contentAlignment = Alignment.Center,
        ) {
            if (state.isInitialLoad) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
        else -> UserList(
            state = state,
            onLoadMore = onLoadMore,
            onUserClick = onUserClick,
        )
    }
}

/**
 * The vertical list of members.
 *
 * The next page is requested when the list scrolls close to its
 * end — the Compose counterpart of iOS's `loadMoreTrigger` appearing
 * on the last row.
 */
@Composable
private fun UserList(
    state: KornerUsersSheetState,
    onLoadMore: () -> Unit,
    onUserClick: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    // The paging flags change with every page, so the collector reads
    // the *current* state rather than the one captured when the
    // effect started.
    val currentState by rememberUpdatedState(state)
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)

    // The next page is requested as the list scrolls close to its end
    // — the Compose counterpart of iOS's `loadMoreTrigger` appearing
    // on the last row.
    LaunchedEffect(listState) {
        snapshotFlow {
            val page = currentState
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            page.hasMore &&
                !page.isLoading &&
                lastVisible >= page.users.size - LOAD_MORE_THRESHOLD
        }.collect { nearEnd ->
            if (nearEnd) currentOnLoadMore()
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = Spacing.space24),
    ) {
        items(state.users, key = { it.id }) { user ->
            UserRow(
                user = user,
                isLeader = state.isLeader,
                onClick = { onUserClick(user.id) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        if (state.isLoading) {
            item(key = "loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.space8),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

/**
 * One member row — the avatar, the nickname and the display name.
 *
 * iOS `KornerUsersListView.userRow`:
 * ```
 * HStack(spacing: 12) {
 *     TriangleImageView / DiamondImageView   // 40 pt, radius 8
 *     VStack(alignment: .leading, spacing: 2) {
 *         Text(nickName)      // lato 14 bold
 *         Text(name)          // lato 12 regular, grey
 *     }
 *     Spacer()
 * }
 * .padding(.horizontal)
 * .padding(.vertical, 10)
 * ```
 */
@Composable
private fun UserRow(
    user: User,
    isLeader: Boolean,
    onClick: () -> Unit,
) {
    val avatarUrl = user.thumbnailURL ?: user.photoUrl
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.space8, vertical = Spacing.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isLeader) {
            KornerzTriangleImage(
                imageUrl = avatarUrl,
                size = 40.dp,
                cornerRadius = 8.dp,
                corner = TriangleCorner.BottomLeft,
                fallbackAsset = "ic_user_placeholder",
            )
        } else {
            KornerzDiamondImage(
                imageUrl = avatarUrl,
                fallbackAsset = "ic_user_placeholder",
                size = 40.dp,
                cornerRadius = 8.dp,
            )
        }
        Spacer(modifier = Modifier.width(Spacing.space6))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.nickname.ifBlank { user.displayName },
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (user.displayName.isNotBlank()) {
                Text(
                    text = user.displayName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The sheet's empty state. Mirrors iOS
 * `KornerUsersListView.content`'s empty branch — the `no_found_message`
 * localisation key, lower-cased.
 */
@Composable
private fun EmptyState(title: String) {
    Text(
        text = "No ${title.lowercase()} found",
        style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.space12, bottom = Spacing.space24),
    )
}

/**
 * How close to the end of the list the user must scroll before the
 * next page is requested.
 */
private const val LOAD_MORE_THRESHOLD = 3

@Preview(showBackground = true, name = "Korner users sheet — kornerstones")
@Composable
private fun KornerUsersSheetLeadersPreview() {
    KornerzTheme(darkTheme = false) {
        KornerUsersSheet(
            state = KornerUsersSheetState(
                title = "Kornerstones",
                isLeader = true,
                codes = listOf("c1", "c2"),
                users = listOf(
                    User(id = "u1", email = "a@a.com", displayName = "Ada Lovelace", nickname = "ada"),
                    User(id = "u2", email = "b@b.com", displayName = "Bob Stone", nickname = "bob"),
                ),
                offset = 2,
                hasLoaded = true,
            ),
            onLoadMore = {},
            onUserClick = {},
            onDismiss = {},
        )
    }
}

@Preview(showBackground = true, name = "Korner users sheet — klouders")
@Composable
private fun KornerUsersSheetFollowersPreview() {
    KornerzTheme(darkTheme = false) {
        KornerUsersSheet(
            state = KornerUsersSheetState(
                title = "Klouders",
                isLeader = false,
                codes = listOf("c1"),
                users = listOf(
                    User(id = "u1", email = "a@a.com", displayName = "Ada Lovelace", nickname = "ada"),
                ),
                offset = 1,
                hasLoaded = true,
            ),
            onLoadMore = {},
            onUserClick = {},
            onDismiss = {},
        )
    }
}

@Preview(showBackground = true, name = "Korner users sheet — empty")
@Composable
private fun KornerUsersSheetEmptyPreview() {
    KornerzTheme(darkTheme = false) {
        KornerUsersSheet(
            state = KornerUsersSheetState(
                title = "Klouders",
                isLeader = false,
                hasLoaded = true,
            ),
            onLoadMore = {},
            onUserClick = {},
            onDismiss = {},
        )
    }
}
