package com.kornerz.feature.nooks.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.kornerz.core.ui.theme.KornerzColors

/**
 * Per-index accent palette for the Nooks tab list cards. Mirrors
 * the iOS `RandomColorService.color(for: index)` in
 * `NooksTabViewModel.swift:142` — same five-color rotation.
 */
private val NooksAccentPalette: List<Color> = listOf(
    KornerzColors.CoralLight,
    KornerzColors.PlumLight,
    KornerzColors.SageLight,
    KornerzColors.AquaLight,
    KornerzColors.SaffronLight,
)

@Composable
@ReadOnlyComposable
fun nooksAccentColor(index: Int): Color =
    NooksAccentPalette[index.mod(NooksAccentPalette.size)].copy(alpha = 1f)
