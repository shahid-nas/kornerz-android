package com.kornerz.feature.profile.components

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
    onEditAvatar: (() -> Unit)? = null,
    isUploading: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The avatar and its camera badge share one Box so the badge
        // overlaps the circle's bottom-right edge, the way iOS layers
        // the edit button over the image
        // (`ProfileUserInfoCell` — the `editUserImageClicked` button sits
        // on top of `imageViewAvatar`).
        //
        // Both the image and the badge open the picker: iOS only wires
        // the button, but tapping the avatar is the more natural
        // gesture, so the whole circle is clickable too.
        //
        // The Box is larger than the avatar so the badge — which hangs
        // off the circle's edge — has its own space to occupy and does
        // not overlap the name below it.
        Box(
            modifier = Modifier.size(AVATAR_BOX_SIZE),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(AVATAR_OUTER_SIZE)
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
                    .border(5.dp, Color.White, CircleShape)
                    .then(
                        if (onEditAvatar != null) {
                            Modifier.clickable(onClick = onEditAvatar)
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                val url = user.thumbnailURL ?: user.imageURL
                if (!url.isNullOrEmpty()) {
                    AsyncImage(
                        model = url,
                        contentDescription = user.displayName,
                        modifier = Modifier
                            .size(AVATAR_INNER_SIZE)
                            .clip(CircleShape),
                    )
                } else {
                    coil.compose.SubcomposeAsyncImage(
                        model = R.drawable.ic_user_placeholder,
                        contentDescription = user.displayName,
                        modifier = Modifier
                            .size(AVATAR_INNER_SIZE)
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
                // The upload overlay sits on top of the image so the
                // avatar itself shows that the upload is in flight.
                if (isUploading) {
                    Box(
                        modifier = Modifier
                            .size(AVATAR_INNER_SIZE)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
            }
            if (onEditAvatar != null) {
                // Anchored to the bottom-right of the box and pushed
                // outward, so the badge straddles the circle's edge
                // instead of sitting below it.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = AVATAR_BADGE_OVERHANG, y = AVATAR_BADGE_OVERHANG)
                        .size(AVATAR_BADGE_SIZE)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(2.dp, KornerzColors.CornflowerLight, CircleShape)
                        .clickable(onClick = onEditAvatar),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = "Change profile photo",
                        tint = KornerzColors.SlateLight,
                        modifier = Modifier.size(18.dp),
                    )
                }
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

/** The avatar's outer size — the gradient circle plus its white ring. */
private val AVATAR_OUTER_SIZE = 118.dp

/** The avatar's inner size — the image itself, inside the 5 dp ring. */
private val AVATAR_INNER_SIZE = 108.dp

/**
 * The size of the Box that holds the avatar and its camera badge.
 *
 * The badge is [AVATAR_BADGE_SIZE] wide and hangs off the circle's
 * bottom-right edge, so the Box has to be wider and taller than the
 * avatar itself — otherwise the badge would spill over the name below.
 */
private val AVATAR_BOX_SIZE = 132.dp

/** The size of the camera badge that overlaps the avatar's edge. */
private val AVATAR_BADGE_SIZE = 34.dp

/**
 * How far the badge hangs off the avatar's bottom-right edge.
 *
 * The avatar is [AVATAR_OUTER_SIZE] wide and sits centred in the
 * [AVATAR_BOX_SIZE] box, so its right edge is at
 * `(132 - 118) / 2 = 7dp` from the box's right side. The badge is
 * anchored to the box's bottom-right corner and offset inward by this
 * amount, which places roughly half of it over the avatar's rim.
 */
private val AVATAR_BADGE_OVERHANG = 10.dp

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
