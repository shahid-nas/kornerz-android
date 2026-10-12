package com.kornerz.core.model

/**
 * Aggregate state of an in-flight or active video call.
 *
 * Produced by [com.kornerz.feature.videocall.data.VideoCallRepository]
 * and rendered by `VideoCallContent`. Mirrors the iOS `CallSession`
 * shape used by `SwiftUI/VideoCall/VideoCallViewModel.swift`.
 *
 * NOT `@Serializable` — the video call is ephemeral and the
 * repository does not persist it across process restarts (arch §6).
 */
data class CallSession(
    val sessionId: String,
    val status: String,
    val localPublisherId: String? = null,
    val remoteSubscribers: List<SubscriberSnapshot> = emptyList(),
) {
    companion object {
        const val STATUS_AUTHENTICATING = "Authenticating"
        const val STATUS_CONNECTING = "Connecting"
        const val STATUS_CONNECTED = "Connected"
        const val STATUS_FAILED = "Failed"
    }
}