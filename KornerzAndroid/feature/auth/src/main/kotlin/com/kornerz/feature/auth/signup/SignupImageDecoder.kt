package com.kornerz.feature.auth.signup

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.kornerz.core.network.storage.ImageOrientationDecoder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Takes care of the bytes behind a picked profile photo.
 *
 * The signup photo step uses the Photo Picker
 * (`ActivityResultContracts.PickVisualMedia`), which hands back a
 * content URI backed by a **short-lived read grant**. The wizard keeps
 * the selection until the final step, where the photo is uploaded — and
 * by then the grant may well have expired, which would leave the upload
 * with an unreadable source.
 *
 * To avoid that, [copyToCache] copies the picked image into the app's
 * private cache directory the moment it is selected. The wizard then
 * holds a `file://` URI it fully controls, and [decode] reads it back
 * whenever the upload runs.
 */
@Singleton
class SignupImageDecoder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val orientationDecoder: ImageOrientationDecoder,
) {

    /**
     * Copies the image behind [uri] into the app's private cache
     * directory and returns a `file://` URI for the copy.
     *
     * The copy is the durable handle the wizard keeps: it does not
     * depend on the Photo Picker's read grant, which expires shortly
     * after the pick. The same pattern is used by the create-forum
     * wizard (`MediaCache` in `feature/community`).
     *
     * The bytes are re-encoded with the EXIF orientation applied, so the
     * cached copy — which is also what the photo-step preview shows — is
     * upright even though the source file's orientation tag is dropped
     * by the re-encode.
     *
     * @throws IllegalStateException if the source cannot be read or the
     *   copy cannot be written.
     */
    suspend fun copyToCache(uri: Uri): Uri = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Could not open the selected image")
        val normalized = orientationDecoder.decode(bytes)
        val directory = File(context.cacheDir, CACHE_DIR_NAME).apply { mkdirs() }
        val target = File(directory, "signup_photo_${System.currentTimeMillis()}.jpg")
        val copied = runCatching {
            target.outputStream().use { output ->
                normalized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            }
        }
        if (copied.isFailure) {
            target.delete()
            throw copied.exceptionOrNull() ?: IllegalStateException("Could not read the selected image")
        }
        Uri.fromFile(target)
    }

    /**
     * Deletes a copy made by [copyToCache].
     *
     * Called when the selection is replaced, skipped or uploaded, so a
     * finished signup does not leave the photo sitting in the cache
     * directory.
     */
    suspend fun discardCached(uri: Uri?) = withContext(Dispatchers.IO) {
        val path = uri?.path ?: return@withContext
        val file = File(path)
        // Only ever delete files that live inside the app's own cache
        // directory — the URI could have come from elsewhere.
        val cacheRoot = File(context.cacheDir, CACHE_DIR_NAME).absolutePath
        if (file.absolutePath.startsWith("$cacheRoot${File.separator}")) {
            file.delete()
        }
    }

    /**
     * Decodes [uri] into a [Bitmap], downscaled so its longest edge is at
     * most [maxDimension] pixels and rotated upright using the source
     * image's EXIF orientation.
     *
     * The downscale happens during decode (via `inSampleSize`) rather than
     * after, so a 12-megapixel camera photo never has to be held in the
     * heap at full resolution. The result is then compressed to JPEG
     * by [com.kornerz.core.network.storage.SignupPhotoStorage].
     *
     * @throws IllegalStateException if the image cannot be decoded.
     */
    suspend fun decode(uri: Uri, maxDimension: Int = MAX_DIMENSION): Bitmap =
        orientationDecoder.decode(uri, maxDimension)

    private companion object {
        /**
         * The longest edge of the decoded bitmap. A 1024-px image is
         * plenty for a profile photo and keeps the JPEG upload small.
         */
        const val MAX_DIMENSION = 1024

        /** The cached copy is written at the same quality iOS uploads at. */
        const val JPEG_QUALITY = 50

        /** The cache sub-directory the picked photo is copied into. */
        const val CACHE_DIR_NAME = "signup_photos"
    }
}
