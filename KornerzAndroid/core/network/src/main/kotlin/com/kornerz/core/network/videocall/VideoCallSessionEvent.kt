package com.kornerz.core.network.videocall

/**
 * Session-level events raised by the Vonage SDK that the call screen
 * has to react to.
 *
 * iOS surfaces both of these through `OTSessionDelegate`
 * (`kornerz/Kornerz/SwiftUI/VideoCall/VideoCallViewModel.swift`):
 *
 *  - `session(_:didFailWithError:)` — the session died; iOS prints
 *    the error today, but `sessionDidConnect` publishes with an error
 *    check that flips `loadingState` to `.failed`.
 *  - `sessionDidDisconnect(_:)` — iOS calls `onClose?()`, which
 *    dismisses the hosting view controller.
 *
 * The Android SDK reports the same conditions through
 * `Session.SessionListener.onError` / `onDisconnected`, plus a
 * publisher-side failure through `PublisherKit.PublisherListener.onError`.
 * Without this channel every one of those failures used to be swallowed
 * and the screen kept showing "Waiting for others to join" for a call
 * that was never going to work.
 */
sealed interface VideoCallSessionEvent {

    /**
     * The session, the publisher or a subscriber failed. The call
     * cannot continue — the view model turns this into
     * `AsyncValue.Error` so the screen renders the error card with a
     * "Try again" action.
     */
    data class Failed(val error: Throwable) : VideoCallSessionEvent

    /**
     * The remote end dropped the session (network loss, the media
     * server closing the room, the other peer ending the call).
     * Mirrors iOS `sessionDidDisconnect(_:)` → `onClose?()`.
     */
    data object Disconnected : VideoCallSessionEvent
}
