package com.kornerz.feature.profile.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kornerz.core.model.User
import com.kornerz.core.ui.R
import com.kornerz.core.ui.theme.KornerzColors

/**
 * The avatar + name + nickName + two-pill row block that tops the
 * Profile screen. Mirrors iOS `ProfileUserInfoCell` (lines 13–102)
 * (P6, task 5.4).
 *
 * Color tokens (per `user-profile-domain` spec):
 * - avatar border: White
 * - avatar gradient: `CornflowerLight` → `IceLight` → `PowderLight`
 *   (mirrors iOS `BCCFF3` → `DFE7F7` → `F3F6FB`)
 * - name: Satoshi Black 20-pt, `SlateLight`
 * - nickName: Satoshi Bold 16-pt, `PurpleGreyLight`
 * - stat pill bg: `SmokeLight`, 20-pt corners
 * - stat pill label: Satoshi Bold 14-pt, `PurpleGreyLight`
 * - stat pill value: Satoshi Bold 20-pt, `InkLight`
 *
 * When the user has no avatar URL, the bundled
 * `R.drawable.ic_user_placeholder` is shown (mirrors iOS line 83
 * `setImageFrom(imageUrl, ..., placeholderImage: "user")`).
 */
@Composable
fun UserInfoHeader(
    user: User,
    adminCommunitiesCount: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(118.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            KornerzColors.PurpleLight, // not used — placeholder
                            Color(0xFFBCCFF3),
                            Color(0xFFDFE7F7),
                            Color(0xFFF3F6FB),
                        ).takeLast(3),
                    ),
                )
                .border(5.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            val url = user.thumbnailURL ?: user.imageURL
            if (!url.isNullOrEmpty()) {
                AsyncImage(
                    model = url,
                    contentDescription = user.displayName,
                    modifier = Modifier
                        .size(108.dp)
                        .clip(CircleShape),
                )
            } else {
                coil.compose.SubcomposeAsyncImage(
                    model = R.drawable.ic_user_placeholder,
                    contentDescription = user.displayName,
                    modifier = Modifier
                        .size(108.dp)
                        .clip(CircleShape),
                    loading = { /* placeholder while loading */ },
                    error = {
                        Text(
                            text = user.displayName.firstOrNull()?.uppercase() ?: "?",
                            color = KornerzColors.SlateLight,
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Black,
                        )
                    },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = user.displayName,
            color = KornerzColors.SlateLight,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
        )
        if (user.nickname.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = user.nickname,
                color = KornerzColors.PurpleGreyLight,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatPill(label = "Calls", value = "0", modifier = Modifier.weight(1f))
            StatPill(
                label = "Communities",
                value = adminCommunitiesCount.toString(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatPill(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFFF2F4F6))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = KornerzColors.PurpleGreyLight,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            color = KornerzColors.InkLight,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
        )
    }
}
