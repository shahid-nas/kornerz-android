package com.kornerz.feature.videocall

import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.CallSession

/**
 * UiState for the video call screen (architecture §2.2).
 *
 * - [callStatus] wraps the live [CallSession] aggregate in
 *   [AsyncValue] so the screen renders Loading / Success / Error
 *   the same way every other screen in the app does.
 * - [isVideoOn] / [isAudioOn] / [isHandRaised] mirror the iOS
 *   `VideoCallViewModel` toggle flags. Hand-raised is a no-op
 *   affordance this phase (design D4).
 */
data class VideoCallState(
    val callStatus: AsyncValue<CallSession> = AsyncValue.Loading,
    val isVideoOn: Boolean = true,
    val isAudioOn: Boolean = true,
    val isHandRaised: Boolean = false,
)