package com.kornerz.feature.nooks.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kornerz.core.ui.theme.Spacing

/**
 * Back-to-Korner row — Satoshi Regular 14sp parent Korner name +
 * trailing chevron. Tap forwards `NookDetailEvent.BackToKornerClicked`
 * to the host (the screen routes the event to
 * `NookDetailNavEvent.NavigateToKornerDetail`).
 *
 * Pinned at the bottom of the scrollable content per spec
 * `nook-detail/spec.md` §8.16.
 */
@Composable
fun BackToKornerRow(
    parentKornerName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.space8, vertical = Spacing.space6),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = parentKornerName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            modifier = Modifier.padding(start = 4.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
