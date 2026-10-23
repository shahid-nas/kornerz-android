package com.kornerz.feature.shell

import androidx.annotation.DrawableRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GroupWork
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import com.kornerz.feature.ranking.navigation.RankingRoute

/**
 * One row in the bottom navigation bar. P0 uses Material Icons as
 * placeholders; designer-supplied brand icons land in a follow-up.
 *
 * Profile is **not** a tab — it is pushed onto the root NavHost with
 * the bottom bar hidden, mirroring the iOS `UserProfileVC` push from
 * `HomeTabCoordinator.navigateToUserProfile()` (which sets
 * `hidesBottomBarWhenPushed = true`). P6 surfaces Profile from the
 * avatar tap in the Home / Kornerz / Nooks / Loops tabs.
 */
internal enum class ShellTab(
    val label: String,
    val icon: ImageVector,
    val route: String,
    /**
     * An optional drawable-backed icon, used by the Klouders tab to
     * carry the iOS `tab_klouders` asset (P10 task 6.5). When it is
     * `null` the tab renders [icon].
     */
    @DrawableRes val iconRes: Int? = null,
) {
    Home(label = "Home", icon = Icons.Filled.Home, route = "tab/home"),
    Kornerz(label = "Kornerz", icon = Icons.Filled.GroupWork, route = "tab/kornerz"),
    Nooks(label = "Nooks", icon = Icons.Filled.Forum, route = "tab/nooks"),
    Loops(label = "Loops", icon = Icons.Filled.PlayCircle, route = "tab/loops"),
    Klouders(
        label = "Klouders",
        icon = Icons.Filled.Subscriptions,
        route = RankingRoute.LIST,
        iconRes = R.drawable.ic_tab_klouders,
    );

    companion object {
        val ordered: List<ShellTab> = listOf(Home, Kornerz, Nooks, Loops, Klouders)
    }
}

@Composable
internal fun KornerzBottomBar(
    currentRoute: String?,
    onSelect: (ShellTab) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        ShellTab.ordered.forEach { tab ->
            val selected = currentRoute == tab.route
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(tab) },
                icon = {
                    val drawable = tab.iconRes
                    if (drawable != null) {
                        Icon(
                            painter = painterResource(drawable),
                            contentDescription = tab.label,
                        )
                    } else {
                        Icon(imageVector = tab.icon, contentDescription = tab.label)
                    }
                },
                label = { Text(text = tab.label) },
            )
        }
    }
}
