package com.kornerz.core.network.videocall

/**
 * Project-local camera position enum exposed by [VonageSessionController].
 *
 * The Vonage SDK uses an `int` constant for camera position; we wrap
 * it so the `ViewModel` never touches the SDK directly (arch §2.5).
 */
enum class CameraPosition {
    FRONT,
    BACK,
}