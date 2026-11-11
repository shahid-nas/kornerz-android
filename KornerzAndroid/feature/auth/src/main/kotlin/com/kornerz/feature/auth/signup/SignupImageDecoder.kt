package com.kornerz.feature.auth.signup

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
     * @throws IllegalStateException if the source cannot be read or the
     *   copy cannot be written.
     */
    suspend fun copyToCache(uri: Uri): Uri = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, CACHE_DIR_NAME).apply { mkdirs() }
        val target = File(directory, "signup_photo_${System.currentTimeMillis()}.jpg")
        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Could not open the selected image")
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
     * most [maxDimension] pixels.
     *
     * The downscale happens during decode (via `inSampleSize`) rather than
     * after, so a 12-megapixel camera photo never has to be held in the
     * heap at full resolution. The result is then compressed to JPEG
     * by [com.kornerz.core.network.storage.SignupPhotoStorage].
     *
     * @throws IllegalStateException if the image cannot be decoded.
     */
    suspend fun decode(uri: Uri, maxDimension: Int = MAX_DIMENSION): Bitmap =
        withContext(Dispatchers.IO) {
            // Read the bytes once. A content URI may only be opened a
            // single time — the Photo Picker's read grant is
            // one-shot — so the stream is opened exactly once and both
            // the bounds probe and the real decode run off the same
            // buffer.
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Could not open the selected image")

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                error("The selected file is not a readable image")
            }

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxDimension)
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                ?: error("Could not decode the selected image")
        }

    /**
     * The power-of-two sample size that brings the image close to
     * [maxDimension] without going below it.
     */
    private fun sampleSizeFor(width: Int, height: Int, maxDimension: Int): Int {
        var sampleSize = 1
        var longestEdge = maxOf(width, height)
        while (longestEdge / 2 >= maxDimension) {
            longestEdge /= 2
            sampleSize *= 2
        }
        return sampleSize
    }

    private companion object {
        /**
         * The longest edge of the decoded bitmap. A 1024-px image is
         * plenty for a profile photo and keeps the JPEG upload small.
         */
        const val MAX_DIMENSION = 1024

        /** The cache sub-directory the picked photo is copied into. */
        const val CACHE_DIR_NAME = "signup_photos"
    }
}
