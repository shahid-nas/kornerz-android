package com.kornerz.feature.videocall

/**
 * Side-channel navigation events emitted by [VideoCallViewModel]
 * over `Channel<VideoCallNavEvent>` (architecture §2.4). The shell
 * collects these and routes via `NavController.popBackStack()`.
 */
sealed interface VideoCallNavEvent {
    data object NavigateBack : VideoCallNavEvent
}