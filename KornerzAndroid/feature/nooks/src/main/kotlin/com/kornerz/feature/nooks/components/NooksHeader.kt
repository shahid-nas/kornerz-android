package com.kornerz.feature.nooks.components

import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kornerz.core.ui.theme.KornerzColors
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.feature.nooks.list.NooksType

/**
 * Header for the Nooks tab list. 1:1 port of iOS `TabHeaderView.swift`
 * (`SwiftUI/Dashboard/Korner/Sections/TabHeaderView.swift`):
 *  - title (iOS passes the selected `NooksType.titleNooks` so the
 *    header reads "Live Nooks" or "Upcoming Nooks" — `nooksTypeTitle`
 *    below is the Android mirror) + leading icon
 *  - right-aligned Live/Upcoming toggle with an underline on the
 *    selected tab (the iOS underline color is `AppColor.CEBE1D3`).
 *
 * Tapping a tab forwards [onSelect] with the new [NooksType] — the
 * screen-level VM updates `state.selectedType` and
 * [com.kornerz.feature.nooks.list.NooksContent] re-renders.
 *
 * **Padding note:** iOS uses `.padding(.horizontal)` only
 * (`TabHeaderView.body` line 25). The previous Android port had
 * `.padding(horizontal = 16.dp, vertical = 8.dp)` which added 8-dp of
 * top padding on top of the shared `TabHeaderContent` above it —
 * that 8-dp gap was the duplicated top spacing reported against the
 * Nooks tab. The fix removes the vertical padding and aligns the
 * row to `Alignment.Bottom` (mirrors iOS `HStack(alignment: .bottom)`).
 */
@Composable
fun NooksHeader(
    title: String,
    selectedType: NooksType,
    onSelect: (NooksType) -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.size(8.dp))
        }
        Text(
            text = title,
            // iOS `TabHeaderView` uses Lato(20, weight: .bold).
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        NooksTypeToggle(selected = selectedType, onSelect = onSelect)
    }
}

/**
 * Mirrors iOS `TabHeaderView.tabView`: `HStack(alignment: .top,
 * spacing: 12) { VStack { Text; Rectangle(2pt) } }`. The 2-pt
 * underline appears only on the selected tab, in `AppColor.CEBE1D3`
 * (mirrored by [KornerzColors.SandLight]).
 */
@Composable
private fun NooksTypeToggle(
    selected: NooksType,
    onSelect: (NooksType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NooksType.values().forEach { type ->
            NooksTypeTab(
                label = type.title,
                isSelected = type == selected,
                onClick = { onSelect(type) },
            )
        }
    }
}

@Composable
private fun NooksTypeTab(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            // iOS `TabHeaderView` uses Lato(12, weight: .regular) for
            // both selected and unselected states; selection is
            // indicated by the underline only.
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) MaterialTheme.colorScheme.onBackground
                else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(2.dp)
                .background(
                    // iOS uses AppColor.CEBE1D3 (#EBE1D3 — sand). The
                    // M3 `primary` (purple) was the previous default
                    // and reads off-style on the smoke/ink surface.
                    if (isSelected) KornerzColors.SandLight else Color.Transparent,
                ),
        )
    }
}

@Preview(showBackground = true, name = "NooksHeader — Live")
@Composable
private fun NooksHeaderLivePreview() {
    KornerzTheme(darkTheme = false) {
        NooksHeader(
            title = NooksType.Live.titleNooks,
            selectedType = NooksType.Live,
            onSelect = {},
        )
    }
}

@Preview(showBackground = true, name = "NooksHeader — Upcoming")
@Composable
private fun NooksHeaderUpcomingPreview() {
    KornerzTheme(darkTheme = false) {
        NooksHeader(
            title = NooksType.Upcoming.titleNooks,
            selectedType = NooksType.Upcoming,
            onSelect = {},
        )
    }
}
