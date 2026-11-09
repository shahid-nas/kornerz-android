package com.kornerz.feature.community.admin.createforum

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Writes [bitmap] into the app's cache directory and returns a
 * `content://` URI for it.
 *
 * iOS's camera path returns a `UIImage` from the picker's
 * `imagePickerController(_:didFinishPickingMediaWithInfo:)`
 * (`ImagePicker.swift`), which the wizard then stores directly. Android
 * needs a URI to hand back to the picker and to the uploader, so the
 * captured frame is written to cache and exposed through the app's
 * `FileProvider`.
 *
 * The provider's authority is declared in the app manifest as
 * `${applicationId}.fileprovider`. The **application id** — not the
 * package name — is what the manifest placeholder expands to, and the
 * two differ in the dev flavour (`com.kornerz.kornerz.dev` vs the
 * `com.kornerz.kornerz` namespace). Resolving the authority from the
 * manifest keeps the two in step.
 */
internal fun Bitmap.toCacheUri(context: Context): Uri? = runCatching {
    val directory = File(context.cacheDir, CACHE_DIR_NAME).apply { mkdirs() }
    val file = File(directory, "cover_${System.currentTimeMillis()}.jpg")
    FileOutputStream(file).use { output ->
        compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
    }
    FileProvider.getUriForFile(context, fileProviderAuthority(context), file)
}.getOrNull()

/**
 * The authority of the app's `FileProvider`, resolved from the merged
 * manifest so it matches the `${applicationId}.fileprovider`
 * placeholder in every flavour.
 */
private fun fileProviderAuthority(context: Context): String {
    val info = context.packageManager.getPackageInfo(
        context.packageName,
        android.content.pm.PackageManager.GET_PROVIDERS,
    )
    val provider = info.providers?.firstOrNull { it.name == FileProvider::class.java.name }
    return provider?.authority
        ?: "${context.packageName}.fileprovider"
}

/** The cache sub-directory the camera capture is written into. */
private const val CACHE_DIR_NAME = "create_forum_media"

/** The JPEG quality used for the camera capture, matching iOS's `jpegData(compressionQuality:)`. */
private const val JPEG_QUALITY = 90
