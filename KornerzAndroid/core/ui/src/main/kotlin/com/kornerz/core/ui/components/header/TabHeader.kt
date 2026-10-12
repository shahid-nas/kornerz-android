package com.kornerz.core.ui.components.header

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.User
import com.kornerz.core.network.gamification.GamificationRepository
import com.kornerz.core.storage.session.SessionRepository
import com.kornerz.core.storage.session.SessionState
import com.kornerz.core.ui.R
import com.kornerz.core.ui.components.HomeShimmerPlaceholder
import com.kornerz.core.ui.components.KornerzDiamondImage
import com.kornerz.core.ui.theme.KornerzColors
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Spacing
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Avatar / points projection rendered at the top of every bottom-bar
 * tab (architecture §2.5 — features consume shared UI from `:core:ui`).
 *
 * Mirrors iOS `HomeHeaderView` / `HomeHeaderData`:
 * `kornerz/Kornerz/SwiftUI/Dashboard/HomeTab/Sections/HeaderView/`.
 */
data class TabHeaderData(
    val user: User,
    val points: Int,
)

/**
 * Shared ViewModel backing the tab header across all four bottom-bar
 * tabs that surface it (Home / Kornerz / Nooks / Loops). Reads the
 * current user from [SessionRepository] and the monthly points from
 * [GamificationRepository] — mirrors iOS `HomeHeaderViewModel` which
 * observes `AppHelper.shared.user` + `RepositoryFactory.gamification
 * .getUserPoints` (lines 46–60 of `HomeHeaderViewModel.swift`).
 *
 * Scoped to its parent `@HiltViewModel` via [ViewModelScoped]
 * (architecture §2.6 — "per-ViewModel helpers (rare — prefer passing
 * via constructor)") and instantiated per-host because the section
 * shares the host's coroutine scope.
 */
@ViewModelScoped
class TabHeaderViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val gamificationRepository: GamificationRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<AsyncValue<TabHeaderData>>(AsyncValue.Loading)
    val state: StateFlow<AsyncValue<TabHeaderData>> = _state.asStateFlow()

    init {
        // Auto-fetch on construction so every tab that injects the
        // shared `TabHeaderViewModel` (Home, Kornerz, Nooks, Loops)
        // actually receives a `Success` state on first composition.
        // The previous behaviour required the host VM to call
        // `refresh()` from its own `init { }` block — which only
        // `HomeViewModel` did, leaving the Kornerz / Nooks / Loops
        // tabs stuck on the 52-dp shimmer placeholder indefinitely.
        fetchData()
    }

    fun fetchData() {
        scope.launch {
            runCatching {
                val session = sessionRepository.data.first()
                if (session !is SessionState.Authenticated) return@runCatching null
                val points = gamificationRepository.getUserPoints().getOrDefault(0)
                TabHeaderData(
                    user = session.user,
                    points = points,
                )
            }.onSuccess { data ->
                if (data != null) {
                    _state.value = AsyncValue.Success(data)
                }
                // `data == null` (session not authenticated) — keep the
                // current state. The next `fetchData()` (e.g. on a
                // tab switch that re-hydrates the session) will pick
                // up where we left off.
            }.onFailure { _state.value = AsyncValue.Error(it) }
        }
    }
}

/**
 * Header row: 52-dp diamond avatar (1:1 port of iOS
 * `DiamondImageView(size:52, radius:10)`) on the leading edge and
 * a points badge on the trailing edge.
 *
 * Mirrors iOS `HomeHeaderView.body.contentView` layout:
 * `HStack(spacing: 16) { userImageView; Spacer(); userPointsView }`
 * with `.padding(.horizontal)`. The avatar is clickable and forwards
 * to [onAvatarTap] to match iOS `viewModel.onUserTap?()`.
 *
 * Color tokens (per iOS source):
 * - avatar background: `C2E2F3E` (`CharcoalLight`) stroke / `CF3F6FB`
 *   (`PowderLight`) fill — `DiamondImageView` uses the iOS
 *   `AppColor.CF3F6FB.swiftUIColor` for the background.
 * - points icon: `R.drawable.ic_points` (mirrors iOS `icon_points`)
 *   in `C2E2F3E` (Charcoal).
 * - points text: Lato/Satoshi 14-pt Bold in `C1B1A23` (Ink).
 */
@Composable
fun TabHeaderContent(
    state: AsyncValue<TabHeaderData>,
    onAvatarTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.space8, vertical = Spacing.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (state) {
            is AsyncValue.Success -> LoadedHeader(state.data, onAvatarTap)
            is AsyncValue.Error -> Box(modifier = Modifier.size(52.dp))
            AsyncValue.Loading -> HomeShimmerPlaceholder(modifier = Modifier.size(52.dp)) {
                Box(modifier = Modifier.size(52.dp))
            }
        }
    }
}

@Composable
private fun LoadedHeader(data: TabHeaderData, onAvatarTap: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        KornerzDiamondImage(
            imageUrl = data.user.thumbnailURL,
            fallbackAsset = "ic_user_placeholder",
            size = 52.dp,
            cornerRadius = 10.dp,
            modifier = Modifier.clickable(onClick = onAvatarTap),
        )
        Spacer(modifier = Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_points),
                contentDescription = null,
                tint = KornerzColors.CharcoalLight,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = data.points.toString(),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = KornerzColors.InkLight,
            )
        }
    }
}

@Preview(showBackground = true, name = "TabHeader — light")
@Composable
private fun TabHeaderLightPreview() {
    KornerzTheme(darkTheme = false) {
        TabHeaderContent(
            state = AsyncValue.Success(
                TabHeaderData(
                    user = User(
                        id = "u1",
                        email = "u@example.com",
                        displayName = "Ada Lovelace",
                        nickname = "ada",
                    ),
                    points = 42,
                ),
            ),
            onAvatarTap = {},
        )
    }
}

@Preview(showBackground = true, name = "TabHeader — dark")
@Composable
private fun TabHeaderDarkPreview() {
    KornerzTheme(darkTheme = true) {
        TabHeaderContent(
            state = AsyncValue.Success(
                TabHeaderData(
                    user = User(
                        id = "u1",
                        email = "u@example.com",
                        displayName = "Ada Lovelace",
                        nickname = "ada",
                    ),
                    points = 42,
                ),
            ),
            onAvatarTap = {},
        )
    }
}

@Preview(showBackground = true, name = "TabHeader — loading")
@Composable
private fun TabHeaderLoadingPreview() {
    KornerzTheme(darkTheme = false) {
        TabHeaderContent(state = AsyncValue.Loading, onAvatarTap = {})
    }
}