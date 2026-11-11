package com.kornerz.core.network.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Uploads bytes and files to Firebase Cloud Storage through the
 * v4 signed-URL pipeline.
 *
 * ## Why this exists
 *
 * The Kornerz bucket is governed by the storage rules recorded in
 * `kornerz/FIREBASE_STORAGE_MIGRATION.md:336-347`:
 *
 * ```
 * match /{allPaths=**} {
 *   allow read: if true;
 *   allow write: if false;
 * }
 * ```
 *
 * Reads are public, but **writes are rejected outright** — the only
 * sanctioned write path is a v4 signed URL minted by the
 * `issueUploadUrl` Cloud Function, which holds the Admin SDK
 * credentials.
 *
 * iOS follows that path
 * (`kornerz/Kornerz/Backend/Services/FirebaseStorageService.swift`):
 *
 * 1. `POST issueUploadUrl {path, contentType, fileSize}`
 * 2. `PUT` the bytes to the returned `uploadUrl`
 * 3. store the returned `downloadUrl` in Firestore
 *
 * The Android port previously used the Firebase **client** SDK
 * (`StorageReference.putBytes` / `putFile`) instead. The client SDK
 * authenticates as the app's (anonymous) Firebase Auth user, which the
 * rules do not grant write access to — every upload failed with
 *
 * ```
 * StorageException: User does not have permission to access this object.
 * Code: -13021 HttpResult: 403
 * ```
 *
 * This class is the Android counterpart of
 * `FirebaseStorageService.swift` and restores the working upload path.
 *
 * ## Threading
 *
 * All work happens on [Dispatchers.IO]; [onProgress] is always
 * delivered on the caller's dispatcher.
 */
@Singleton
class FirebaseStorageUploader @Inject constructor(
    private val client: OkHttpClient,
) {

    /**
     * Uploads [bytes] to [path] and returns the public download URL.
     *
     * @param contentType MIME type used for the signed URL and the
     *   `Content-Type` header of the PUT. It must match the type the
     *   object was signed with or GCS rejects the upload.
     * @param onProgress Optional progress callback in the range
     *   `0.0..1.0`. The cover image reports progress; the thumbnail
     *   does not — matching iOS, where the thumbnail upload is fired
     *   without a progress handler
     *   (`AddForumViewModel.swift:181-189`).
     */
    suspend fun uploadBytes(
        path: String,
        bytes: ByteArray,
        contentType: String,
        onProgress: ((Double) -> Unit)? = null,
    ): String = upload(
        path = path,
        contentType = contentType,
        fileSize = bytes.size.toLong(),
        body = ByteArrayRequestBody(bytes, contentType, onProgress),
        onProgress = onProgress,
    )

    /**
     * Uploads [file] to [path] and returns the public download URL.
     *
     * The file is streamed from disk rather than read into memory, so
     * a large video does not have to fit in the heap.
     */
    suspend fun uploadFile(
        path: String,
        file: File,
        contentType: String,
        onProgress: ((Double) -> Unit)? = null,
    ): String = upload(
        path = path,
        contentType = contentType,
        fileSize = file.length(),
        body = FileRequestBody(file, contentType, onProgress),
        onProgress = onProgress,
    )

    /**
     * Uploads the content of [uri]'s file to [path] and returns the
     * public download URL.
     *
     * The URI is resolved to a [File] by the caller, because the
     * content resolver may hand back a document URI that is not
     * backed by a real file.
     */
    private suspend fun upload(
        path: String,
        contentType: String,
        fileSize: Long,
        body: RequestBody,
        onProgress: ((Double) -> Unit)?,
    ): String = withContext(Dispatchers.IO) {
        val issued = issueUploadUrl(path = path, contentType = contentType, fileSize = fileSize)

        val request = Request.Builder()
            .url(issued.uploadUrl)
            .put(body)
            .header("Content-Type", contentType)
            .header("Cache-Control", "public, max-age=31536000")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                throw StorageUploadException(
                    "Upload failed with HTTP ${response.code}${body.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()}",
                )
            }
        }

        issued.downloadUrl
    }

    /**
     * Asks the `issueUploadUrl` Cloud Function for a signed upload URL.
     *
     * Mirrors iOS `ApiService.issueUploadUrl`
     * (`kornerz/Kornerz/Helper/ApiService.swift:307-348`): a plain
     * `POST` with a JSON body, unauthenticated. The function mints a
     * v4 write URL and returns the matching public download URL.
     */
    private fun issueUploadUrl(
        path: String,
        contentType: String,
        fileSize: Long,
    ): IssuedUploadUrl {
        val payload = buildString {
            append('{')
            append("\"path\":").append(jsonString(path)).append(',')
            append("\"contentType\":").append(jsonString(contentType)).append(',')
            append("\"fileSize\":").append(fileSize)
            append('}')
        }

        val request = Request.Builder()
            .url(ISSUE_UPLOAD_URL)
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw StorageUploadException(
                    "Could not issue an upload URL (HTTP ${response.code})${body.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()}",
                )
            }
            return parseIssuedUploadUrl(body)
        }
    }

    private fun parseIssuedUploadUrl(body: String): IssuedUploadUrl {
        val root = try {
            JSON.parseToJsonElement(body) as? JsonObject
        } catch (e: Exception) {
            null
        } ?: throw StorageUploadException("The upload URL response was not a JSON object")

        // An `onCall` Cloud Function wraps the payload in a `data`
        // envelope; a plain HTTPS function answers with the object
        // itself. Accept both so the client works either way.
        val json = root["data"]?.let { it as? JsonObject } ?: root

        val uploadUrl = json.stringOrNull("uploadUrl")
        val downloadUrl = json.stringOrNull("downloadUrl")
        if (uploadUrl.isNullOrBlank() || downloadUrl.isNullOrBlank()) {
            throw StorageUploadException("The upload URL response was missing uploadUrl/downloadUrl")
        }

        return IssuedUploadUrl(
            uploadUrl = uploadUrl,
            downloadUrl = downloadUrl,
            expiresAt = json.longOrNull("expiresAt") ?: 0L,
        )
    }

    private fun JsonObject.stringOrNull(key: String): String? {
        val value = this[key] ?: return null
        return value.jsonPrimitive.content.takeIf { it.isNotBlank() }
    }

    private fun JsonObject.longOrNull(key: String): Long? {
        val value = this[key] ?: return null
        return value.jsonPrimitive.content.toLongOrNull()
    }

    private fun jsonString(value: String): String {
        val builder = StringBuilder(value.length + 2)
        builder.append('"')
        value.forEach { c ->
            when (c) {
                '"' -> builder.append("\\\"")
                '\\' -> builder.append("\\\\")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                '\t' -> builder.append("\\t")
                else -> if (c < ' ') {
                    builder.append("\\u").append(String.format("%04x", c.code))
                } else {
                    builder.append(c)
                }
            }
        }
        builder.append('"')
        return builder.toString()
    }

    private companion object {
        /**
         * The `issueUploadUrl` Cloud Function. iOS uses the same URL
         * for its development and production environments
         * (`ApiService.swift:47-48` and `:70-71`).
         */
        const val ISSUE_UPLOAD_URL =
            "https://us-central1-kornerz-72fba.cloudfunctions.net/issueUploadUrl"

        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val JSON = Json { ignoreUnknownKeys = true }
    }
}

