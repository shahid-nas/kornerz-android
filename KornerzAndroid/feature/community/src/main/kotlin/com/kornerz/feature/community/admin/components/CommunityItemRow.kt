package com.kornerz.feature.community.admin.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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

/**
 * A single row in the admin-owned-communities list (P7, task 5.3).
 * Mirrors iOS `CommunityItemView.swift` lines 12–88.
 *
 * Layout: 56-pt circular cover on the left, vertically-stacked
 * name + description in the middle, trailing vertical stack of two
 * count pills on the right. 73-pt tall rounded card on
 * `colorScheme.surface` with `colorScheme.outlineVariant` 1-pt stroke.
 */
@Composable
fun CommunityItemRow(
    community: Community,
    memberCount: Int,
    nookCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(73.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverThumbnail(community = community, modifier = Modifier.size(56.dp))
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = community.name,
                color = Color(0xFF353446),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val description = community.description
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    color = Color(0xFF9E9EAE),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.End,
        ) {
            CountPill(label = "Members", value = memberCount)
            CountPill(label = "Nooks", value = nookCount)
        }
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
                    .size(56.dp)
                    .clip(CircleShape),
            )
        } else {
            coil.compose.SubcomposeAsyncImage(
                model = R.drawable.ic_community_placeholder,
                contentDescription = community.name,
                modifier = Modifier
                    .size(56.dp)
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

@Composable
private fun CountPill(label: String, value: Int) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF2F4F6))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = Color(0xFF9E9EAE),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = value.toString(),
            color = Color(0xFF353446),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
