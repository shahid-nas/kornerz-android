package com.kornerz.core.ui.components.header

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kornerz.core.ui.R
import com.kornerz.core.ui.theme.KornerzColors
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Spacing

/**
 * Section title row with a trailing tab strip. 1:1 port of iOS
 * `SwiftUI/Dashboard/Korner/Sections/TabHeaderView.swift`.
 *
 * iOS layout:
 * ```
 * HStack(alignment: .bottom) {
 *     HStack(alignment: .center) { [image 35x35]; Text(title, lato 20 bold) }
 *     Spacer()
 *     HStack(alignment: .top, spacing: 12) {
 *         ForEach(tab) { VStack { Text(tab.title, lato 12 regular)
 *                                 if selected { Rectangle().frame(height: 2) } } }
 *     }
 * }
 * .padding(.horizontal)
 * ```
 *
 * The selected tab is marked with a 2-dp underline in the iOS "sand"
 * swatch (`AppColor.CEBE1D3`) — the same token the Nooks tab's
 * Live/Upcoming toggle uses, so the two surfaces read identically.
 *
 * The underline spans the width of the tab label, which is what the
 * iOS `Rectangle()` does inside a `VStack` whose only other child is
 * the label. Reproducing that on Compose needs the label's own width:
 * a bare `fillMaxWidth()` inside the tab's `Column` resolves against
 * the *row's* remaining width, not the label's, so the underline would
 * stretch to the screen edge and push the remaining tabs off-screen
 * (their labels are then measured at zero width and wrap into a tall
 * stack, which in turn drags the section title out of alignment).
 * `width(IntrinsicSize.Min)` sizes the tab column to the label's
 * intrinsic width, so `fillMaxWidth()` inside it fills exactly the
 * label — iOS behaviour, and the tabs stay on screen.
 *
 * Modifier is the last parameter (architecture §4).
 *
 * The iOS artwork is multi-colour (a Sand `#EBE1D3` badge behind a
 * Charcoal `#2E2F3E` glyph), so it is supplied via the `@DrawableRes`
 * overload and painted with `Image`, **not** `Icon`. `Icon` applies a
 * `ColorFilter` tint across the whole painted output — defaulting to
 * `LocalContentColor` — which collapses the two-colour artwork into a
 * single flat colour and turns the pale Sand badge into a solid dark
 * blob. `Image` never tints.
 */
@Composable
fun KornerzSectionTabHeader(
    title: String,
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionTabHeaderRow(
        title = title,
        tabs = tabs,
        selectedTabIndex = selectedTabIndex,
        onTabSelected = onTabSelected,
        modifier = modifier,
        iconContent = null,
    )
}

@Composable
fun KornerzSectionTabHeader(
    title: String,
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes iconRes: Int,
) {
    SectionTabHeaderRow(
        title = title,
        tabs = tabs,
        selectedTabIndex = selectedTabIndex,
        onTabSelected = onTabSelected,
        modifier = modifier,
        iconContent = {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(SectionTabHeaderIconSize),
            )
        },
    )
}

@Composable
fun KornerzSectionTabHeader(
    title: String,
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector,
) {
    SectionTabHeaderRow(
        title = title,
        tabs = tabs,
        selectedTabIndex = selectedTabIndex,
        onTabSelected = onTabSelected,
        modifier = modifier,
        iconContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(SectionTabHeaderIconSize),
            )
        },
    )
}

@Composable
private fun SectionTabHeaderRow(
    title: String,
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    iconContent: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.space8),
        verticalAlignment = Alignment.Bottom,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (iconContent != null) {
                iconContent()
                Spacer(modifier = Modifier.width(Spacing.space2))
            }
            Text(
                text = title,
                // iOS `lato(20, weight: .bold)` → Satoshi Bold 20sp.
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            tabs.forEachIndexed { index, tab ->
                Column(
                    modifier = Modifier
                        // The tab is only as wide as its label, so the
                        // underline below it matches the label and the
                        // strip never overflows the header row.
                        .width(IntrinsicSize.Min)
                        .clickable { onTabSelected(index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = tab,
                        // iOS `lato(12, weight: .regular)` → Satoshi Regular 12sp.
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    if (index == selectedTabIndex) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(KornerzColors.SandLight),
                        )
                    }
                }
            }
        }
    }
}

private val SectionTabHeaderIconSize = 35.dp

@Preview(showBackground = true, name = "Section tab header — light")
@Composable
private fun KornerzSectionTabHeaderLightPreview() {
    KornerzTheme(darkTheme = false) {
        KornerzSectionTabHeader(
            title = "Nooks",
            tabs = listOf("Current", "Upcoming"),
            selectedTabIndex = 0,
            onTabSelected = {},
            modifier = Modifier.padding(vertical = Spacing.space8),
            iconRes = R.drawable.ic_home_section_nook,
        )
    }
}

@Preview(showBackground = true, name = "Section tab header — dark")
@Composable
private fun KornerzSectionTabHeaderDarkPreview() {
    KornerzTheme(darkTheme = true) {
        KornerzSectionTabHeader(
            title = "Kornerstones",
            tabs = listOf("Kornerstones", "Klouders"),
            selectedTabIndex = 1,
            onTabSelected = {},
            modifier = Modifier.padding(vertical = Spacing.space8),
            iconRes = R.drawable.ic_kornerstones,
        )
    }
}