/**
 * The signed upload URL issued by the `issueUploadUrl` Cloud
 * Function, plus the public URL the uploaded object is readable at.
 */
private data class IssuedUploadUrl(
    val uploadUrl: String,
    val downloadUrl: String,
    val expiresAt: Long,
)

/** Raised when a signed-URL upload cannot be completed. */
class StorageUploadException(message: String) : IOException(message)

/**
 * A [RequestBody] over an in-memory byte array.
 *
 * [writeTo] reports progress through [onProgress] as the bytes are
 * handed to the socket, which is what drives the publish progress bar.
 */
private class ByteArrayRequestBody(
    private val bytes: ByteArray,
    private val contentType: String,
    private val onProgress: ((Double) -> Unit)? = null,
) : RequestBody() {

    override fun contentType() = contentType.toMediaType()

    override fun contentLength() = bytes.size.toLong()

    override fun writeTo(sink: BufferedSink) {
        val total = bytes.size
        if (total == 0) {
            sink.flush()
            return
        }
        var written = 0
        val chunk = 64 * 1024
        while (written < total) {
            val end = minOf(written + chunk, total)
            sink.write(bytes, written, end - written)
            written = end
            onProgress?.invoke(written.toDouble() / total)
        }
        sink.flush()
    }
}

/**
 * A [RequestBody] that streams a file from disk.
 *
 * The file is read through a source so a large video never has to be
 * held in memory.
 */
private class FileRequestBody(
    private val file: File,
    private val contentType: String,
    private val onProgress: ((Double) -> Unit)? = null,
) : RequestBody() {

    override fun contentType() = contentType.toMediaType()

    override fun contentLength() = file.length()

    override fun writeTo(sink: BufferedSink) {
        val total = file.length()
        if (total <= 0L) {
            sink.flush()
            return
        }
        var written = 0L
        file.source().use { source ->
            val buffer = okio.Buffer()
            while (true) {
                val read = source.read(buffer, SEGMENT_SIZE)
                if (read == -1L) break
                sink.write(buffer, read)
                written += read
                onProgress?.invoke(written.toDouble() / total)
            }
        }
        sink.flush()
    }

    private companion object {
        const val SEGMENT_SIZE = 64 * 1024L
    }
}
