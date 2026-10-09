package com.kornerz.feature.home.header

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
import com.kornerz.core.ui.components.HomeShimmerPlaceholder
import com.kornerz.core.ui.components.KornerzDiamondImage
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.feature.home.HomeHeaderData

/**
 * Header row: 52dp diamond avatar (1:1 port of iOS
 * `DiamondImageView(size:52, radius:10)`) on the leading edge, signed-in
 * user display name, points badge on the trailing edge.
 *
 * Mirrors iOS `HomeHeaderView.swift` layout. Modifier last per
 * architecture §4.
 */
@Composable
fun HomeHeaderContent(
    state: AsyncValue<HomeHeaderData>,
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
private fun LoadedHeader(data: HomeHeaderData, onAvatarTap: () -> Unit) {
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

private val Spacing = com.kornerz.core.ui.theme.Spacing

@Preview(showBackground = true, name = "Header — light")
@Composable
private fun HomeHeaderLightPreview() {
    KornerzTheme(darkTheme = false) {
        HomeHeaderContent(
            state = AsyncValue.Success(
                HomeHeaderData(
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

@Preview(showBackground = true, name = "Header — dark")
@Composable
private fun HomeHeaderDarkPreview() {
    KornerzTheme(darkTheme = true) {
        HomeHeaderContent(
            state = AsyncValue.Success(
                HomeHeaderData(
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

@Preview(showBackground = true, name = "Header — loading")
@Composable
private fun HomeHeaderLoadingPreview() {
    KornerzTheme(darkTheme = false) {
        HomeHeaderContent(state = AsyncValue.Loading, onAvatarTap = {})
    }
}
