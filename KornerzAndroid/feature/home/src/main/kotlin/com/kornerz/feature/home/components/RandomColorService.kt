package com.kornerz.feature.home.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.kornerz.core.ui.theme.KornerzColors

internal val LoopAccentPalette: List<Color> = listOf(
    KornerzColors.CoralLight,
    KornerzColors.PlumLight,
    KornerzColors.SageLight,
    KornerzColors.AquaLight,
    KornerzColors.SaffronLight,
)

@Composable
@ReadOnlyComposable
fun loopAccentColor(index: Int): Color =
    LoopAccentPalette[index.mod(LoopAccentPalette.size)].copy(alpha = 1f)
