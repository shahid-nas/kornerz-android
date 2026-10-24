package com.kornerz.feature.kornerz.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kornerz.core.model.Korner
import com.kornerz.core.ui.components.KornerzMiniCapsuleButton
import com.kornerz.core.ui.components.KornerzTriangleImage
import com.kornerz.core.ui.components.TriangleCorner
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Spacing

/**
 * Hero banner of the Korner detail screen. 1:1 port of iOS
 * `SwiftUI/Dashboard/Korner/KornerDetail/KornerDetailHeaderView.swift`.
 *
 * iOS layout:
 * ```
 * HStack {
 *     VStack(alignment: .leading) {
 *         backButton            // chevron.left, 16x16
 *         Text(communityName)   // lato 20 medium
 *         Spacer()
 *         headerActions         // Follow capsule + "About ⌄"
 *     }
 *     Spacer()
 *     TriangleImageView(image: .url(korner.imageURL), size: 125, radius: 15, type: .topRight)
 * }
 * .foregroundStyle(.white)
 * .padding()
 * .background(alignment: .bottom) { color.frame(height: 500) }
 * ```
 *
 * The trailing `.background` paints the Korner's theme colour behind
 * the header; the 500-pt frame is bottom-aligned, so the colour band
 * that is actually on screen is the header's own height. Android
 * therefore renders the hero as a solid theme-colour block that hosts
 * the chevron, the Korner name, the two actions and the
 * triangle-clipped cover — **not** as a 16:9 image banner with a
 * scrim, which is what the screen used to show.
 */
@Composable
fun KornerDetailHeader(
    korner: Korner,
    isFollowing: Boolean,
    onBackClick: () -> Unit,
    onFollowClick: () -> Unit,
    onAboutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(parseKornerThemeColor(korner.themeColor)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.space8),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // The chevron is 20 dp, but its touch target is 40 dp —
                // iOS' `chevron.left` is only 16×16, which is too small
                // for a thumb.
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable(onClick = onBackClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    text = korner.name,
                    // iOS `lato(20, weight: .medium)` → Satoshi Medium 20sp.
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = Color.White,
                )
                Spacer(modifier = Modifier.height(Spacing.space12))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KornerzMiniCapsuleButton(
                        text = if (isFollowing) "Unfollow" else "Follow",
                        onClick = onFollowClick,
                    )
                    Spacer(modifier = Modifier.width(Spacing.space6))
                    AboutButton(onClick = onAboutClick)
                }
            }
            Spacer(modifier = Modifier.width(Spacing.space8))
            KornerzTriangleImage(
                imageUrl = korner.coverUrl,
                size = 125.dp,
                cornerRadius = 15.dp,
                corner = TriangleCorner.TopRight,
            )
        }
    }
}

/**
 * The header's trailing "About" affordance — a 12-sp label followed by
 * a 12-dp chevron. iOS `KornerDetailHeaderView.headerActions` renders
 * `Text("about").font(.lato(12))` next to `chevron.down`.
 */
@Composable
private fun AboutButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "About",
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
            ),
            color = Color.White,
        )
        Icon(
            imageVector = Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(12.dp),
        )
    }
}

/**
 * Loading placeholder for the hero. Mirrors the real header's shape —
 * a wide colour block with a triangle-sized hole on the trailing edge —
 * so the screen does not jump when the data lands.
 */
@Composable
fun KornerDetailHeaderShimmer(modifier: Modifier = Modifier) {
    val placeholder = MaterialTheme.colorScheme.surfaceVariant
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(placeholder),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.space8),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(placeholder),
                )
                Spacer(modifier = Modifier.height(Spacing.space2))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(20.dp)
                        .background(placeholder),
                )
                Spacer(modifier = Modifier.height(Spacing.space12))
                Box(
                    modifier = Modifier
                        .width(96.dp)
                        .height(24.dp)
                        .background(placeholder),
                )
            }
            Spacer(modifier = Modifier.width(Spacing.space8))
            Box(
                modifier = Modifier
                    .size(125.dp)
                    .background(placeholder),
            )
        }
    }
}

/**
 * Resolves a Korner's theme colour. The field is a `#RRGGBB` hex
 * string on the `Communities` document; an unreadable or missing
 * value falls back to the brand Coral swatch, the same default the
 * Home carousel uses.
 */
internal fun parseKornerThemeColor(hex: String?): Color {
    val cleaned = hex?.trim()?.takeIf { it.isNotBlank() } ?: return FALLBACK_THEME_COLOR
    return runCatching {
        Color(android.graphics.Color.parseColor(if (cleaned.startsWith("#")) cleaned else "#$cleaned"))
    }.getOrDefault(FALLBACK_THEME_COLOR)
}

private val FALLBACK_THEME_COLOR = Color(0xFFE27D5A)

@Preview(showBackground = true, name = "Korner detail header — light")
@Composable
private fun KornerDetailHeaderLightPreview() {
    KornerzTheme(darkTheme = false) {
        KornerDetailHeader(
            korner = Korner(
                id = "c1",
                name = "Design Korner",
                themeColor = "#E27D5A",
                coverUrl = null,
            ),
            isFollowing = false,
            onBackClick = {},
            onFollowClick = {},
            onAboutClick = {},
        )
    }
}

@Preview(showBackground = true, name = "Korner detail header — dark")
@Composable
private fun KornerDetailHeaderDarkPreview() {
    KornerzTheme(darkTheme = true) {
        KornerDetailHeader(
            korner = Korner(
                id = "c1",
                name = "Design Korner",
                themeColor = "#5D47ED",
                coverUrl = null,
            ),
            isFollowing = true,
            onBackClick = {},
            onFollowClick = {},
            onAboutClick = {},
        )
    }
}
