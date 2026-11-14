package com.kornerz.feature.kornerz.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kornerz.core.model.User
import com.kornerz.core.ui.R
import com.kornerz.core.ui.components.KornerzDiamondImage
import com.kornerz.core.ui.components.KornerzTriangleImage
import com.kornerz.core.ui.components.TriangleCorner
import com.kornerz.core.ui.components.header.KornerzSectionTabHeader
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Spacing

/**
 * The users section of the Korner detail screen. 1:1 port of iOS
 * `SwiftUI/Dashboard/Korner/Sections/KornerUsersView.swift`.
 *
 * iOS layout:
 * ```
 * VStack {
 *     TabHeaderView(image: kornerstones, title: selectedUserTab.title, selectedTab: $selectedUserTab)
 *     switch selectedUserTab {
 *     case .kornerstones: leadersView    // triangle avatars
 *     case .klouders:     followersView  // diamond avatars
 *     }
 * }
 * ```
 *
 * The two tabs show the Korner's **admins** ("Kornerstones") and its
 * **followers** ("Klouders"). The Android port used to render a single
 * "Members" section with circular avatars; it now mirrors the iOS
 * tabbed section, and the avatar shape follows the tab: a triangle
 * for Kornerstones, a diamond for Klouders.
 *
 * The carousel shows at most [CAROUSEL_LIMIT] users and ends with a
 * "View All" tile — iOS's `viewAllButton(tab:)`
 * (`KornerUsersView.swift:105`), which opens the sheet listing every
 * member of the tab.
 */
@Composable
fun KornerDetailUsersSection(
    kornerstones: List<User>,
    klouders: List<User>,
    selectedTab: KornerDetailUserTab,
    onTabSelected: (KornerDetailUserTab) -> Unit,
    onUserClick: (String) -> Unit,
    onViewAllClick: (KornerDetailUserTab) -> Unit,
    themeColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        KornerzSectionTabHeader(
            title = selectedTab.title,
            iconRes = R.drawable.ic_kornerstones,
            tabs = KornerDetailUserTab.entries.map { it.title },
            selectedTabIndex = KornerDetailUserTab.entries.indexOf(selectedTab),
            onTabSelected = { index -> onTabSelected(KornerDetailUserTab.entries[index]) },
        )
        Spacer(modifier = Modifier.height(Spacing.space4))
        when (selectedTab) {
            KornerDetailUserTab.Kornerstones -> UserRow(
                users = kornerstones,
                shape = KornerDetailAvatarShape.Triangle,
                emptyLabel = KornerDetailUserTab.Kornerstones.title.lowercase(),
                onUserClick = onUserClick,
                onViewAllClick = { onViewAllClick(KornerDetailUserTab.Kornerstones) },
                themeColor = themeColor,
            )
            KornerDetailUserTab.Klouders -> UserRow(
                users = klouders,
                shape = KornerDetailAvatarShape.Diamond,
                emptyLabel = KornerDetailUserTab.Klouders.title.lowercase(),
                onUserClick = onUserClick,
                onViewAllClick = { onViewAllClick(KornerDetailUserTab.Klouders) },
                themeColor = themeColor,
            )
        }
    }
}

/** The avatar silhouette a tab renders its users with. */
private enum class KornerDetailAvatarShape { Triangle, Diamond }

@Composable
private fun UserRow(
    users: List<User>,
    shape: KornerDetailAvatarShape,
    emptyLabel: String,
    onUserClick: (String) -> Unit,
    onViewAllClick: () -> Unit,
    themeColor: Color,
) {
    if (users.isEmpty()) {
        Text(
            text = "No $emptyLabel found",
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.space8, vertical = Spacing.space4),
        )
        return
    }
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Spacing.space8),
        horizontalArrangement = Arrangement.spacedBy(Spacing.space4),
    ) {
        items(users.take(CAROUSEL_LIMIT), key = { it.id }) { user ->
            UserTile(
                user = user,
                shape = shape,
                onClick = { onUserClick(user.id) },
            )
        }
        item(key = VIEW_ALL_KEY) {
            ViewAllTile(
                onClick = onViewAllClick,
                themeColor = themeColor,
            )
        }
    }
}

