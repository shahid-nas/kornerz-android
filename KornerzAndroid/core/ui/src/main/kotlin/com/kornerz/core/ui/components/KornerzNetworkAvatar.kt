package com.kornerz.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.kornerz.core.ui.theme.KornerzTheme

/**
 * URL-driven circular avatar used by the Home feed (Recent articles
 * author avatar at 24dp, Loop of the day, etc.). The original
 * `KornerzAvatar` in `KornerzImagePicker.kt` is a Uri-based control used
 * by the auth signup flow; this is its network-loaded sibling.
 *
 * Modifier is the last parameter per architecture §4.
 */
@Composable
fun KornerzNetworkAvatar(
    imageUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl != null) {
            SubcomposeAsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
                loading = { FallbackGlyph() },
                error = { FallbackGlyph() },
            )
        } else {
            FallbackGlyph()
        }
    }
}

@Composable
private fun FallbackGlyph() {
    Icon(
        imageVector = Icons.Filled.Person,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(24.dp),
    )
}

@Preview(showBackground = true, name = "Avatar — light")
@Composable
private fun KornerzNetworkAvatarLightPreview() {
    KornerzTheme(darkTheme = false) {
        KornerzNetworkAvatar(imageUrl = null)
    }
}

@Preview(showBackground = true, name = "Avatar — dark")
@Composable
private fun KornerzNetworkAvatarDarkPreview() {
    KornerzTheme(darkTheme = true) {
        KornerzNetworkAvatar(imageUrl = null)
    }
}
