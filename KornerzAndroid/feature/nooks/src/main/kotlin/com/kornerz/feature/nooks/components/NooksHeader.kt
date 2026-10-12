package com.kornerz.feature.nooks.components

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
 *  - title + leading icon
 *  - right-aligned Live/Upcoming toggle with an underline on the
 *    selected tab (the iOS underline color is `AppColor.CEBE1D3`).
 *
 * Tapping a tab forwards [onSelect] with the new [NooksType] — the
 * screen-level VM updates `state.selectedType` and
 * [com.kornerz.feature.nooks.list.NooksContent] re-renders.
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
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

@Composable
private fun NooksTypeToggle(
    selected: NooksType,
    onSelect: (NooksType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Top,
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
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
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
                .height(2.dp)
                .fillMaxWidth()
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
            title = "Nooks",
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
            title = "Nooks",
            selectedType = NooksType.Upcoming,
            onSelect = {},
        )
    }
}
