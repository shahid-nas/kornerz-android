package com.kornerz.feature.profile.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A single 73-pt-tall menu row on the Profile screen. Mirrors iOS
 * `ProfileMenuCell` (P6, task 5.5).
 *
 * [trailingText] (added in P8) renders an optional right-aligned,
 * vertically-centered label at the trailing edge with a 16-pt inset
 * mirroring the row's leading inset. It carries the Premium row's current
 * plan state ("Premium" / "Monthly" / "Yearly" / "Active until {date}").
 * Defaults to `null`, so the six pre-existing call sites compile and
 * render exactly as they did before P8 — this is the first shared-component
 * signature change P0–P7 has required, and it is deliberately additive.
 */
@Composable
fun ProfileMenuRow(
    iconRes: Int,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingText: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(73.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFF2F4F6))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = Color(0xFF353446),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = title,
            color = Color(0xFF353446),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        if (trailingText != null) {
            Spacer(Modifier.weight(1f))
            Text(
                text = trailingText,
                color = Color(0xFF75748B),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileMenuRowTrailingTextPreview() {
    Column(Modifier.padding(16.dp)) {
        ProfileMenuRow(
            iconRes = android.R.drawable.ic_dialog_info,
            title = "Premium",
            onClick = {},
            trailingText = "Yearly",
        )
        Spacer(Modifier.height(12.dp))
        ProfileMenuRow(
            iconRes = android.R.drawable.ic_dialog_info,
            title = "Notifications",
            onClick = {},
        )
    }
}
