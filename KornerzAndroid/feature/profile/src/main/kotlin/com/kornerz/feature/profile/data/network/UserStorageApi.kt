package com.kornerz.feature.profile.data.network

import com.kornerz.core.network.storage.FirebaseStorageUploader
import java.util.UUID
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
     *
     * The object names carry a random suffix so every upload lands on a
     * **new** object. A fixed name would be overwritten in place, and
     * the previous image would still be served from the HTTP cache and
     * Coil's memory cache — the Profile screen would keep showing the
     * old avatar after a successful upload. iOS solves the same
     * problem with `AppHelper.firebaseImagePath`
     * (`kornerz/Kornerz/Helper/AppHelper.swift:387-394`), which appends
     * a 12-character UUID to the prefix.
     *
     * @param onProgress reports the `0.0..1.0` progress of the upload
     *   currently in flight. The full image is step `1` and the
     *   thumbnail is step `2`, so the caller can weight the two into a
     *   single overall bar.
     */
    suspend fun uploadAvatar(
        userCode: String,
        fullJpegBytes: ByteArray,
        thumbnailBytes: ByteArray,
        onProgress: (step: Int, progress: Double) -> Unit = { _, _ -> },
    ): Result<Pair<String, String>> = runCatching {
        val fullUrl = uploader.uploadBytes(
            path = "users/$userCode/profile/${randomObjectName()}.jpg",
            bytes = fullJpegBytes,
            contentType = CONTENT_TYPE_IMAGE,
        ) { progress -> onProgress(STEP_FULL_IMAGE, progress) }
        val thumbUrl = uploader.uploadBytes(
            path = "users/$userCode/profile/${randomObjectName()}.jpg",
            bytes = thumbnailBytes,
            contentType = CONTENT_TYPE_IMAGE,
        ) { progress -> onProgress(STEP_THUMBNAIL, progress) }
        fullUrl to thumbUrl
    }

    /**
     * A 12-character random object name, mirroring iOS
     * `AppHelper.firebaseImagePath` (which uses `UUID().uuidString.prefix(12)`).
     */
    private fun randomObjectName(): String =
        UUID.randomUUID().toString().replace("-", "").take(12)

    private companion object {
        const val CONTENT_TYPE_IMAGE = "image/jpeg"

        /** Step index of the full-size image upload. */
        const val STEP_FULL_IMAGE = 1

        /** Step index of the thumbnail upload. */
        const val STEP_THUMBNAIL = 2
    }
}
