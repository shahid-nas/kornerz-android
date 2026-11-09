package com.kornerz.feature.profile.data.network

import com.kornerz.core.network.storage.FirebaseStorageUploader
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
 *
 * The upload goes through [FirebaseStorageUploader] rather than the
 * Firebase client SDK because the bucket's storage rules deny client
 * writes (`allow write: if false;` — see
 * `kornerz/FIREBASE_STORAGE_MIGRATION.md:336-347`); a direct
 * `putBytes` fails with `StorageException … 403 Permission denied`.
 */
@Singleton
class UserStorageApi @Inject constructor(
    private val uploader: FirebaseStorageUploader,
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
        val fullUrl = uploader.uploadBytes(
            path = "users/$userCode/profile/avatar.jpg",
            bytes = fullJpegBytes,
            contentType = CONTENT_TYPE_IMAGE,
        )
        val thumbUrl = uploader.uploadBytes(
            path = "users/$userCode/profile/avatar_thumb.jpg",
            bytes = thumbnailBytes,
            contentType = CONTENT_TYPE_IMAGE,
        )
        fullUrl to thumbUrl
    }

    private companion object {
        const val CONTENT_TYPE_IMAGE = "image/jpeg"
    }
}
