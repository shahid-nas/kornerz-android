package com.kornerz.core.network.videocall

/**
 * Failure modes for the video-call pipeline. Mirrors the iOS
 * `VideoCallError` enum in `OpenTokTokenService.swift`. The
 * `Repository` / `ViewModel` translate each case into the
 * user-facing copy rendered by `VideoCallLoadingState`.
 */
sealed class VideoCallError : RuntimeException() {
    data object FailedToGetToken : VideoCallError() {
        private fun readResolve(): Any = FailedToGetToken
        override val message: String = "Failed to get Vonage session token"
    }

    data object SocketTimeout : VideoCallError() {
        private fun readResolve(): Any = SocketTimeout
        override val message: String = "Timed out waiting for session token"
    }

    data object SocketFailedToConnect : VideoCallError() {
        private fun readResolve(): Any = SocketFailedToConnect
        override val message: String = "Couldn't connect to the session service"
    }

    data class Error(val throwable: Throwable) : VideoCallError() {
        override val message: String = throwable.message ?: throwable::class.simpleName.orEmpty()
    }
}