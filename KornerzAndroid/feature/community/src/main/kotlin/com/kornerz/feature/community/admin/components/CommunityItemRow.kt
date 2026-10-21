package com.kornerz.feature.community.admin.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kornerz.core.model.Community
import com.kornerz.core.ui.R
import com.kornerz.core.ui.theme.Satoshi

/**
 * A single row in the admin-owned-communities list. Mirrors iOS
 * `CommunityItemView.swift` lines 10–58 (P7 + parity fix):
 *
 *  - 60-pt circular cover thumbnail on the left (KFImage / `splash`
 *    placeholder on iOS, Coil / `ic_community_placeholder` on
 *    Android). Wrapped in a theme gradient if the URL is missing.
 *  - Community name in Satoshi Bold 16-pt, 3-line cap, leading
 *    (`InkLight` / `C1B1A23`), filling the middle.
 *  - Trailing "See all" pill button — Satoshi Bold 14-pt
 *    `PurpleLight` / `C5D47ED` text, 1-pt `C E5E5EA` rounded-20pt
 *    outline. Tapping it fires [onClick] (iOS routes the user to
 *    `CommunityDetailAsAdminVC`).
 *
 * The whole row is 65-pt tall (per iOS `.frame(height: 65)`), with no
 * card chrome — iOS renders the row directly inside a plain `List`,
 * not inside a rounded card.
 */
@Composable
fun CommunityItemRow(
    community: Community,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(65.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverThumbnail(community = community, modifier = Modifier.size(60.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text = community.name,
            fontFamily = Satoshi,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = Color(0xFF1B1A23),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        SeeAllPill(onClick = onClick)
    }
}

@Composable
private fun CoverThumbnail(
    community: Community,
    modifier: Modifier = Modifier,
) {
    val coverUrl = community.coverImageURL
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(
                    community.theme.gradientBrush.map { Color(it.toULong().toLong()) },
                ),
            ),
    ) {
        if (!coverUrl.isNullOrEmpty()) {
            AsyncImage(
                model = coverUrl,
                contentDescription = community.name,
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape),
            )
        } else {
            coil.compose.SubcomposeAsyncImage(
                model = R.drawable.ic_community_placeholder,
                contentDescription = community.name,
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape),
                loading = { /* placeholder while loading */ },
                error = {
                    Text(
                        text = community.name.firstOrNull()?.uppercase() ?: "?",
                        color = Color(0xFF353446),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                },
            )
        }
    }
}

/**
 * iOS `CommunityItemView` trailing "See all" pill — Satoshi Bold
 * 14-pt `PurpleLight` (`C5D47ED`) text on transparent background with
 * 1-pt `C E5E5EA` rounded 20-pt outline.
 */
@Composable
private fun SeeAllPill(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .border(
                width = 1.dp,
                color = Color(0xFFE5E5EA),
                shape = RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "See all",
            fontFamily = Satoshi,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = Color(0xFF5D47ED),
        )
    }
}
