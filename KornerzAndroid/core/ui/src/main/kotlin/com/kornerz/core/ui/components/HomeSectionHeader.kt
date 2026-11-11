package com.kornerz.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.kornerz.core.ui.R
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Spacing

/**
 * Section header used across the Home tab and the Korner detail screen.
 *
 * 1:1 port of iOS `SwiftUI/Dashboard/HomeTab/Sections/HomeSectionHeader.swift`,
 * which renders an optional 35x35 image next to the title. The iOS
 * artwork is multi-colour (a Sand `#EBE1D3` badge behind a Charcoal
 * `#2E2F3E` glyph), so it is supplied here as a drawable resource and
 * painted with `Image`, **not** `Icon`.
 *
 * `Icon` applies a `ColorFilter` tint across the whole painted output —
 * defaulting to `LocalContentColor` — which collapses the two-colour
 * artwork into a single flat colour and turns the pale Sand badge into
 * a solid dark blob. `Image` never tints, so the badge and the glyph
 * keep their own colours.
 *
 * The [icon] overload stays available for single-colour Material icons.
 */
@Composable
fun HomeSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    SectionHeaderRow(modifier = modifier) {
        SectionHeaderTitle(title = title)
    }
}

@Composable
fun HomeSectionHeader(
    title: String,
    @DrawableRes iconRes: Int,
    modifier: Modifier = Modifier,
) {
    SectionHeaderRow(modifier = modifier) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier.size(SectionHeaderIconSize),
        )
        Spacer(Modifier.padding(end = Spacing.space4))
        SectionHeaderTitle(title = title)
    }
}

@Composable
fun HomeSectionHeader(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    SectionHeaderRow(modifier = modifier) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(SectionHeaderIconSize),
        )
        Spacer(Modifier.padding(end = Spacing.space4))
        SectionHeaderTitle(title = title)
    }
}

@Composable
private fun SectionHeaderRow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

@Composable
private fun SectionHeaderTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
        color = MaterialTheme.colorScheme.onBackground,
    )
}

private val SectionHeaderIconSize = 36.dp

@Preview(showBackground = true)
@Composable
private fun HomeSectionHeaderPreview() {
    KornerzTheme(darkTheme = false) {
        HomeSectionHeader(
            title = "Loop of the day",
            iconRes = R.drawable.ic_home_section_loop,
        )
    }
}

@Preview(showBackground = true, name = "dark")
@Composable
private fun HomeSectionHeaderDarkPreview() {
    KornerzTheme(darkTheme = true) {
        HomeSectionHeader(
            title = "Your Kornerz",
            iconRes = R.drawable.ic_home_section_korner,
        )
    }
}
