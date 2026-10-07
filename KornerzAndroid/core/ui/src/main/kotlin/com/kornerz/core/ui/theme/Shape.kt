package com.kornerz.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material 3 shape tokens. Components in P1+ consume these via
 * `MaterialTheme.shapes.X` rather than instantiating `RoundedCornerShape`
 * inline.
 */
val KornerzShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
)