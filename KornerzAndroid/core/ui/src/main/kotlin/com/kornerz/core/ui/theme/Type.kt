package com.kornerz.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.kornerz.core.ui.R

/**
 * Bundled Satoshi font family. 10 weights mirrored from the iOS app.
 * Italic variants are declared alongside the upright weights so a
 * component can opt into italic styling without redefining the family.
 */
val Satoshi = FontFamily(
    Font(R.font.satoshi_light, FontWeight.Light, FontStyle.Normal),
    Font(R.font.satoshi_lightitalic, FontWeight.Light, FontStyle.Italic),
    Font(R.font.satoshi_regular, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.satoshi_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.satoshi_medium, FontWeight.Medium, FontStyle.Normal),
    Font(R.font.satoshi_mediumitalic, FontWeight.Medium, FontStyle.Italic),
    Font(R.font.satoshi_bold, FontWeight.Bold, FontStyle.Normal),
    Font(R.font.satoshi_bolditalic, FontWeight.Bold, FontStyle.Italic),
    Font(R.font.satoshi_black, FontWeight.Black, FontStyle.Normal),
    Font(R.font.satoshi_blackitalic, FontWeight.Black, FontStyle.Italic),
)

/**
 * Material 3 typography scale bound to the Satoshi family. Sizes follow
 * the M3 default scale; weights use the closest Satoshi weight that
 * matches the M3 role (regular text → Regular, emphasized text → Medium
 * or Bold, large display text → Black where the iOS app uses Satoshi
 * Black for hero copy).
 */
val KornerzTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Satoshi,
        fontWeight = FontWeight.Black,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = Satoshi,
        fontWeight = FontWeight.Black,
        fontSize = 45.sp,
        lineHeight = 52.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = Satoshi,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = Satoshi,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Satoshi,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Satoshi,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Satoshi,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Satoshi,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Satoshi,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Satoshi,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Satoshi,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Satoshi,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Satoshi,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Satoshi,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Satoshi,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)