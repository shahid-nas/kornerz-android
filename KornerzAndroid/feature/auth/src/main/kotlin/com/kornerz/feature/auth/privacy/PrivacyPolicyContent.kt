package com.kornerz.feature.auth.privacy

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Satoshi
import com.kornerz.feature.auth.R
import com.kornerz.feature.auth.recovery.RecoveryBackChevronButton

/**
 * Stateless renderer for the standalone Privacy Policy screen. Mirrors
 * the visual design of iOS `PrivacyPolicyView`
 * (kornerz/Kornerz/SwiftUI/OnBoarding/Signup/Views/PrivacyPolicyView.swift)
 * 1:1: Aqua #68B6CC background, a compact fixed-height top header
 * built as a ZStack-style [Box] — exactly mirroring iOS's
 * `ZStack { CloudHeaderView(); Text("...") }` — with the
 * `bg-clouds-light` decoration as the back layer (matching the rest of
 * the Android auth palette) and a centered Satoshi "Privacy Policy"
 * label as the front layer, bottom-aligned inside the header at the
 * same 24sp / Medium weight as iOS. The bundled English policy PDF is
 * rendered via [PdfViewer] in the remaining vertical space with a 16dp
 * bottom gap above the CTA, and a white-on-black capsule CTA reading
 * "I have read and accepted" is pinned to the bottom with 32dp
 * horizontal and bottom insets.
 *
 * The Android project does not bundle Lato, so the title uses Satoshi
 * Medium 24sp — the closest bundled weight that matches iOS's
 * `.lato(24, weight: .medium)` style.
 *
 * The Android-side addition over the iOS screen is a scroll-to-end gate
 * that keeps the CTA disabled until the user reaches the bottom of the
 * PDF (per `phase-1-auth-onboarding` §13). Returning to Welcome is
 * handled by the system back gesture / nav up button.
 */
@Composable
fun PrivacyPolicyContent(
    state: PrivacyPolicyState,
    onEvent: (PrivacyPolicyEvent) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val pdfScrollState = rememberScrollState()
    val reachedEnd by remember {
        derivedStateOf {
            pdfScrollState.value >= pdfScrollState.maxValue - 4
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { reachedEnd }
            .collect { if (it) onEvent(PrivacyPolicyEvent.ScrolledToEndChanged(true)) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Aqua)
            .navigationBarsPadding(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Image(
                painter = painterResource(id = R.drawable.bg_clouds_light),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Crop,
            )
            Text(
                text = "Privacy Policy",
                color = Color.White,
                fontFamily = Satoshi,
                fontWeight = FontWeight.Medium,
                fontSize = 24.sp,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            if (onBack != null) {
                RecoveryBackChevronButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.TopStart),
                    tint = Color.White,
                )
            }
        }

        PdfViewer(
            assetFileName = "Privacy-Police-en.pdf",
            scrollState = pdfScrollState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = 16.dp),
        )

        CapsuleWhiteOnBlackButton(
            text = "I have read and accepted",
            onClick = { onEvent(PrivacyPolicyEvent.Accept) },
            enabled = state.canAccept,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 32.dp, end = 32.dp, bottom = 32.dp),
        )
    }
}

private val Aqua = Color(0xFF68B6CC)

@Composable
private fun CapsuleWhiteOnBlackButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val bg = if (enabled) Color.Black else Color(0x33000000)
    val fg = if (enabled) Color.White else Color.White.copy(alpha = 0.6f)
    Box(
        modifier = modifier
            .height(52.dp)
            .background(bg, shape = RoundedCornerShape(percent = 50))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = fg,
            fontFamily = Satoshi,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
        )
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun PrivacyPolicyContentPreview() {
    KornerzTheme(darkTheme = false) {
        PrivacyPolicyContent(state = PrivacyPolicyState(hasScrolledToEnd = true), onEvent = {})
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun PrivacyPolicyContentDarkPreview() {
    KornerzTheme(darkTheme = false) {
        PrivacyPolicyContent(state = PrivacyPolicyState(hasScrolledToEnd = false), onEvent = {})
    }
}
