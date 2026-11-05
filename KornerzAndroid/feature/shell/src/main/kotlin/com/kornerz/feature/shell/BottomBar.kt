package com.kornerz.feature.shell

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import com.kornerz.feature.ranking.navigation.RankingRoute

/**
 * One row in the bottom navigation bar. Every tab carries the iOS
 * brand glyph as an Android VectorDrawable, ported from the matching
 * asset in `kornerz/Kornerz/Resources/Assets.xcassets/Images/Home/`
 * (`tab_home`, `tab_kornerz`, `tab_nooks`, `tab_loops`,
 * `tab_klouders`). The Material icons the bar shipped with since P0
 * were placeholders.
 *
 * Profile is **not** a tab — it is pushed onto the root NavHost with
 * the bottom bar hidden, mirroring the iOS `UserProfileVC` push from
 * `HomeTabCoordinator.navigateToUserProfile()` (which sets
 * `hidesBottomBarWhenPushed = true`). P6 surfaces Profile from the
 * avatar tap in the Home / Kornerz / Nooks / Loops tabs.
 */
internal enum class ShellTab(
    val label: String,
    /**
     * The tab's brand glyph, ported from the iOS tab-bar asset of the
     * same name. `Icon` tints the painter with the bottom bar's
     * ambient content colour, so the selected and unselected states
     * follow the bar's own palette — the same behaviour the iOS
     * template-rendered assets get from `UITabBarItem`.
     */
    @DrawableRes val iconRes: Int,
    val route: String,
) {
    Home(label = "Home", iconRes = R.drawable.ic_tab_home, route = "tab/home"),
    Kornerz(label = "Kornerz", iconRes = R.drawable.ic_tab_kornerz, route = "tab/kornerz"),
    Nooks(label = "Nooks", iconRes = R.drawable.ic_tab_nooks, route = "tab/nooks"),
    Loops(label = "Loops", iconRes = R.drawable.ic_tab_loops, route = "tab/loops"),
    Klouders(label = "Klouders", iconRes = R.drawable.ic_tab_klouders, route = RankingRoute.LIST);

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
                    Icon(
                        painter = painterResource(tab.iconRes),
                        contentDescription = tab.label,
                    )
                },
                label = { Text(text = tab.label) },
            )
        }
    }
}
