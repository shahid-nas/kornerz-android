package com.kornerz.feature.videocall

import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.CallSession
import com.opentok.android.Publisher
import com.opentok.android.Subscriber

/**
 * UiState for the video call screen (architecture §2.2).
 *
 * - [callStatus] wraps the live [CallSession] aggregate in
 *   [AsyncValue] so the screen renders Loading / Success / Error
 *   the same way every other screen in the app does.
 * - [isVideoOn] / [isAudioOn] / [isHandRaised] mirror the iOS
 *   `VideoCallViewModel` toggle flags.
 * - [communityName] is the title shown in the top toolbar, mirroring
 *   iOS `VideoCallViewModel.community.communityName`.
 * - [publisher] / [participants] carry the live SDK handles the UI
 *   needs to render the local self-view and the remote video surfaces.
 *   iOS keeps the same handles on its view model (`publisher`,
 *   `subscribers`).
 */
data class VideoCallState(
    val callStatus: AsyncValue<CallSession> = AsyncValue.Loading,
    val communityName: String? = null,
    val localThumbnailUrl: String? = null,
    val isVideoOn: Boolean = true,
    val isAudioOn: Boolean = true,
    val isHandRaised: Boolean = false,
    val publisher: Publisher? = null,
    val participants: List<VideoCallParticipant> = emptyList(),
)

/**
 * One remote participant as rendered by the call grid.
 *
 * Mirrors iOS `SubscriberModel` (`kornerz/Kornerz/HomeVC/OpenTokMeeting/
 * CommunityMeetingVC/SubscriberModel.swift`): the published identity
 * parsed from the stream name, the video-enabled flag, the raised-hand
 * flag, and the SDK subscriber that carries the video surface.
 */
data class VideoCallParticipant(
    val streamId: String,
    val userName: String,
    val thumbnailUrl: String?,
    val hasVideo: Boolean,
    val raisedHand: Boolean,
    val subscriber: Subscriber? = null,
) {
    companion object {
        /** Preview-only factory — no SDK subscriber is attached. */
        fun preview(
            userName: String,
            hasVideo: Boolean = false,
            raisedHand: Boolean = false,
            thumbnailUrl: String? = null,
        ): VideoCallParticipant = VideoCallParticipant(
            streamId = "preview",
            userName = userName,
            thumbnailUrl = thumbnailUrl,
            hasVideo = hasVideo,
            raisedHand = raisedHand,
            subscriber = null,
        )
    }
}
