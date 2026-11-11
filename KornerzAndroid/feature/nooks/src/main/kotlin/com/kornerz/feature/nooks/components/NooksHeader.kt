package com.kornerz.feature.nooks.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kornerz.core.ui.R
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
 * iOS passes the `nook-icon` artwork as the leading image, so the
 * tab's header carries the same badge-and-glyph mark the Home tab's
 * "Upcoming Nooks" and the Korner detail's "Nooks" section use. Use
 * the `leadingIconRes` overload for that artwork — see its KDoc for
 * why it must not be painted through `Icon`.
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
    NooksHeaderRow(
        title = title,
        selectedType = selectedType,
        onSelect = onSelect,
        modifier = modifier,
        iconContent = leadingIcon?.let { icon ->
            {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(NooksHeaderIconSize),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        },
    )
}

/**
 * Drawable-resource overload, matching iOS `TabHeaderView(image:
 * .init("nook-icon"), ...)` (`NooksTabView.swift:51`).
 *
 * The iOS artwork is multi-colour (a Sand `#EBE1D3` badge behind a
 * Charcoal `#2E2F3E` glyph), so it is painted with `Image`, **not**
 * `Icon`. `Icon` applies a `ColorFilter` tint across the whole painted
 * output — defaulting to `LocalContentColor` — which collapses the
 * two-colour artwork into a single flat colour and turns the pale Sand
 * badge into a solid dark blob. `Image` never tints.
 */
@Composable
fun NooksHeader(
    title: String,
    selectedType: NooksType,
    onSelect: (NooksType) -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes leadingIconRes: Int,
) {
    NooksHeaderRow(
        title = title,
        selectedType = selectedType,
        onSelect = onSelect,
        modifier = modifier,
        iconContent = {
            Image(
                painter = painterResource(id = leadingIconRes),
                contentDescription = null,
                modifier = Modifier.size(NooksHeaderIconSize),
            )
        },
    )
}

@Composable
private fun NooksHeaderRow(
    title: String,
    selectedType: NooksType,
    onSelect: (NooksType) -> Unit,
    modifier: Modifier = Modifier,
    iconContent: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        if (iconContent != null) {
            iconContent()
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

/** iOS `TabHeaderView.titleContent` renders the icon in a 35x35 frame. */
private val NooksHeaderIconSize = 35.dp

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
            leadingIconRes = R.drawable.ic_home_section_nook,
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
            leadingIconRes = R.drawable.ic_home_section_nook,
        )
    }
}
