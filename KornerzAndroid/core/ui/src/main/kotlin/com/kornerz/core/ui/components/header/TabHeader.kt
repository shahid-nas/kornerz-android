package com.kornerz.core.ui.components.header

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.User
import com.kornerz.core.storage.session.SessionRepository
import com.kornerz.core.storage.session.SessionState
import com.kornerz.core.ui.components.HomeShimmerPlaceholder
import com.kornerz.core.ui.components.KornerzDiamondImage
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
 * Shared ViewModel backing the tab header across all five bottom-bar
 * tabs (Home / Kornerz / Nooks / Loops / Klouders). Reads the current
 * user from [SessionRepository]; P2 returns 0 points, P10 wires
 * `GamificationService` (mirrors iOS `HomeHeaderViewModel`).
 *
 * Scoped to its parent `@HiltViewModel` via [ViewModelScoped]
 * (architecture §2.6 — "per-ViewModel helpers (rare — prefer passing
 * via constructor)") and instantiated per-host because the section
 * shares the host's coroutine scope.
 */
@ViewModelScoped
class TabHeaderViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<AsyncValue<TabHeaderData>>(AsyncValue.Loading)
    val state: StateFlow<AsyncValue<TabHeaderData>> = _state.asStateFlow()

    fun fetchData() {
        scope.launch {
            _state.value = AsyncValue.Loading
            runCatching {
                val session = sessionRepository.data.first()
                when (session) {
                    is SessionState.Authenticated -> TabHeaderData(
                        user = session.user,
                        points = 0,
                    )
                    SessionState.Anonymous -> error("not signed in")
                    SessionState.Loading -> return@launch
                }
            }.onSuccess { _state.value = AsyncValue.Success(it) }
                .onFailure { _state.value = AsyncValue.Error(it) }
        }
    }
}

/**
 * Header row: 52dp diamond avatar (1:1 port of iOS
 * `DiamondImageView(size:52, radius:10)`) on the leading edge,
 * signed-in user display name, points badge on the trailing edge.
 *
 * Mirrors iOS `HomeHeaderView.body.contentView` layout:
 * `HStack(spacing: 16) { userImageView; Spacer(); userPointsView }`
 * with `.padding(.horizontal)`. The avatar is clickable and forwards
 * to [onAvatarTap] to match iOS `viewModel.onUserTap?()`. Modifier
 * last per architecture §4.
 *
 * Each tab feeds this composable from its parent ViewModel, which
 * injects the shared [TabHeaderViewModel] and exposes its state via
 * `AsyncValue<TabHeaderData>` — mirrors iOS, where every tab embeds
 * `HomeHeaderView` at the top of its scrollable content.
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
    Row(verticalAlignment = Alignment.CenterVertically) {
        KornerzDiamondImage(
            imageUrl = data.user.photoUrl,
            fallbackAsset = null,
            size = 52.dp,
            cornerRadius = 10.dp,
            modifier = Modifier.clickable(onClick = onAvatarTap),
        )
        Spacer(modifier = Modifier.width(Spacing.space8))
        Text(
            text = data.user.displayName.ifBlank { data.user.nickname },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
        ) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = data.points.toString(),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
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