package com.kornerz.feature.shell.deeplink

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.kornerz.core.ui.components.KornerzLoadingView
import com.kornerz.core.ui.theme.KornerzTheme

/**
 * The deep-link interstitial's rendered state (inventory 13.1).
 *
 * A full-screen resolving state: the interstitial exists only while
 * the link is being resolved, and it removes itself from the back
 * stack as soon as it has an answer.
 *
 * It deliberately shows **no** entity-loading affordance: Android's
 * destination screens take an id in their route and load their own
 * data, so the destination's own loading and error states are the
 * single failure surface. Duplicating the fetch here would create two
 * owners of the same load.
 */
@Composable
fun DeepLinkResolvingContent(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        KornerzLoadingView(modifier = Modifier.fillMaxSize())
    }
}

@Preview(showBackground = true)
@Composable
private fun DeepLinkResolvingContentPreview() {
    KornerzTheme(darkTheme = false) {
        DeepLinkResolvingContent()
    }
}