/**
 * The carousel's trailing "View All" tile. 1:1 port of iOS
 * `KornerUsersView.viewAllButton(tab:)`
 * (`kornerz/Kornerz/SwiftUI/Dashboard/Korner/Sections/KornerUsersView.swift:105`):
 *
 * ```
 * VStack(spacing: 4) {
 *     Image(systemName: "list.bullet").font(.system(size: 16, weight: .bold))
 *     Text("view_all").font(.lato(10, weight: .regular)).lineLimit(1)
 * }
 * .frame(width: 60)
 * .frame(maxHeight: .infinity)
 * .padding(.vertical, 6)
 * .background(RoundedRectangle(cornerRadius: 8).fill(viewModel.color))
 * .foregroundColor(AppColor.CF2F4F6.swiftUIColor)
 * ```
 *
 * The tile is painted in the Korner's own theme colour — the colour
 * the detail screen was opened with — and its glyph and label in the
 * iOS `CF2F4F6` near-white.
 */
@Composable
private fun ViewAllTile(
    onClick: () -> Unit,
    themeColor: Color,
) {
    Column(
        modifier = Modifier
            .width(60.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(themeColor)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.space2, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.List,
            contentDescription = null,
            tint = VIEW_ALL_FOREGROUND,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = "View All",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Normal,
            ),
            color = VIEW_ALL_FOREGROUND,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * One avatar + name tile. iOS renders the avatar at 40 pt with an
 * 8-pt radius and the nickname underneath at 10 sp regular, one line,
 * with a 50-pt minimum width.
 */
@Composable
private fun UserTile(
    user: User,
    shape: KornerDetailAvatarShape,
    onClick: () -> Unit,
) {
    val avatarUrl = user.thumbnailURL ?: user.photoUrl
    Column(
        modifier = Modifier
            .width(56.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (shape) {
            KornerDetailAvatarShape.Triangle -> KornerzTriangleImage(
                imageUrl = avatarUrl,
                size = 40.dp,
                cornerRadius = 8.dp,
                corner = TriangleCorner.BottomLeft,
            )
            KornerDetailAvatarShape.Diamond -> KornerzDiamondImage(
                imageUrl = avatarUrl,
                fallbackAsset = "ic_user_placeholder",
                size = 40.dp,
                cornerRadius = 8.dp,
            )
        }
        Text(
            text = user.nickname.ifBlank { user.displayName },
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Normal,
            ),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Preview(showBackground = true, name = "Korner detail users — kornerstones")
@Composable
private fun KornerDetailUsersSectionKornerstonesPreview() {
    KornerzTheme(darkTheme = false) {
        KornerDetailUsersSection(
            kornerstones = listOf(
                User(id = "u1", email = "a@a.com", displayName = "Ada", nickname = "ada"),
                User(id = "u2", email = "b@b.com", displayName = "Bob", nickname = "bob"),
            ),
            klouders = emptyList(),
            selectedTab = KornerDetailUserTab.Kornerstones,
            onTabSelected = {},
            onUserClick = {},
            onViewAllClick = {},
            themeColor = Color(0xFFE27D5A),
        )
    }
}

@Preview(showBackground = true, name = "Korner detail users — klouders")
@Composable
private fun KornerDetailUsersSectionKloudersPreview() {
    KornerzTheme(darkTheme = false) {
        KornerDetailUsersSection(
            kornerstones = emptyList(),
            klouders = listOf(
                User(id = "u1", email = "a@a.com", displayName = "Ada", nickname = "ada"),
                User(id = "u2", email = "b@b.com", displayName = "Bob", nickname = "bob"),
            ),
            selectedTab = KornerDetailUserTab.Klouders,
            onTabSelected = {},
            onUserClick = {},
            onViewAllClick = {},
            themeColor = Color(0xFFE27D5A),
        )
    }
}

/**
 * How many users the carousel shows before the "View All" tile.
 *
 * Firestore's `in` clause caps at 30 values, so iOS resolves the
 * carousel's members in pages of 30
 * (`KornerDetailViewModel.usersPageSize`: "Firestore `in` queries are
 * limited to 30 values, so users are fetched in pages of this size").
 * The sheet that the tile opens lists the rest.
 */
private const val CAROUSEL_LIMIT = 30

/** The `LazyRow` key of the trailing "View All" tile. */
private const val VIEW_ALL_KEY = "view_all"

/**
 * The "View All" tile's foreground — iOS `AppColor.CF2F4F6`, the
 * near-white the glyph and the label are painted in.
 */
private val VIEW_ALL_FOREGROUND = Color(0xFFF2F4F6)
