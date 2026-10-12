package com.kornerz.feature.nooks.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kornerz.core.model.Nook
import com.kornerz.core.model.User
import com.kornerz.core.ui.components.TriangleCorner

/**
 * Upcoming-tab row — 1:1 port of iOS `ScheduledNookView.swift`:
 *  - HStack with 16pt gap; padding 8pt; rounded 12dp background in
 *    the per-index accent color (iOS `RandomColorService.color(for:)`).
 *  - Left VStack: 55dp `TriangleImageView` publisher thumbnail
 *    (iOS `TriangleImageView(size: 55, radius: 8)`), then an
 *    "Add" capsule button.
 *  - Middle VStack: `event.formatedStartDate` (Lato 11 regular →
 *    Satoshi 11sp Regular), `event.title` (Lato 12 medium →
 *    Satoshi 12sp Medium), `in <community>` line (Lato 12
 *    regular), then "X spots left" (Lato 12 bold → Satoshi 12sp
 *    Bold).
 *  - Right: 80dp `TriangleImageView` with the top-right corner
 *    clipped (iOS `type: .topRight`) showing the parent Korner
 *    cover.
 */
@Composable
fun ScheduledNookCard(
    nook: Nook,
    publisher: User?,
    parentKornerName: String,
    parentKornerCoverUrl: String?,
    accentColor: Color,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(accentColor)
            .clickable { onClick(nook.id) }
            .padding(8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TriangleCover(
                imageUrl = publisher?.photoUrl,
                contentDescription = publisher?.displayName.orEmpty(),
                corner = TriangleCorner.TopLeft,
                size = 55.dp,
                radius = 8.dp,
            )
            AddButton()
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = nook.startDate?.let { formatNookStartDate(it) }.orEmpty(),
                color = Color.White,
                style = TextStyle(fontWeight = FontWeight.Normal, fontSize = 11.sp),
            )
            Text(
                text = nook.title,
                color = Color.White,
                style = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp),
                maxLines = 2,
            )
            Text(
                text = "in $parentKornerName",
                color = Color.White,
                style = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp),
                maxLines = 1,
            )
            Spacer(modifier = Modifier.size(4.dp))
            Text(
                text = "9 spots left",
                color = Color.White,
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp),
            )
        }
        TriangleCover(
            imageUrl = parentKornerCoverUrl,
            contentDescription = parentKornerName,
            corner = TriangleCorner.TopRight,
            size = 80.dp,
            radius = 8.dp,
        )
    }
}

@Composable
private fun AddButton() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF2E2F3E))
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(
            text = "Add",
            color = Color.White,
            style = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp),
        )
    }
}
