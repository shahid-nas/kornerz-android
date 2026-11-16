package com.kornerz.feature.profile.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.kornerz.core.ui.device.AppVersionInfo
import com.kornerz.core.ui.theme.Satoshi
import com.kornerz.core.ui.theme.Spacing

/**
 * The footer of the Profile screen that shows the installed app's
 * version and build number.
 *
 * Most apps surface this pair at the bottom of the settings/profile
 * screen in very small, muted type so it is present but unobtrusive;
 * this component follows that convention. The iOS app keeps the same
 * values in its `AppInfo` helper but never renders them on the profile
 * screen, so this row is Android-only.
 *
 * The values come from the platform's `PackageManager` via
 * [AppVersionInfo] rather than from `BuildConfig`: the version is
 * declared once in the `app` module, and `feature` modules cannot
 * read another module's `BuildConfig`.
 */
@Composable
fun AppVersionFooter(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val versionName = remember(context) { AppVersionInfo.versionName(context) }
    val buildNumber = remember(context) { AppVersionInfo.buildNumber(context) }
    Text(
        text = "Version $versionName · Build $buildNumber",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontFamily = Satoshi,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.space8),
    )
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun AppVersionFooterPreview() {
    AppVersionFooter()
}
