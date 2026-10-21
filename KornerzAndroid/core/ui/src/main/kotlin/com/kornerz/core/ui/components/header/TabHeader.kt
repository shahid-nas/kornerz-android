package com.kornerz.core.ui.components.header

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
 * Scoped as `@Singleton` so the four tab VMs all read from the
 * **same** state flow. The previous `@ViewModelScoped` setup
 * created one `TabHeaderViewModel` per tab — four separate state
 * machines, four independent session subscriptions, and one tab
 * (Nooks) was reliably stuck on the Loading shimmer because its
 * instance was constructed before the `SessionRepository` DataStore
 * had emitted `Authenticated`. With a singleton, the header
 * resolves once at app start and every tab reads the same state.
 *
 * The coroutine scope is owned by the singleton (SupervisorJob +
 * `Dispatchers.Main.immediate`) and lives for the application
 * lifetime, so the session collection never gets cancelled by a
 * tab navigation.
 */
@Singleton
class TabHeaderViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val gamificationRepository: GamificationRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<AsyncValue<TabHeaderData>>(AsyncValue.Loading)
    val state: StateFlow<AsyncValue<TabHeaderData>> = _state.asStateFlow()

    init {
        // Observe the session continuously and re-resolve the header
        // every time the session transitions. This handles the
        // cold-start race where the tab VM is constructed before the
        // DataStore-backed `SessionRepository.data` has emitted
        // `Authenticated` — the previous `first()` implementation
        // bailed in that case and left the header stuck on
        // `AsyncValue.Loading` (shimmer).
        scope.launch {
            sessionRepository.data.collect { session ->
                if (session is SessionState.Authenticated) {
                    resolveHeader(user = session.user)
                }
                // else: keep state at the previous value. The header
                // composable renders a full-width shimmer strip in the
                // `Loading` state so the user sees a non-empty header
                // while the session hydrates.
            }
        }
    }

    /**
     * Re-fetches the points count for the current user and emits
     * `Success(TabHeaderData(user, points))`. Failure on the points
     * query surfaces as a `Success` with `points = 0` (the user
     * should see their own header even if the gamification server
     * is down). Other failures (e.g. the user object deserializing
     * wrong) fall through to `Error`.
     */
    private suspend fun resolveHeader(user: com.kornerz.core.model.User) {
        runCatching {
            val points = gamificationRepository.getUserPoints().getOrDefault(0)
            TabHeaderData(user = user, points = points)
        }.onSuccess { data -> _state.value = AsyncValue.Success(data) }
            .onFailure { _state.value = AsyncValue.Error(it) }
    }

    /**
     * Re-fetches the header on demand. Hosts can still call this from
     * their pull-to-refresh handler (the [HomeViewModel.refresh] /
     * [NooksViewModel.refresh] / etc. paths). Internally we just emit
     * the current user again through [resolveHeader] to refresh the
     * points count without re-waiting on the session flow.
     */
    fun fetchData() {
        scope.launch {
            val session = sessionRepository.data.first()
            if (session is SessionState.Authenticated) resolveHeader(session.user)
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
            // Render the full header layout (avatar + 0-point badge)
            // for Loading + Error states — matches the iOS
            // `.redacted(reason: .placeholder)` behaviour. The
            // previous implementation collapsed to a 52-dp shimmer
            // box, which made the header look missing on tabs where
            // `TabHeaderViewModel.fetchData` bailed early (e.g. when
            // the session is still hydrating from DataStore).
            is AsyncValue.Error -> LoadedHeader(
                data = TabHeaderData(
                    user = User(id = "", email = "", displayName = "", nickname = ""),
                    points = 0,
                ),
                onAvatarTap = onAvatarTap,
            )
            AsyncValue.Loading -> HomeShimmerPlaceholder(
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Mirror the real layout so the shimmer takes up
                // exactly the height of the success-state header
                // (42-dp avatar + spacer + 18-dp points badge).
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape),
                    )
                    Spacer(modifier = Modifier.width(Spacing.space2))
                    Box(
                        modifier = Modifier
                            .width(24.dp)
                            .height(14.dp),
                    )
                }
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
            // 42-dp diamond avatar (reduced from the iOS port's
            // 52-dp `DiamondImageView(size:52, radius:10)` per the
            // design review — the iOS spec reads large on Android's
            // display at 1× density). 8-dp corner radius preserves
            // the iOS diamond ratio (10/52 ≈ 8/42 ≈ 0.19).
            size = 42.dp,
            cornerRadius = 8.dp,
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