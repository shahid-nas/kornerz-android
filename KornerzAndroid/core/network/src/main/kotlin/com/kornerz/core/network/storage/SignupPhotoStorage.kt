package com.kornerz.core.network.storage

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Uploads the profile photo chosen during signup to Firebase Cloud
 * Storage and returns the public download URL.
 *
 * ## Why this exists
 *
 * iOS uploads the avatar **before** the account is created
 * (`kornerz/Kornerz/SwiftUI/OnBoarding/Signup/SignupViewModel.swift:174-208`):
 * the picked `UIImage` is JPEG-encoded at quality `0.5`, uploaded to
 * `users/{userCode}/profile/{uuid}.jpg`, and the returned download URL
 * is written to the user's `firebaseImageUrl` field as part of the
 * registration payload. If the upload fails, iOS aborts the
 * registration with a visible error.
 *
 * The Android port originally had a no-op placeholder in
 * `SignupViewModel.uploadPhoto()` that marked the upload as
 * "successful" without touching the network, so the account was always
 * created with a null `firebaseImageUrl`. This class supplies the
 * missing upload.
 *
 * ## Why the signed-URL pipeline
 *
 * The bucket rules deny client writes (`allow write: if false;` — see
 * `kornerz/FIREBASE_STORAGE_MIGRATION.md:336-347`), so the bytes must
 * go through the `issueUploadUrl` Cloud Function. [FirebaseStorageUploader]
 * implements exactly that flow and is shared with the Profile and
 * community screens.
 */
@Singleton
class SignupPhotoStorage @Inject constructor(
    private val uploader: FirebaseStorageUploader,
) {

    /**
     * Compresses [bitmap] to JPEG and uploads it, returning the public
     * download URL to store on the user document.
     *
     * The object name carries a random suffix so every signup lands on
     * a **new** object — a fixed name would be overwritten in place and
     * the previous image would still be served from the HTTP cache and
     * Coil's memory cache. This mirrors iOS
     * `AppHelper.firebaseImagePath`
     * (`kornerz/Kornerz/Helper/AppHelper.swift:387-394`), which appends
     * a 12-character UUID to the prefix.
     *
     * The JPEG quality is `50`, matching iOS
     * `profileImage.jpegData(compressionQuality: 0.5)`.
     *
     * @param userCode the user's `codeUser` value. iOS falls back to
     *   the user id when `codeUser` is absent; Android generates the
     *   code before the upload, so it is always present here.
     */
    suspend fun uploadAvatar(userCode: String, bitmap: Bitmap): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val jpeg = bitmap.toJpegBytes(JPEG_QUALITY)
                uploader.uploadBytes(
                    path = "users/$userCode/profile/${randomObjectName()}.jpg",
                    bytes = jpeg,
                    contentType = CONTENT_TYPE_IMAGE,
                )
            }
        }

    /**
     * A 12-character random object name, mirroring iOS
     * `AppHelper.firebaseImagePath` (which uses `UUID().uuidString.prefix(12)`).
     */
    private fun randomObjectName(): String =
        UUID.randomUUID().toString().replace("-", "").take(12)

    private fun Bitmap.toJpegBytes(quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }

    private companion object {
        const val CONTENT_TYPE_IMAGE = "image/jpeg"

        /** iOS compresses the signup avatar at quality `0.5`. */
        const val JPEG_QUALITY = 50
    }
}
