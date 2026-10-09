package com.kornerz.feature.kornerz.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Spacing

/**
 * About section of the Korner detail screen (spec `korner-detail/spec.md`
 * §"About is hidden when description is empty"). Tapping the body
 * toggles between collapsed (`maxLines = 6`, ellipsis) and expanded
 * (full text) states.
 */
@Composable
fun AboutSection(
    description: String,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.space8, vertical = Spacing.space4)) {
        Text(
            text = "About",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = Spacing.space2)
                .clickable { expanded = !expanded },
        )
    }
}

private const val COLLAPSED_LINES = 6

@Preview(showBackground = true)
@Composable
private fun AboutSectionPreview() {
    KornerzTheme(darkTheme = false) {
        AboutSection(description = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. ".repeat(20))
    }
}
