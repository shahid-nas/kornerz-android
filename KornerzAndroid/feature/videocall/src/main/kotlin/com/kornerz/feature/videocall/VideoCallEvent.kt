package com.kornerz.feature.videocall

/**
 * UiEvent for the video call screen (architecture §2.1).
 */
sealed interface VideoCallEvent {
    data object ToggleAudio : VideoCallEvent
    data object ToggleVideo : VideoCallEvent
    data object FlipCamera : VideoCallEvent
    data object ToggleHandRaised : VideoCallEvent
    data object EndCall : VideoCallEvent
    data object Retry : VideoCallEvent
}