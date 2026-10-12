package com.kornerz.feature.profile.data.network

import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase Storage API for avatar uploads. Mirrors iOS
 * `UserProfileRepository.uploadUserProfileImage` (P6, task 3.3).
 *
 * The Android port uses the v4 signed-URL pipeline: write the
 * compressed JPEG bytes to `users/{userCode}/profile/avatar.jpg` and
 * a 200-pt thumbnail to `users/{userCode}/profile/avatar_thumb.jpg`,
 * then return both resolved download URLs for the caller to write
 * back to the `Users/{email}` document (see
 * [UserFirestoreApi.updateProfileImage]).
 *
 * iOS uses the same storage path prefix (lines 43–48
 * `let avatarPrefix = "users/\(userCode)/profile"`) and writes four
 * fields (`image`, `thumbnail`, `firebaseImageUrl`,
 * `firebaseThumbnailUrl`) — the Android port writes the same four
 * fields to keep backwards compatibility with the legacy
 * `imageURL` / `thumbnailURL` IPFS branches.
 */
@Singleton
class UserStorageApi @Inject constructor(
    private val storage: FirebaseStorage,
) {

    /**
     * Uploads [fullJpegBytes] (full-size) and [thumbnailBytes] to
     * Firebase Storage and returns the resolved download URLs.
     */
    suspend fun uploadAvatar(
        userCode: String,
        fullJpegBytes: ByteArray,
        thumbnailBytes: ByteArray,
    ): Result<Pair<String, String>> = runCatching {
        val fullRef = storage.reference
            .child("users/$userCode/profile/avatar.jpg")
        val thumbRef = storage.reference
            .child("users/$userCode/profile/avatar_thumb.jpg")

        fullRef.putBytes(fullJpegBytes).await()
        thumbRef.putBytes(thumbnailBytes).await()

        val fullUrl = fullRef.downloadUrl.await().toString()
        val thumbUrl = thumbRef.downloadUrl.await().toString()
        fullUrl to thumbUrl
    }
}
