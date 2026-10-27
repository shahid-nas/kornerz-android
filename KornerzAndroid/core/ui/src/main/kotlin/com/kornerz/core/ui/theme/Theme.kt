package com.kornerz.core.ui.theme

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Material 3 ColorScheme built from the Kornerz palette. Each role is
 * mapped to the iOS `AppColor` token it most closely represents, with
 * separate light/dark variants chosen by the M3 contrast guidelines.
 *
 * P1+ components consume these via `MaterialTheme.colorScheme.X`; they
 * MUST NOT reference the underlying `KornerzPalette` constants directly.
 */
private val LightScheme = lightColorScheme(
    primary = KornerzPalette.PurpleLight,
    onPrimary = Color.White,
    primaryContainer = KornerzPalette.PurpleSoftLight,
    onPrimaryContainer = KornerzPalette.IndigoLight,

    secondary = KornerzPalette.TealLight,
    onSecondary = Color.White,
    secondaryContainer = KornerzPalette.SkyLight,
    onSecondaryContainer = KornerzPalette.IndigoLight,

    tertiary = KornerzPalette.PlumLight,
    onTertiary = Color.White,
    tertiaryContainer = KornerzPalette.PinkLight,
    onTertiaryContainer = KornerzPalette.MagentaLight,

    background = KornerzPalette.SmokeLight,
    onBackground = KornerzPalette.InkLight,

    surface = Color.White,
    onSurface = KornerzPalette.InkLight,
    surfaceVariant = KornerzPalette.MistLight,
    onSurfaceVariant = KornerzPalette.SlateLight,

    error = KornerzPalette.RedLight,
    onError = Color.White,
    errorContainer = KornerzPalette.PinkLight,
    onErrorContainer = KornerzPalette.MagentaLight,

    outline = KornerzPalette.PurpleGreyLight,
    outlineVariant = KornerzPalette.MistLight,
)

private val DarkScheme = darkColorScheme(
    primary = KornerzPalette.PurpleDark,
    onPrimary = KornerzPalette.InkLight,
    primaryContainer = KornerzPalette.PurpleSoftDark,
    onPrimaryContainer = KornerzPalette.PurpleWashLight,

    secondary = KornerzPalette.TealDark,
    onSecondary = KornerzPalette.InkLight,
    secondaryContainer = KornerzPalette.SkyMidDark,
    onSecondaryContainer = KornerzPalette.SkyLight,

    tertiary = KornerzPalette.PlumDark,
    onTertiary = KornerzPalette.InkLight,
    tertiaryContainer = KornerzPalette.MagentaDark,
    onTertiaryContainer = KornerzPalette.PinkLight,

    background = KornerzPalette.CharcoalLight,
    onBackground = KornerzPalette.InkDark,

    surface = KornerzPalette.CharcoalLight,
    onSurface = KornerzPalette.InkDark,
    surfaceVariant = KornerzPalette.CharcoalDark,
    onSurfaceVariant = KornerzPalette.PurpleGreyDark,

    error = KornerzPalette.RedDark,
    onError = KornerzPalette.InkLight,
    errorContainer = KornerzPalette.MagentaDark,
    onErrorContainer = KornerzPalette.PinkLight,

    outline = KornerzPalette.GraphiteDark,
    outlineVariant = KornerzPalette.CharcoalDark,
)

/**
 * Top-level theme entry point. Wrap any P1+ screen in `KornerzTheme`
 * to gain access to brand colors, Satoshi typography, and shape tokens
 * via `MaterialTheme`.
 *
 * The app is light-only for now: the default is [darkTheme] = `false`
 * so the UI does not follow the system setting. Callers can still opt
 * into the dark scheme explicitly by passing `darkTheme = true`.
 */
@Composable
fun KornerzTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkScheme else LightScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = KornerzTypography,
        shapes = KornerzShapes,
        content = content,
    )
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "KornerzTheme — Light",
    showBackground = true,
)
@androidx.compose.runtime.Composable
private fun KornerzThemeLightPreview() {
    KornerzTheme(darkTheme = false) {
        androidx.compose.material3.Surface(
            color = androidx.compose.material3.MaterialTheme.colorScheme.background,
        ) {
            androidx.compose.material3.Text(
                text = "Kornerz",
                style = androidx.compose.material3.MaterialTheme.typography.displayLarge,
                color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                modifier = androidx.compose.ui.Modifier
                    .padding(Spacing.space8),
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "KornerzTheme — Dark",
    showBackground = true,
)
@androidx.compose.runtime.Composable
private fun KornerzThemeDarkPreview() {
    KornerzTheme(darkTheme = true) {
        androidx.compose.material3.Surface(
            color = androidx.compose.material3.MaterialTheme.colorScheme.background,
        ) {
            androidx.compose.material3.Text(
                text = "Kornerz",
                style = androidx.compose.material3.MaterialTheme.typography.displayLarge,
                color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                modifier = androidx.compose.ui.Modifier
                    .padding(Spacing.space8),
            )
        }
    }
}