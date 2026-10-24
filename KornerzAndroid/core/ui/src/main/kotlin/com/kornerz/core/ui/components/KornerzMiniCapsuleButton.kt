package com.kornerz.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kornerz.core.ui.theme.KornerzColors
import com.kornerz.core.ui.theme.KornerzTheme

/**
 * The small "dark" capsule used inside coloured cards and the Korner
 * detail hero. 1:1 port of iOS `AppTextButton(text:configs: .mini)`
 * styled with `CapsuleButtonStyle(config: .dark)`
 * (`kornerz/Kornerz/Components/AppTextButton.swift` +
 * `kornerz/Kornerz/Components/CapsuleButtonStyle.swift`).
 *
 * iOS geometry:
 *   - label font: Satoshi 12 regular
 *   - capsule padding: `EdgeInsets(h: 16, v: 4)` (`.miniInsets`)
 *   - background: `AppColor.C2E2F3E` — the Charcoal swatch
 *   - foreground: white
 *   - shape: capsule
 *
 * **Why this is a sibling of [KornerzCapsuleButton] rather than a
 * style of it:** `KornerzCapsuleButton` is the full-width 56-dp
 * primary action (plans, success screens). The Korner detail hero and
 * the nook cards need an *inline* capsule with a 12-sp regular label
 * and a 16×4 padding, so the geometry differs in every dimension.
 *
 * Modifier is the last parameter (architecture §4).
 */
@Composable
fun KornerzMiniCapsuleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = KornerzColors.CharcoalLight,
    contentColor: Color = Color.White,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
            ),
            color = contentColor,
        )
    }
}

@Preview(showBackground = true, name = "Mini capsule — light")
@Composable
private fun KornerzMiniCapsuleButtonLightPreview() {
    KornerzTheme(darkTheme = false) {
        KornerzMiniCapsuleButton(text = "Follow", onClick = {})
    }
}

@Preview(showBackground = true, name = "Mini capsule — dark")
@Composable
private fun KornerzMiniCapsuleButtonDarkPreview() {
    KornerzTheme(darkTheme = true) {
        KornerzMiniCapsuleButton(text = "Unfollow", onClick = {})
    }
}
