package com.kornerz.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Kornerz brand palette mapped 1:1 from the iOS `AppColor.CXXXXXX`
 * tokens in `kornerz/Kornerz/Helper/Config.swift`. The leading `C` in
 * each iOS name is the hex `#` marker; stripping it yields the 6-digit
 * hex used here, prefixed with `0xFF` for full opacity in Compose.
 *
 * Hex values were transcribed from the iOS source rather than
 * approximated from the Swift `#colorLiteral` RGB triples, so the
 * Android palette is byte-for-byte identical to iOS in P0. Dark-mode
 * variants are best-guess Material 3 mappings (TBD where iOS does not
 * define an explicit dark value).
 */
internal object KornerzPalette {

    // Primary brand purple
    val PurpleLight = Color(0xFF5D47ED)
    val PurpleDark = Color(0xFF8B7BFF)

    // Neutrals
    val SlateLight = Color(0xFF353446)
    val SlateDark = Color(0xFFE3E1F0)

    val PurpleGreyLight = Color(0xFF9E9EAE)
    val PurpleGreyDark = Color(0xFF6E6E7E)

    val InkLight = Color(0xFF1B1A23)
    val InkDark = Color(0xFFEFEDF5)

    val MistLight = Color(0xFFE5E5EA)
    val MistDark = Color(0xFF2A2A33)

    val SmokeLight = Color(0xFFF2F4F6)
    val SmokeDark = Color(0xFF1F1F25)

    val GraphiteLight = Color(0xFF75748B)
    val GraphiteDark = Color(0xFF8E8DA0)

    // Purple tints
    val PurpleSoftLight = Color(0xFFCEC8FA)
    val PurpleSoftDark = Color(0xFF3D3268)

    val PurpleWashLight = Color(0xFFEFEDFD)
    val PurpleWashDark = Color(0xFF2B2547)

    // Accents
    val RedLight = Color(0xFFF50041)
    val RedDark = Color(0xFFFF6E8A)

    val DeepBlueLight = Color(0xFF343651)
    val DeepBlueDark = Color(0xFFA9ABC2)

    // Communities gradient palette
    val SkyLight = Color(0xFFD2EBFA)
    val SkyDark = Color(0xFF1F3142)

    val SkyMidLight = Color(0xFFA3D6F5)
    val SkyMidDark = Color(0xFF243E4F)

    val LilacLight = Color(0xFFEAE8FD)
    val LilacDark = Color(0xFF2F2A55)

    val PeriwinkleLight = Color(0xFFC8C3FC)
    val PeriwinkleDark = Color(0xFF312D54)

    val PinkLight = Color(0xFFF5E1ED)
    val PinkDark = Color(0xFF4A2A3A)

    val PinkHotLight = Color(0xFFFEABDE)
    val PinkHotDark = Color(0xFF6B2D4A)

    val PeachLight = Color(0xFFFDF0E0)
    val PeachDark = Color(0xFF4D3D2A)

    val MagentaLight = Color(0xFF83165B)
    val MagentaDark = Color(0xFFE36CB1)

    val AmberLight = Color(0xFF7D4401)
    val AmberDark = Color(0xFFFFC97A)

    val IndigoLight = Color(0xFF39309C)
    val IndigoDark = Color(0xFF9A92FF)

    val TealLight = Color(0xFF055B8E)
    val TealDark = Color(0xFF7BC2EE)

    val VioletLight = Color(0xFFAEA3F6)
    val VioletDark = Color(0xFF3D3470)

    val CornflowerLight = Color(0xFFBCCFF3)
    val CornflowerDark = Color(0xFF33416B)

    val IceLight = Color(0xFFDFE7F7)
    val IceDark = Color(0xFF2A3552)

    val PowderLight = Color(0xFFF3F6FB)
    val PowderDark = Color(0xFF222A3A)

    val CloudLight = Color(0xFFDFE9FC)
    val CloudDark = Color(0xFF283454)

    val CoralLight = Color(0xFFE27D5A)
    val CoralDark = Color(0xFFFFA888)

    val CharcoalLight = Color(0xFF2E2F3E)
    val CharcoalDark = Color(0xFFD0CFE0)

    val PlumLight = Color(0xFFAD598D)
    val PlumDark = Color(0xFFE9A8CC)

    val SageLight = Color(0xFF86BFA9)
    val SageDark = Color(0xFF4F7A6B)

    val SandLight = Color(0xFFEBE1D3)
    val SandDark = Color(0xFF56503F)

    val AquaLight = Color(0xFF68B6CC)
    val AquaDark = Color(0xFFA4DCEA)

    val SaffronLight = Color(0xFFEBAA4A)
    val SaffronDark = Color(0xFFFFD08A)
}

/**
 * Public name aliases for the brand palette. Use these inside
 * `core/ui/theme/` only; P1+ code must consume colors via
 * `MaterialTheme.colorScheme.X`, never via these constants directly.
 */
object KornerzColors {
    val PurpleLight get() = KornerzPalette.PurpleLight
    val PurpleDark get() = KornerzPalette.PurpleDark
    val SlateLight get() = KornerzPalette.SlateLight
    val SlateDark get() = KornerzPalette.SlateDark
    val PurpleGreyLight get() = KornerzPalette.PurpleGreyLight
    val PurpleGreyDark get() = KornerzPalette.PurpleGreyDark
    val InkLight get() = KornerzPalette.InkLight
    val InkDark get() = KornerzPalette.InkDark
    val SmokeLight get() = KornerzPalette.SmokeLight
    val SmokeDark get() = KornerzPalette.SmokeDark
    val MistLight get() = KornerzPalette.MistLight
    val RedLight get() = KornerzPalette.RedLight
    val RedDark get() = KornerzPalette.RedDark
    val CoralLight get() = KornerzPalette.CoralLight
    val PlumLight get() = KornerzPalette.PlumLight
    val SageLight get() = KornerzPalette.SageLight
    val AquaLight get() = KornerzPalette.AquaLight
    val SaffronLight get() = KornerzPalette.SaffronLight
    // Sand mirrors iOS `AppColor.CEBE1D3`; used by the nooks tab
    // Live/Upcoming toggle underline.
    val SandLight get() = KornerzPalette.SandLight
    val CharcoalLight get() = KornerzPalette.CharcoalLight
    val CharcoalDark get() = KornerzPalette.CharcoalDark
}