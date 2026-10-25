package com.kornerz.feature.videocall.ui

import androidx.compose.ui.graphics.Color

/**
 * Colour tokens for the video call screen, transcribed 1:1 from the iOS
 * `AppColor` palette (`kornerz/Kornerz/Helper/Config.swift`).
 *
 * The iOS names carry a leading `C` in place of the `#` marker, so
 * `AppColor.CE27D5A` is the hex `#E27D5A`. Every value below is the
 * byte-for-byte colour the iOS call screen paints with — the Android
 * call screen must not substitute a Material default for any of them,
 * otherwise the two apps stop looking like the same product.
 */
internal object VideoCallPalette {

    /**
     * `AppColor.CE27D5A` (#E27D5A) — the call screen background.
     * iOS `VideoCallView.body` applies it to the root `VStack`.
     */
    val Background = Color(0xFFE27D5A)

    /**
     * `AppColor.C2E2F3E` (#2E2F3E) — the tile behind the local
     * publisher placeholder and the "selected" fill of a toggled
     * control.
     */
    val Tile = Color(0xFF2E2F3E)

    /**
     * `AppColor.CDFE9FC` (#DFE9FC) — the foreground of the bordered
     * call controls and the fill of the "light" (end call) control.
     */
    val Control = Color(0xFFDFE9FC)

    /**
     * `AppColor.CFDF0E0` (#FDF0E0) — the foreground of a control that is
     * in its "selected" state (mic muted, camera off, hand raised).
     */
    val SelectedControl = Color(0xFFFDF0E0)

    /** Pure white — subscriber name labels and loading copy. */
    val OnBackground = Color.White

    /** Pure black — the letterbox behind a subscriber video surface. */
    val VideoBackdrop = Color.Black
}
