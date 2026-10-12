package com.kornerz.core.network.videocall

/**
 * Compile-time configuration for the Vonage video calling SDK.
 *
 * The [API_KEY] is shared with the iOS app for the demo
 * (`kornerz/Kornerz/Backend/Services/OpenTokTokenService.swift`).
 * Before any public release the Android app must be rotated to a
 * dedicated Vonage project API key — see the
 * `TODO(android-key-rotation)` marker below and the `tasks.md`
 * checklist item 1.2 / 12.12.
 */
object VonageConfig {
    const val API_KEY: String = "47628671" // TODO(android-key-rotation): rotate to a dedicated Android project before shipping
}