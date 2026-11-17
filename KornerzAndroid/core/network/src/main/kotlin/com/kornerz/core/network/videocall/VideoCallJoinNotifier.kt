package com.kornerz.core.network.videocall

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Notifies the backend when a user joins a video call.
 *
 * Mirrors iOS `ApiService.notifyVideoCallJoined`: a GET request to the
 * `videocallstarted` Cloud Function with `communityId` and `nickName`
 * query parameters.
 */
@Singleton
class VideoCallJoinNotifier @Inject constructor(
    private val client: OkHttpClient,
) {

    suspend fun notifyVideoCallJoined(communityId: String, nickName: String): Result<Unit> {
        if (communityId.isBlank() || nickName.isBlank()) {
            return Result.failure(IllegalArgumentException("Missing communityId or nickName"))
        }

        val url = ENDPOINT.toHttpUrl().newBuilder()
            .addQueryParameter("communityId", communityId)
            .addQueryParameter("nickName", nickName)
            .build()
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.i(TAG, "Video call join notified for communityId='$communityId'")
                        Result.success(Unit)
                    } else {
                        Result.failure(
                            IllegalStateException("Video call join notification failed: HTTP ${response.code}"),
                        )
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private companion object {
        const val ENDPOINT = "https://videocallstarted-hp7acuma7a-uc.a.run.app"
        const val TAG = "VideoCallJoin"
    }
}
