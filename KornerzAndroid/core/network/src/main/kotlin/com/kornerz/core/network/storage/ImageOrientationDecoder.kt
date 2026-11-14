package com.kornerz.core.network.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decodes picked images with their EXIF orientation applied to the pixels.
 *
 * Android's `BitmapFactory` ignores the EXIF orientation tag, so a photo
 * taken in portrait is decoded sideways. Re-encoding that bitmap to JPEG
 * strips the orientation tag and permanently bakes the wrong rotation
 * into the uploaded image.
 *
 * The Photo Picker returns the original camera file, including its EXIF
 * metadata, so the orientation has to be read from the source bytes and
 * applied to the decoded bitmap.
 */
@Singleton
class ImageOrientationDecoder @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Reads [uri] and returns a bitmap whose pixels are upright.
     *
     * @param maxDimension the longest edge the decoded bitmap may have.
     *   A value of `0` keeps the source resolution.
     * @throws IllegalStateException if the image cannot be read or decoded.
     */
    suspend fun decode(uri: Uri, maxDimension: Int = 0): Bitmap = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Could not open the selected image")
        decode(bytes, maxDimension)
    }

    /**
     * Decodes [bytes] and returns a bitmap whose pixels are upright.
     *
     * @param maxDimension the longest edge the decoded bitmap may have.
     *   A value of `0` keeps the source resolution.
     * @throws IllegalStateException if the image cannot be decoded.
     */
    suspend fun decode(bytes: ByteArray, maxDimension: Int = 0): Bitmap = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            error("The selected file is not a readable image")
        }

        val options = BitmapFactory.Options().apply {
            if (maxDimension > 0) {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxDimension)
            }
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: error("Could not decode the selected image")
        applyExifOrientation(decoded, bytes)
    }

    /**
     * Applies the EXIF orientation recorded in [bytes] to [bitmap].
     *
     * The orientation tag is consumed here because the JPEG re-encode that
     * follows drops it.
     */
    private fun applyExifOrientation(bitmap: Bitmap, bytes: ByteArray): Bitmap {
        val orientation = readOrientation(bytes) ?: return bitmap
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun readOrientation(bytes: ByteArray): Int? = runCatching {
        bytes.inputStream().use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }
    }.getOrNull()

    private fun sampleSizeFor(width: Int, height: Int, maxDimension: Int): Int {
        var sampleSize = 1
        var longestEdge = maxOf(width, height)
        while (longestEdge / 2 >= maxDimension) {
            longestEdge /= 2
            sampleSize *= 2
        }
        return sampleSize
    }
}
