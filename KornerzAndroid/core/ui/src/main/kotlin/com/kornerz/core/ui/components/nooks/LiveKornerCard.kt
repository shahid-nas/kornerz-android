package com.kornerz.core.ui.components.nooks

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kornerz.core.model.Korner
import com.kornerz.core.model.Nook
import com.kornerz.core.ui.components.TriangleClippedShape
import com.kornerz.core.ui.components.TriangleCorner
import com.kornerz.core.ui.theme.Satoshi

/**
 * Live-tab row — 1:1 port of iOS `NookView.swift` (live card):
 *  - HStack with 16pt gap; padding 8pt; rounded 12dp background in
 *    the per-index accent color (iOS `RandomColorService.color(for:)`).
 *  - Left VStack: community name (Lato 11 regular → Satoshi 11sp
 *    Regular), session title (Lato 12 → Satoshi 12sp Regular), Spacer,
 *    Join button + spots-left text (Lato 12 bold → Satoshi 12sp Bold).
 *  - Right: 80dp `TriangleImageView` with the top-right corner
 *    clipped (iOS `type: .topRight`).
 *
 * The "Join" button is a static capsule (no navigation wired — the
 * join action requires the `joinVideoCall` flow that lives in the
 * P9 video calling phase). Spots-left text is also static for the
 * same reason — Android does not yet model the iOS REST
 * `ApiService.getSessionsInfoAsync` payload.
 */
@Composable
fun LiveKornerCard(
    korner: Korner,
    accentColor: Color,
    onClick: (String) -> Unit,
    onJoin: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(accentColor)
            .clickable {
                val sessionId = korner.sessionId
                if (sessionId != null) onJoin(sessionId) else onClick(korner.id)
            }
            .padding(8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = korner.name,
                color = Color.White,
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = Satoshi,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                ),
                maxLines = 1,
            )
            Text(
                text = korner.description?.takeIf { it.isNotBlank() }
                    ?: "Live session in progress",
                color = Color.White,
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = Satoshi,
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.sp,
                ),
                maxLines = 2,
            )
            Spacer(modifier = Modifier.size(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (korner.sessionId != null) {
                    JoinLabel()
                }
                Text(
                    text = "9 spots left",
                    color = Color.White,
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = Satoshi,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    ),
                    maxLines = 1,
                )
            }
        }
        TriangleCover(
            imageUrl = korner.coverUrl,
            contentDescription = korner.name,
            corner = TriangleCorner.TopRight,
            size = 80.dp,
            radius = 8.dp,
        )
    }
}

@Composable
private fun JoinLabel() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF2E2F3E))
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(
            text = "Join",
            color = Color.White,
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = Satoshi,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
            ),
        )
    }
}

/**
 * Convenience overload for the Korner detail "Current Nook" section.
 * Mirrors the iOS `KornerNookView.swift` row layout when the nook
 * is live (the "LIVE" badge is the same red 10dp dot used by
 * `KornerNookView` in iOS).
 */
@Composable
fun LiveKornerCard(
    nook: Nook,
    parentKornerName: String,
    accentColor: Color,
    onClick: (String) -> Unit,
    onJoin: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(accentColor)
            .clickable {
                val sessionId = nook.sessionId
                if (sessionId != null) onJoin(sessionId) else onClick(nook.id)
            }
            .padding(8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = nook.title,
                color = Color.White,
                style = androidx.compose.ui.text.TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                ),
                maxLines = 1,
            )
            Text(
                text = "Live in $parentKornerName",
                color = Color.White,
                style = androidx.compose.ui.text.TextStyle(
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.sp,
                ),
                maxLines = 2,
            )
            Spacer(modifier = Modifier.size(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (nook.sessionId != null) {
                    JoinLabel()
                }
                Text(
                    text = "9 spots left",
                    color = Color.White,
                    style = androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    ),
                    maxLines = 1,
                )
            }
        }
        TriangleCover(
            imageUrl = nook.coverUrl,
            contentDescription = nook.title,
            corner = TriangleCorner.TopRight,
            size = 80.dp,
            radius = 8.dp,
        )
    }
}

@Composable
internal fun TriangleCover(
    imageUrl: String?,
    contentDescription: String,
    corner: TriangleCorner,
    size: androidx.compose.ui.unit.Dp,
    radius: androidx.compose.ui.unit.Dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(TriangleClippedShape(corner = corner, cornerRadius = radius))
            .background(Color(0xFFCCCCCC)),
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size),
        )
    }
}
