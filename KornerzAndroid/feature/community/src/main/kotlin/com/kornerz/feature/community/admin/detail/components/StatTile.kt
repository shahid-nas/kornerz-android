package com.kornerz.feature.community.admin.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kornerz.core.ui.theme.Satoshi

/**
 * One tile in the `CommunityDetailAsAdmin` stats grid. Mirrors the
 * seven `community*StatView` cards on iOS `CommunityDetailAsAdminVC`
 * (XIB lines 184-482) plus the `pendingEventRequestView` at line 190:
 *
 *  - 86-dp fixed height (matches iOS `<constraint firstAttribute="height"
 *    constant="86">` on every tile view)
 *  - 20-dp rounded corners (iOS `viewDidLayoutSubviews()` lines
 *    284-290 sets `layer.cornerRadius = 20.0` on all seven stat views,
 *    and line 303 sets the same on `pendingEventRequestView`)
 *  - `SmokeLight` (`CF2F4F6`) background - the XIB assigns
 *    `red="0.9490196078" green="0.9568627451" blue="0.9647058824"`
 *    to every tile view, which is `#F2F4F6`. This is deliberately
 *    one step darker than the page (white) so the tile reads as a card.
 *  - 16-dp inner padding (left + top), 8-dp label/value gap (iOS
 *    `<constraint firstItem label/value top constant="16">`)
 *  - `PurpleGreyLight` label - iOS `setupUI()` assigns
 *    `AppColor.C9E9EAE` to every `community*StatTextLabel`; per the
 *    `Config.swift` header note ("Replace first C character with # to
 *    get colour RGB values") that is `#9E9EAE`
 *  - `InkLight` (`C1B1A23` → `#1B1A23`) Satoshi Bold 20-sp value
 *  - Trailing chevron "›" anchored bottom-end. iOS colors the chevron
 *    label `red="0.6196" green="0.6196" blue="0.6824"` = `#9E9EAE`
 *    (the same `PurpleGreyLight` token as the label).
 */
@Composable
fun StatTile(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(86.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFFF2F4F6))
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                style = TextStyle(
                    fontFamily = Satoshi,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFF9E9EAE),
                ),
            )
            Text(
                text = value.toString(),
                style = TextStyle(
                    fontFamily = Satoshi,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color(0xFF1B1A23),
                ),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Text(
            text = "›",
            style = TextStyle(
                fontFamily = Satoshi,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                color = Color(0xFF9E9EAE),
            ),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 8.dp, bottom = 8.dp),
        )
    }
}
